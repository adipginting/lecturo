using System.Text;
using Lecturo.Application.Library;
using Lecturo.Domain;
using Xunit;

namespace Lecturo.Tests;

/// <summary>
/// The library use cases (F2), tested without the network or the disk: a file is
/// copied in, a URL is judged by its payload, and deletion takes the owned copy
/// and the row.
/// </summary>
public class LibraryServiceTests
{
    private static (LibraryService Service, FakeDocumentRepository Documents, FakeFileStore Files) Build(IDocumentFetcher fetcher)
    {
        var documents = new FakeDocumentRepository();
        var files = new FakeFileStore();
        return (new LibraryService(documents, files, fetcher), documents, files);
    }

    private static IDocumentFetcher NoFetch() => new FakeDocumentFetcher(new InvalidOperationException("no network in this test"));

    [Fact]
    public void Importing_a_file_copies_it_in_and_registers_it()
    {
        var (service, documents, files) = Build(NoFetch());
        files.Seed("/home/reader/notes.pdf", Encoding.ASCII.GetBytes("%PDF-1.7"));

        var doc = service.ImportFromFile("/home/reader/notes.pdf");

        Assert.Equal("notes", doc.Title);
        Assert.Equal(DocumentFormat.Pdf, doc.Format);
        Assert.Single(documents.List());
        // The source plus the app-owned copy.
        Assert.Equal(2, files.Count);
        Assert.Equal("/fake/" + doc.FileName, service.GetPath(doc));
    }

    [Fact]
    public void A_file_that_is_not_a_pdf_or_epub_is_refused_by_name()
    {
        var (service, documents, _) = Build(NoFetch());

        var ex = Assert.Throws<InvalidOperationException>(() => service.ImportFromFile("/home/reader/notes.txt"));

        Assert.Contains("notes.txt", ex.Message);
        Assert.Empty(documents.List());
    }

    [Fact]
    public async Task A_url_is_judged_by_its_payload_not_its_name()
    {
        // The URL says .txt, the bytes say PDF: the bytes win.
        var fetched = new FetchedDocument(Encoding.ASCII.GetBytes("%PDF-1.7 ..."), "https://example.org/book.txt", "text/plain", "book.txt");
        var (service, _, _) = Build(new FakeDocumentFetcher(fetched));

        var doc = await service.ImportFromUrlAsync("https://example.org/book.txt");

        Assert.Equal(DocumentFormat.Pdf, doc.Format);
    }

    [Fact]
    public async Task A_url_that_does_not_return_a_document_is_refused()
    {
        var fetched = new FetchedDocument(Encoding.ASCII.GetBytes("<html>nothing here</html>"), "https://example.org/page", "text/html", null);
        var (service, documents, _) = Build(new FakeDocumentFetcher(fetched));

        await Assert.ThrowsAsync<InvalidOperationException>(() => service.ImportFromUrlAsync("https://example.org/page"));

        Assert.Empty(documents.List());
    }

    [Fact]
    public void Importing_downloaded_bytes_sniffs_an_epub_zip()
    {
        var (service, _, _) = Build(NoFetch());

        var doc = service.ImportDownloaded(Encoding.ASCII.GetBytes("PK\u0003\u0004 epub"), "Some Book");

        Assert.Equal(DocumentFormat.Epub, doc.Format);
        Assert.Equal("Some Book", doc.Title);
    }

    [Fact]
    public void Importing_downloaded_bytes_that_are_not_a_book_is_refused()
    {
        var (service, _, _) = Build(NoFetch());

        Assert.Throws<InvalidOperationException>(() =>
            service.ImportDownloaded(Encoding.ASCII.GetBytes("<html>"), "not a book"));
    }

    [Fact]
    public void Deleting_a_document_removes_the_copy_and_the_row()
    {
        var (service, documents, files) = Build(NoFetch());
        files.Seed("/home/reader/notes.epub", Encoding.ASCII.GetBytes("PK\u0003\u0004"));
        var doc = service.ImportFromFile("/home/reader/notes.epub");

        service.Delete(doc);

        Assert.Empty(documents.List());
        // Only the source remains; the app-owned copy is gone.
        Assert.Equal(1, files.Count);
    }
}
