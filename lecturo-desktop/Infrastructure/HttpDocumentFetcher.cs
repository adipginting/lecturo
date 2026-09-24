using System.Net.Http.Headers;
using System.Text;
using System.Text.RegularExpressions;
using Lecturo.Application.Library;

namespace Lecturo.Infrastructure;

/// <summary>
/// Fetches a document from the web, following the download pages that stand in
/// for a file — HTML that merely links to or redirects to the book — up to three
/// hops. The first bytes decide whether the payload is the book.
/// </summary>
public sealed partial class HttpDocumentFetcher : IDocumentFetcher
{
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(60) };

    public async Task<FetchedDocument> FetchAsync(string url, CancellationToken ct = default)
    {
        var current = url;
        for (var hop = 0; hop <= 3; hop++)
        {
            using var request = new HttpRequestMessage(HttpMethod.Get, current);
            using var response = await Http.SendAsync(request, HttpCompletionOption.ResponseContentRead, ct);
            response.EnsureSuccessStatusCode();

            var bytes = await response.Content.ReadAsByteArrayAsync(ct);
            var finalUrl = response.RequestMessage?.RequestUri?.AbsoluteUri ?? current;
            var contentType = response.Content.Headers.ContentType;
            var disposition = response.Content.Headers.ContentDisposition?.FileName?.Trim('"');

            if (DocumentFormats.FromMagic(bytes) is not null)
                return new FetchedDocument(bytes, finalUrl, contentType?.MediaType, disposition);

            if (hop < 3 && LooksLikeHtml(bytes, contentType) && FindDownloadLink(bytes, finalUrl) is { } next)
            {
                current = next;
                continue;
            }

            return new FetchedDocument(bytes, finalUrl, contentType?.MediaType, disposition);
        }

        throw new InvalidOperationException("The download did not resolve to a file.");
    }

    private static bool LooksLikeHtml(byte[] bytes, MediaTypeHeaderValue? contentType)
    {
        if ((contentType?.MediaType ?? "").Contains("html", StringComparison.OrdinalIgnoreCase))
            return true;
        var head = Encoding.UTF8.GetString(bytes, 0, Math.Min(bytes.Length, 512)).TrimStart();
        return head.StartsWith("<", StringComparison.Ordinal);
    }

    /// <summary>A meta refresh URL, or the first link to a .pdf/.epub.</summary>
    private static string? FindDownloadLink(byte[] bytes, string baseUrl)
    {
        var html = Encoding.UTF8.GetString(bytes);
        var candidates = new List<string?>();

        var refresh = MetaRefreshRegex().Match(html);
        if (refresh.Success)
            candidates.Add(refresh.Groups[1].Value);

        foreach (Match link in AnchorRegex().Matches(html))
            candidates.Add(link.Groups[1].Value);

        foreach (var candidate in candidates)
        {
            if (string.IsNullOrWhiteSpace(candidate))
                continue;
            var value = candidate.Trim().Trim('"', '\'');
            if (Uri.TryCreate(new Uri(baseUrl), value, out var resolved))
                return resolved.AbsoluteUri;
        }

        return null;
    }

    [GeneratedRegex(
        @"<meta[^>]+http-equiv\s*=\s*[""']?refresh[""']?[^>]+content\s*=\s*[""'][^""']*url\s*=\s*([^""';]+)",
        RegexOptions.IgnoreCase)]
    private static partial Regex MetaRefreshRegex();

    [GeneratedRegex(@"<a\b[^>]+href\s*=\s*[""']([^""']+\.(?:epub|pdf)(?:\?[^""']*)?)[""']", RegexOptions.IgnoreCase)]
    private static partial Regex AnchorRegex();
}
