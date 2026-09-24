namespace Lecturo.Domain;

/// <summary>
/// How the reading surface shows a document. Distinct from <see cref="DocumentFormat"/>:
/// that says which kind of file it is, this says which surface can render it.
/// </summary>
public enum RenderMode
{
    /// <summary>PDFium-rendered PDF pages: the page image, with words for selection.</summary>
    PdfPages,

    /// <summary>
    /// A PDF that could not be rendered, handed to WebKitGTK's own viewer. The last
    /// resort: that surface cannot be selected or captured.
    /// </summary>
    Pdf,

    /// <summary>An EPUB, converted to Markdown for the native reading surface.</summary>
    Epub,
}

public static class RenderModeExtensions
{
    /// <summary>Whether WebKit renders it, which means the app cannot select or capture it.</summary>
    public static bool UsesWebView(this RenderMode mode) => mode == RenderMode.Pdf;
}
