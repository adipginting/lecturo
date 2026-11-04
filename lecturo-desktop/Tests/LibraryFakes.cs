using Lecturo.Application.Library;
using Lecturo.Domain;

namespace Lecturo.Tests;

/// <summary>An in-memory document store, keyed by id.</summary>
internal sealed class FakeDocumentRepository : IDocumentRepository
{
    private readonly Dictionary<string, Document> _documents = new();
    private long _next = 1;

    public List<Document> List() => _documents.Values.OrderByDescending(d => d.UpdatedAt).ToList();

    public Document? Get(string id) => _documents.GetValueOrDefault(id);

    public string Add(string title, string fileName, DocumentFormat format)
    {
        var id = (_next++).ToString();
        _documents[id] = new Document(id, title, fileName, format, null, DateTime.UtcNow);
        return id;
    }

    public void UpdateLocator(string id, string locator)
    {
        if (_documents.TryGetValue(id, out var d))
            _documents[id] = d with { LastLocator = locator, UpdatedAt = DateTime.UtcNow };
    }

    public void Delete(string id) => _documents.Remove(id);
}

/// <summary>A filesystem in a dictionary: no temp dirs, no cleanup.</summary>
internal sealed class FakeFileStore : IFileStore
{
    private readonly Dictionary<string, byte[]> _files = new();

    public string PathFor(string fileName) => "/fake/" + fileName;

    public bool Exists(string path) => _files.ContainsKey(path);

    public byte[] ReadAllBytes(string path) => _files[path];

    public void Write(string fileName, byte[] bytes) => _files[PathFor(fileName)] = bytes;

    public void Delete(string path) => _files.Remove(path);

    /// <summary>Puts a file where a test can import it from.</summary>
    public void Seed(string path, byte[] bytes) => _files[path] = bytes;

    public int Count => _files.Count;
}

/// <summary>Answers a URL with a preset payload, or fails.</summary>
internal sealed class FakeDocumentFetcher : IDocumentFetcher
{
    private readonly Func<FetchedDocument> _reply;

    public FakeDocumentFetcher(FetchedDocument fetched) => _reply = () => fetched;

    public FakeDocumentFetcher(Exception error) => _reply = () => throw error;

    public Task<FetchedDocument> FetchAsync(string url, CancellationToken ct = default) =>
        Task.FromResult(_reply());
}
