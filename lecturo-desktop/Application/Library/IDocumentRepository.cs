using Lecturo.Domain;

namespace Lecturo.Application.Library;

/// <summary>The persistence port for the document aggregate (the local library).</summary>
public interface IDocumentRepository
{
    List<Document> List();
    Document? Get(long id);
    long Add(string title, string fileName, DocumentFormat format);
    void UpdateLocator(long id, string locator);
    void Delete(long id);
}
