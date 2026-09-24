namespace Lecturo.Domain;

/// <summary>
/// One entry from a remote library (Calibre OPDS or Zotero Web API). A catalog
/// entry (Calibre navigation) carries <see cref="CatalogUrl"/> and no format; a
/// downloadable entry carries a format and a download URL.
/// </summary>
public sealed record RemoteItem(
    string Source,
    string RemoteId,
    string Title,
    string Author,
    DocumentFormat? Format,
    string? DownloadUrl,
    string? CatalogUrl,
    string? Summary)
{
    public bool IsCatalog => !string.IsNullOrEmpty(CatalogUrl);

    public string FormatLabel => Format is { } format ? format.ToLabel() : "Catalog";

    public string Initial =>
        string.IsNullOrWhiteSpace(Title) ? "?" : Title.Trim()[..1].ToUpperInvariant();

    public string Meta
    {
        get
        {
            var parts = new List<string>();
            if (!string.IsNullOrWhiteSpace(Author))
                parts.Add(Author.Trim());
            parts.Add(FormatLabel);
            return string.Join(" · ", parts);
        }
    }
}
