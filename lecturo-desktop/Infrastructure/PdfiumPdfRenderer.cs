using Lecturo.Application.Reading;
using Lecturo.Domain.Reading;
using PDFtoImage;
using SkiaSharp;
using UglyToad.PdfPig;

namespace Lecturo.Infrastructure;

/// <summary>
/// Renders PDF pages to PNG with PDFium (via PDFtoImage) and reads each page's
/// words with PdfPig, normalized to the page so a selection maps onto the image.
/// The page looks as it does in any viewer; the words make it selectable.
/// </summary>
public sealed class PdfiumPdfRenderer : IPdfRenderer
{
    public IReadOnlyList<PdfRenderedPage> Render(string path, int dpi, CancellationToken ct = default)
    {
        var bytes = File.ReadAllBytes(path);
        using var document = PdfDocument.Open(bytes);
        var pages = new List<PdfRenderedPage>();

        foreach (var page in document.GetPages())
        {
            ct.ThrowIfCancellationRequested();

            using var stream = new MemoryStream(bytes);
            using var bitmap = Conversion.ToImage(stream, page: new Index(page.Number - 1), options: new RenderOptions(Dpi: dpi));
            using var image = SKImage.FromBitmap(bitmap);
            using var encoded = image.Encode(SKEncodedImageFormat.Png, 90);

            var words = page.GetWords()
                .Select(word => PdfGeometry.Normalize(
                    word.Text,
                    word.BoundingBox.Left,
                    word.BoundingBox.Bottom,
                    word.BoundingBox.Right,
                    word.BoundingBox.Top,
                    page.Width,
                    page.Height))
                .ToList();

            pages.Add(new PdfRenderedPage(page.Number, encoded.ToArray(), bitmap.Width, bitmap.Height, words));
        }

        return pages;
    }
}
