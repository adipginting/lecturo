using System;
using Lecturo.Domain.Reading;
using Xunit;

namespace Lecturo.Tests;

/// <summary>
/// The pure PDF selection: what a drag over a rendered page picks, and the text it
/// yields. The view supplies normalized word boxes; this is what makes the result
/// testable without a renderer, a display, or a real PDF.
/// </summary>
public class PdfSelectionTests
{
    private static PdfWord Word(string text, double left, double top, double right, double bottom) =>
        new(text, left, top, right, bottom);

    [Fact]
    public void A_word_whose_centre_is_inside_is_selected()
    {
        var words = new[] { Word("hello", 0.10, 0.10, 0.20, 0.14) };

        Assert.Single(PdfSelection.WordsIn(words, 0.05, 0.05, 0.30, 0.30));
    }

    [Fact]
    public void A_word_whose_centre_is_outside_is_not_selected()
    {
        var words = new[] { Word("far", 0.80, 0.80, 0.90, 0.84) };

        Assert.Empty(PdfSelection.WordsIn(words, 0.05, 0.05, 0.30, 0.30));
    }

    [Fact]
    public void Words_come_back_in_reading_order_not_drag_order()
    {
        var words = new[]
        {
            Word("world", 0.30, 0.10, 0.40, 0.14), // right of "hello"
            Word("second", 0.10, 0.20, 0.20, 0.24), // next line
            Word("hello", 0.10, 0.10, 0.20, 0.14),
        };

        var picked = PdfSelection.WordsIn(words, 0.9, 0.9, 0.0, 0.0);

        Assert.Equal(new[] { "hello", "world", "second" }, picked.Select(w => w.Text));
    }

    [Fact]
    public void Text_joins_a_line_with_spaces_and_new_lines_between_lines()
    {
        var words = new[]
        {
            Word("hello", 0.10, 0.10, 0.20, 0.14),
            Word("world", 0.30, 0.10, 0.40, 0.14),
            Word("again", 0.10, 0.20, 0.20, 0.24),
        };

        Assert.Equal("hello world\nagain", PdfSelection.TextIn(words, 0.0, 0.0, 1.0, 1.0));
    }

    [Fact]
    public void A_drag_that_does_not_enlarge_selects_nothing()
    {
        var words = new[] { Word("hello", 0.10, 0.10, 0.20, 0.14) };

        Assert.Equal(string.Empty, PdfSelection.TextIn(words, 0.5, 0.5, 0.5, 0.5));
    }

    [Fact]
    public void Normalizing_flips_the_pdf_origin_to_the_top_left()
    {
        // A word near the bottom of the page (small PDF y) is near the bottom in
        // normalized space (y near 1), not the top.
        var top = PdfGeometry.Normalize("high", 100, 700, 200, 710, 600, 800);
        var bottom = PdfGeometry.Normalize("low", 100, 50, 200, 60, 600, 800);

        Assert.True(top.Top < bottom.Top);
        Assert.True(top.CenterY < bottom.CenterY);
    }
}
