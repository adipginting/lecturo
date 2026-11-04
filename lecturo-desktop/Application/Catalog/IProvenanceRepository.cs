namespace Lecturo.Application.Catalog;

/// <summary>
/// The record of which remote item each downloaded document came from
/// (provenance), so a remote list can mark what is already local.
/// </summary>
public interface IProvenanceRepository
{
    string Add(string documentId, string source, string remoteId, string url, string title);

    /// <summary>Every linked remote item, as "source\u001fremoteId".</summary>
    HashSet<string> DownloadedKeys();

    /// <summary>The local document a remote item was downloaded into, if any.</summary>
    string? FindDocumentId(string source, string remoteId);
}
