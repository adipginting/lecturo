using Lecturo.Domain.Reading;

namespace Lecturo.Application.Reading;

/// <summary>
/// A rendered PDF page: a PNG to display, and its words with normalized boxes so a
/// drag can select the text the image shows.
/// </summary>
public sealed record PdfRenderedPage(
    int Number,
    byte[] Png,
    int PixelWidth,
    int PixelHeight,
    IReadOnlyList<PdfWord> Words);

/// <summary>
/// Renders a PDF into page images with positioned words. A port, so the reader can
/// be driven by a fake in tests and the engine (PDFium) replaced without touching
/// the reading use cases.
/// </summary>
public interface IPdfRenderer
{
    IReadOnlyList<PdfRenderedPage> Render(string path, int dpi, CancellationToken ct = default);
}
