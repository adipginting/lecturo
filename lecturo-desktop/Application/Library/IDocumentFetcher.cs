namespace Lecturo.Application.Library;

/// <summary>What a fetch finally returned, after any download pages were followed.</summary>
public sealed record FetchedDocument(byte[] Bytes, string Url, string? ContentType, string? FileName);

/// <summary>
/// Fetches a URL, following the download pages that stand in for a file. A port
/// so URL import can be tested without the network.
/// </summary>
public interface IDocumentFetcher
{
    Task<FetchedDocument> FetchAsync(string url, CancellationToken ct = default);
}
