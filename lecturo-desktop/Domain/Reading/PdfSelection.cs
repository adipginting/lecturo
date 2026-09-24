using System.Text;

namespace Lecturo.Domain.Reading;

/// <summary>
/// Which words a drag on a rendered PDF page selects, and the text they make. Pure:
/// the view hands it normalized word boxes and a normalized drag rectangle.
/// </summary>
public static class PdfSelection
{
    /// <summary>The words whose centre lies inside the rectangle, in reading order.</summary>
    public static IReadOnlyList<PdfWord> WordsIn(
        IReadOnlyList<PdfWord> words,
        double x1,
        double y1,
        double x2,
        double y2)
    {
        var left = Math.Min(x1, x2);
        var right = Math.Max(x1, x2);
        var top = Math.Min(y1, y2);
        var bottom = Math.Max(y1, y2);

        return words
            .Where(w => w.CenterX >= left && w.CenterX <= right && w.CenterY >= top && w.CenterY <= bottom)
            .OrderBy(w => Math.Round(w.CenterY, 2))
            .ThenBy(w => w.CenterX)
            .ToList();
    }

    /// <summary>The selected words as text, a newline when the vertical band changes.</summary>
    public static string TextIn(
        IReadOnlyList<PdfWord> words,
        double x1,
        double y1,
        double x2,
        double y2)
    {
        var selected = WordsIn(words, x1, y1, x2, y2);
        var builder = new StringBuilder();
        double? line = null;

        foreach (var word in selected)
        {
            var current = Math.Round(word.CenterY, 2);
            if (builder.Length > 0)
                builder.Append(line is { } previous && previous == current ? " " : "\n");
            builder.Append(word.Text);
            line = current;
        }

        return builder.ToString();
    }
}
