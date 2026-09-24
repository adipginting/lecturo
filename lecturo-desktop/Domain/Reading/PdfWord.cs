namespace Lecturo.Domain.Reading;

/// <summary>
/// A word on a PDF page, in normalized page coordinates: x and y in 0..1 with the
/// origin at the top-left, so a selection is independent of the render resolution.
/// </summary>
public sealed record PdfWord(string Text, double Left, double Top, double Right, double Bottom)
{
    public double CenterX => (Left + Right) / 2;

    public double CenterY => (Top + Bottom) / 2;
}

/// <summary>Turns a PDF-space box (origin bottom-left, in points) into a normalized one.</summary>
public static class PdfGeometry
{
    public static PdfWord Normalize(
        string text,
        double left,
        double bottom,
        double right,
        double top,
        double pageWidth,
        double pageHeight)
    {
        var x1 = left / pageWidth;
        var x2 = right / pageWidth;
        var y1 = 1 - (top / pageHeight);
        var y2 = 1 - (bottom / pageHeight);
        return new PdfWord(
            text,
            Math.Min(x1, x2),
            Math.Min(y1, y2),
            Math.Max(x1, x2),
            Math.Max(y1, y2));
    }
}
