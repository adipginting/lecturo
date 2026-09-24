using Lecturo.Domain;

namespace Lecturo.Application.Catalog;

/// <summary>One remote library behind a common browse/search/download interface (F3).</summary>
public interface IRemoteLibrarySource
{
    string Id { get; }
    bool IsConfigured { get; }
    Task<IReadOnlyList<RemoteItem>> BrowseAsync(string? catalogUrl, CancellationToken ct = default);
    Task<IReadOnlyList<RemoteItem>> SearchAsync(string query, CancellationToken ct = default);
    Task<byte[]> DownloadAsync(RemoteItem item, CancellationToken ct = default);
}
