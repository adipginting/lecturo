using Lecturo.Domain;

namespace Lecturo.Application.Library;

/// <summary>The persistence port for the document aggregate (the local library).</summary>
public interface IDocumentRepository
{
    List<Document> List();
    Document? Get(string id);
    string Add(string title, string fileName, DocumentFormat format);
    void UpdateLocator(string id, string locator);
    void Delete(string id);
}
