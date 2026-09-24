using System.Text;
using Lecturo.Application.Catalog;
using Lecturo.Application.Library;
using Lecturo.Domain;
using Xunit;

namespace Lecturo.Tests;

/// <summary>
/// The remote-library download use case: the file is stored, the provenance is
/// recorded, and a second open finds the local copy.
/// </summary>
public class CatalogServiceTests
{
    private static (CatalogService Catalog, FakeDocumentRepository Documents, FakeProvenanceRepository Provenance) Build(IRemoteLibrarySource source)
    {
        var documents = new FakeDocumentRepository();
        var files = new FakeFileStore();
        var library = new LibraryService(documents, files, new FakeDocumentFetcher(new InvalidOperationException("no network")));
        var provenance = new FakeProvenanceRepository();
        var catalog = new CatalogService(source, source, provenance, library);
        return (catalog, documents, provenance);
    }

    [Fact]
    public async Task Downloading_an_item_stores_it_and_records_provenance()
    {
        var source = new FakeRemoteSource("calibre", Encoding.ASCII.GetBytes("%PDF-1.7"));
        var (catalog, documents, provenance) = Build(source);
        var item = new RemoteItem("calibre", "book:pdf", "A Book", "An Author", DocumentFormat.Pdf,
            "https://example.org/book.pdf", null, null);

        var doc = await catalog.DownloadAsync(source, item, "A Book");

        Assert.Single(documents.List());
        Assert.Contains(Provenance.Key("calibre", "book:pdf"), provenance.DownloadedKeys());
        Assert.Equal(doc.Id, catalog.FindDocumentId("calibre", "book:pdf"));
    }

    [Fact]
    public void For_returns_the_source_named_by_the_tab()
    {
        var source = new FakeRemoteSource("calibre", Array.Empty<byte>());
        var (catalog, _, _) = Build(source);

        Assert.Same(source, catalog.For("calibre"));
        Assert.Same(source, catalog.For("Calibre"));
    }
}
