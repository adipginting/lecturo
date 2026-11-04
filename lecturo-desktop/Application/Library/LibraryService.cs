using Lecturo.Domain;

namespace Lecturo.Application.Library;

/// <summary>
/// The library use cases (F2): import means copy into app-owned storage, a URL
/// is judged by its payload, deletion is local-only. The rules that used to live
/// in the service beside the HTTP client are here; the network and the disk are
/// ports.
/// </summary>
public sealed class LibraryService
{
    private readonly IDocumentRepository _documents;
    private readonly IFileStore _files;
    private readonly IDocumentFetcher _fetcher;

    public LibraryService(IDocumentRepository documents, IFileStore files, IDocumentFetcher fetcher)
    {
        _documents = documents;
        _files = files;
        _fetcher = fetcher;
    }

    public IReadOnlyList<Document> List() => _documents.List();

    public Document? Get(string id) => _documents.Get(id);

    public string GetPath(Document doc) => _files.PathFor(doc.FileName);

    /// <summary>Copies a local PDF/EPUB into app storage and registers it.</summary>
    public Document ImportFromFile(string sourcePath)
    {
        var format = DocumentFormats.FromExtension(Path.GetExtension(sourcePath))
            ?? throw new InvalidOperationException(
                $"\u201c{Path.GetFileName(sourcePath)}\u201d is not a document \u2014 only PDF and EPUB are supported.");

        var title = Path.GetFileNameWithoutExtension(sourcePath);
        return Store(_files.ReadAllBytes(sourcePath), title, format);
    }

    /// <summary>Downloads a document from a URL into app storage and registers it.</summary>
    public async Task<Document> ImportFromUrlAsync(string url, CancellationToken ct = default)
    {
        var fetched = await _fetcher.FetchAsync(url, ct);

        var format = DocumentFormats.FromMagic(fetched.Bytes)
            ?? DocumentFormats.FromContentType(fetched.ContentType)
            ?? DocumentFormats.FromExtension(Path.GetExtension(new Uri(fetched.Url).AbsolutePath))
            ?? throw new InvalidOperationException(
                "The URL did not return a document \u2014 nothing was added to the library.");

        var title = TitleFromFileName(fetched.FileName)
            ?? Path.GetFileNameWithoutExtension(new Uri(fetched.Url).AbsolutePath);
        if (string.IsNullOrWhiteSpace(title))
            title = "downloaded-document";

        return Store(fetched.Bytes, title, format);
    }

    /// <summary>Imports bytes fetched from a remote library, sniffing the payload.</summary>
    public Document ImportDownloaded(byte[] bytes, string title, DocumentFormat? suggestedFormat = null)
    {
        var format = DocumentFormats.FromMagic(bytes)
            ?? suggestedFormat
            ?? throw new InvalidOperationException("The downloaded file is not a PDF or EPUB.");

        return Store(bytes, string.IsNullOrWhiteSpace(title) ? "download" : title.Trim(), format);
    }

    /// <summary>Deletion is local-only: the owned copy and the library row both go.</summary>
    public void Delete(Document doc)
    {
        try
        {
            var path = GetPath(doc);
            if (_files.Exists(path))
                _files.Delete(path);
        }
        catch
        {
            // The row still goes; a stray file is preferable to a stuck library.
        }
        _documents.Delete(doc.Id);
    }

    /// <summary>Remembers where the reader left off, so opening resumes there.</summary>
    public void UpdateLocator(string id, string locator) => _documents.UpdateLocator(id, locator);

    private Document Store(byte[] bytes, string title, DocumentFormat format)
    {
        var fileName = $"{UuidV7.New()}.{format.ToStoredValue()}";
        _files.Write(fileName, bytes);
        var id = _documents.Add(title, fileName, format);
        return _documents.Get(id)!;
    }

    private static string? TitleFromFileName(string? fileName)
    {
        if (string.IsNullOrWhiteSpace(fileName))
            return null;
        var title = Path.GetFileNameWithoutExtension(fileName.Trim('"'));
        return string.IsNullOrWhiteSpace(title) ? null : title;
    }
}
