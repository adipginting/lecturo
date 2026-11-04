using Lecturo.Application.Catalog;
using Lecturo.Domain;

namespace Lecturo.Tests;

internal sealed class FakeProvenanceRepository : IProvenanceRepository
{
    private readonly List<(string DocumentId, string Source, string RemoteId, string Url, string Title)> _rows = new();

    public string Add(string documentId, string source, string remoteId, string url, string title)
    {
        _rows.Add((documentId, source, remoteId, url, title));
        return _rows.Count.ToString();
    }

    public HashSet<string> DownloadedKeys() =>
        _rows.Select(r => Provenance.Key(r.Source, r.RemoteId)).ToHashSet();

    public string? FindDocumentId(string source, string remoteId) =>
        _rows.LastOrDefault(r => r.Source == source && r.RemoteId == remoteId) is { DocumentId: not null } row
            ? row.DocumentId
            : null;
}

internal sealed class FakeRemoteSource : IRemoteLibrarySource
{
    private readonly byte[] _bytes;

    public FakeRemoteSource(string id, byte[] bytes)
    {
        Id = id;
        _bytes = bytes;
    }

    public string Id { get; }
    public bool IsConfigured => true;

    public Task<IReadOnlyList<RemoteItem>> BrowseAsync(string? catalogUrl, CancellationToken ct = default) =>
        Task.FromResult<IReadOnlyList<RemoteItem>>(Array.Empty<RemoteItem>());

    public Task<IReadOnlyList<RemoteItem>> SearchAsync(string query, CancellationToken ct = default) =>
        Task.FromResult<IReadOnlyList<RemoteItem>>(Array.Empty<RemoteItem>());

    public Task<byte[]> DownloadAsync(RemoteItem item, CancellationToken ct = default) =>
        Task.FromResult(_bytes);
}
