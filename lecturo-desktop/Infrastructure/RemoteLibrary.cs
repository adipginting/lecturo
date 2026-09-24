using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Xml.Linq;
using Lecturo.Application.Catalog;
using Lecturo.Application.Settings;
using Lecturo.Domain;

namespace Lecturo.Infrastructure;

/// <summary>Calibre's Content Server, over its OPDS (Atom/XML) catalog.</summary>
public sealed class CalibreSource : IRemoteLibrarySource
{
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(45) };

    private readonly ISettingsStore _settings;
    private readonly ISecretStore _secrets;

    public CalibreSource(ISettingsStore settings, ISecretStore secrets)
    {
        _settings = settings;
        _secrets = secrets;
    }

    public string Id => "calibre";

    public bool IsConfigured => !string.IsNullOrWhiteSpace(_settings.Settings.CalibreUrl);

    public async Task<IReadOnlyList<RemoteItem>> BrowseAsync(string? catalogUrl, CancellationToken ct = default)
    {
        var baseUrl = RequireBaseUrl();
        var url = string.IsNullOrWhiteSpace(catalogUrl) ? baseUrl + "/opds" : catalogUrl;
        var (xml, finalUrl) = await GetStringAsync(url, ct);
        return ParseFeed(xml, finalUrl);
    }

    public async Task<IReadOnlyList<RemoteItem>> SearchAsync(string query, CancellationToken ct = default)
    {
        var baseUrl = RequireBaseUrl();
        var (rootXml, rootUrl) = await GetStringAsync(baseUrl + "/opds", ct);
        var root = XDocument.Parse(rootXml).Root
            ?? throw new InvalidOperationException("The Calibre catalog was empty.");

        var ns = root.Name.Namespace;
        var template = root.Elements(ns + "link")
            .FirstOrDefault(l => (string?)l.Attribute("rel") == "search")
            ?.Attribute("href")?.Value;

        if (string.IsNullOrWhiteSpace(template))
            throw new InvalidOperationException("This Calibre server does not declare OPDS search.");

        var searchUrl = new Uri(new Uri(rootUrl), template)
            .AbsoluteUri.Replace("{searchTerms}", Uri.EscapeDataString(query));
        var (xml, finalUrl) = await GetStringAsync(searchUrl, ct);
        return ParseFeed(xml, finalUrl);
    }

    public async Task<byte[]> DownloadAsync(RemoteItem item, CancellationToken ct = default)
    {
        if (string.IsNullOrWhiteSpace(item.DownloadUrl))
            throw new InvalidOperationException("This catalog entry has nothing to download.");

        using var request = new HttpRequestMessage(HttpMethod.Get, item.DownloadUrl);
        ApplyAuth(request);
        using var response = await Http.SendAsync(request, ct);
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadAsByteArrayAsync(ct);
    }

    private string RequireBaseUrl()
    {
        var url = _settings.Settings.CalibreUrl?.Trim();
        if (string.IsNullOrWhiteSpace(url))
            throw new InvalidOperationException("Set the Calibre Content Server URL in Settings.");
        return url.TrimEnd('/');
    }

    private async Task<(string body, string url)> GetStringAsync(string url, CancellationToken ct)
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, url);
        request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/atom+xml"));
        request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/xml"));
        ApplyAuth(request);

        using var response = await Http.SendAsync(request, ct);
        response.EnsureSuccessStatusCode();
        return (await response.Content.ReadAsStringAsync(ct), response.RequestMessage?.RequestUri?.AbsoluteUri ?? url);
    }

    private void ApplyAuth(HttpRequestMessage request)
    {
        var user = _settings.Settings.CalibreUsername;
        var password = _secrets.Get("calibre-password");
        if (string.IsNullOrWhiteSpace(user) || string.IsNullOrWhiteSpace(password))
            return;

        var token = Convert.ToBase64String(Encoding.UTF8.GetBytes($"{user}:{password}"));
        request.Headers.Authorization = new AuthenticationHeaderValue("Basic", token);
    }

    private static List<RemoteItem> ParseFeed(string xml, string feedUrl)
    {
        var root = XDocument.Parse(xml).Root
            ?? throw new InvalidOperationException("The Calibre catalog was empty.");
        var ns = root.Name.Namespace;
        var baseUri = new Uri(feedUrl);
        var items = new List<RemoteItem>();

        string Resolve(string href) => Uri.TryCreate(baseUri, href, out var uri) ? uri.AbsoluteUri : href;

        foreach (var entry in root.Elements(ns + "entry"))
        {
            var title = entry.Element(ns + "title")?.Value?.Trim() is { Length: > 0 } t ? t : "Untitled";
            var author = entry.Element(ns + "author")?.Element(ns + "name")?.Value?.Trim() ?? "";
            var id = entry.Element(ns + "id")?.Value?.Trim() ?? title;
            var summary = entry.Element(ns + "content")?.Value?.Trim()
                ?? entry.Element(ns + "summary")?.Value?.Trim();

            var links = entry.Elements(ns + "link").ToList();
            var catalogHref = links.FirstOrDefault(l =>
                ((string?)l.Attribute("type") ?? "").Contains("atom+xml", StringComparison.OrdinalIgnoreCase))
                ?.Attribute("href")?.Value;

            var produced = false;
            foreach (var link in links)
            {
                var rel = (string?)link.Attribute("rel") ?? "";
                if (!rel.Contains("acquisition", StringComparison.OrdinalIgnoreCase))
                    continue;

                var href = (string?)link.Attribute("href");
                var format = RemoteFormat.FromMediaType((string?)link.Attribute("type"));
                if (string.IsNullOrWhiteSpace(href) || format is null)
                    continue;

                items.Add(new RemoteItem("calibre", $"{id}:{format.Value.ToStoredValue()}", title, author, format,
                    Resolve(href), null, summary));
                produced = true;
            }

            if (!produced && !string.IsNullOrWhiteSpace(catalogHref))
                items.Add(new RemoteItem("calibre", id, title, author, null, null, Resolve(catalogHref), summary));
        }

        return items;
    }
}

/// <summary>Zotero's Web API: the user's PDF/EPUB attachments.</summary>
public sealed class ZoteroSource : IRemoteLibrarySource
{
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(45) };

    private readonly ISettingsStore _settings;
    private readonly ISecretStore _secrets;

    public ZoteroSource(ISettingsStore settings, ISecretStore secrets)
    {
        _settings = settings;
        _secrets = secrets;
    }

    public string Id => "zotero";

    private string UserId => _settings.Settings.ZoteroUserId?.Trim() ?? "";
    private string ApiKey => _secrets.Get("zotero-api-key") ?? "";

    private string ApiBase
    {
        get
        {
            var url = _settings.Settings.ZoteroBaseUrl?.Trim();
            return string.IsNullOrWhiteSpace(url) ? "https://api.zotero.org" : url.TrimEnd('/');
        }
    }

    public bool IsConfigured => UserId.Length > 0 && ApiKey.Length > 0;

    public Task<IReadOnlyList<RemoteItem>> BrowseAsync(string? catalogUrl, CancellationToken ct = default) =>
        ListAsync(null, ct);

    public Task<IReadOnlyList<RemoteItem>> SearchAsync(string query, CancellationToken ct = default) =>
        ListAsync(query, ct);

    public async Task<byte[]> DownloadAsync(RemoteItem item, CancellationToken ct = default)
    {
        if (string.IsNullOrWhiteSpace(item.DownloadUrl))
            throw new InvalidOperationException("This Zotero item has no downloadable file.");

        using var request = new HttpRequestMessage(HttpMethod.Get, item.DownloadUrl);
        Authorize(request);
        using var response = await Http.SendAsync(request, ct);
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadAsByteArrayAsync(ct);
    }

    private async Task<IReadOnlyList<RemoteItem>> ListAsync(string? query, CancellationToken ct)
    {
        if (!IsConfigured)
            throw new InvalidOperationException("Add your Zotero user id and API key in Settings.");

        var url = $"{ApiBase}/users/{UserId}/items?format=json&limit=100&itemType=attachment";
        if (!string.IsNullOrWhiteSpace(query))
            url += "&q=" + Uri.EscapeDataString(query);

        using var request = new HttpRequestMessage(HttpMethod.Get, url);
        Authorize(request);
        using var response = await Http.SendAsync(request, ct);
        var body = await response.Content.ReadAsStringAsync(ct);
        if (!response.IsSuccessStatusCode)
            response.EnsureSuccessStatusCode();

        using var json = JsonDocument.Parse(body);
        var items = new List<RemoteItem>();
        foreach (var entry in json.RootElement.EnumerateArray())
        {
            if (!entry.TryGetProperty("data", out var data))
                continue;

            var contentType = data.TryGetProperty("contentType", out var ct2) ? ct2.GetString() ?? "" : "";
            var format = RemoteFormat.FromMediaType(contentType);
            if (format is null)
                continue;

            var title = data.TryGetProperty("title", out var titleEl) && titleEl.GetString() is { Length: > 0 } tv
                ? tv
                : data.TryGetProperty("filename", out var fileEl) ? fileEl.GetString() ?? "Untitled" : "Untitled";

            var remoteId = entry.TryGetProperty("key", out var keyEl) ? keyEl.GetString() ?? title : title;
            var href = entry.TryGetProperty("links", out var links)
                && links.TryGetProperty("enclosure", out var enclosure)
                && enclosure.TryGetProperty("href", out var hrefEl)
                    ? hrefEl.GetString()
                    : null;

            items.Add(new RemoteItem("zotero", remoteId, title, "", format, href, null, null));
        }

        return items;
    }

    private void Authorize(HttpRequestMessage request)
    {
        request.Headers.Add("Zotero-API-Version", "3");
        request.Headers.Add("Authorization", $"Bearer {ApiKey}");
    }
}

/// <summary>Remote sources name formats as media types; both sources read them the same way.</summary>
internal static class RemoteFormat
{
    public static DocumentFormat? FromMediaType(string? mediaType)
    {
        if (string.IsNullOrWhiteSpace(mediaType))
            return null;
        if (mediaType.Contains("epub", StringComparison.OrdinalIgnoreCase))
            return DocumentFormat.Epub;
        return mediaType.Contains("pdf", StringComparison.OrdinalIgnoreCase) ? DocumentFormat.Pdf : null;
    }
}
