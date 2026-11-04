using Lecturo.Application.Library;
using Lecturo.Domain;

namespace Lecturo.Application.Catalog;

/// <summary>
/// The remote-library use cases (F3): pick the source for a tab, download an
/// item into the local library and remember where it came from. The sources and
/// the provenance store are ports.
/// </summary>
public sealed class CatalogService
{
    private readonly IRemoteLibrarySource _calibre;
    private readonly IRemoteLibrarySource _zotero;
    private readonly IProvenanceRepository _provenance;
    private readonly LibraryService _library;

    public CatalogService(
        IRemoteLibrarySource calibre,
        IRemoteLibrarySource zotero,
        IProvenanceRepository provenance,
        LibraryService library)
    {
        _calibre = calibre;
        _zotero = zotero;
        _provenance = provenance;
        _library = library;
    }

    public IRemoteLibrarySource? For(string tab) => tab.ToLowerInvariant() switch
    {
        "calibre" => _calibre,
        "zotero" => _zotero,
        _ => null,
    };

    /// <summary>Every linked remote item, as "source\u001fremoteId".</summary>
    public HashSet<string> DownloadedKeys() => _provenance.DownloadedKeys();

    /// <summary>The local document a remote item was downloaded into, if any.</summary>
    public string? FindDocumentId(string source, string remoteId) => _provenance.FindDocumentId(source, remoteId);

    /// <summary>
    /// Downloads an item through its source, stores it in the local library and
    /// records the provenance in one step, so the caller cannot forget the link.
    /// </summary>
    public async Task<Document> DownloadAsync(
        IRemoteLibrarySource source,
        RemoteItem item,
        string title,
        CancellationToken ct = default)
    {
        var bytes = await source.DownloadAsync(item, ct);
        var document = _library.ImportDownloaded(bytes, title, item.Format);
        _provenance.Add(document.Id, source.Id, item.RemoteId, item.DownloadUrl ?? "", title);
        return document;
    }
}
