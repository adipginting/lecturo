using Lecturo.Domain;

namespace Lecturo.Application.Library;

/// <summary>
/// What a payload is, decided by its bytes first and its URL or headers only as
/// a fallback — a book URL does not always answer with the book.
/// </summary>
public static class DocumentFormats
{
    /// <summary>A PDF's <c>%PDF-</c>, or a ZIP container (EPUB is one).</summary>
    public static DocumentFormat? FromMagic(byte[] bytes)
    {
        if (bytes.Length > 4 && bytes[0] == (byte)'%' && bytes[1] == (byte)'P')
            return DocumentFormat.Pdf;
        if (bytes.Length > 4 && bytes[0] == (byte)'P' && bytes[1] == (byte)'K')
            return DocumentFormat.Epub;
        return null;
    }

    public static DocumentFormat? FromExtension(string extension) => extension.ToLowerInvariant() switch
    {
        ".pdf" => DocumentFormat.Pdf,
        ".epub" => DocumentFormat.Epub,
        _ => null,
    };

    public static DocumentFormat? FromContentType(string? mediaType) => mediaType?.ToLowerInvariant() switch
    {
        "application/pdf" => DocumentFormat.Pdf,
        "application/epub+zip" => DocumentFormat.Epub,
        _ => null,
    };
}
