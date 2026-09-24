using Lecturo.Services;
using Xunit;

namespace Lecturo.Tests;

/// <summary>
/// The page a maths message is rendered from: prose keeps its shapes and its
/// escaping, formulas arrive as TeX in data attributes for KaTeX to typeset.
/// </summary>
public class MathsHtmlTests
{
    [Fact]
    public void Markup_in_prose_is_escaped_and_shapes_survive()
    {
        Assert.Contains("<p>a &lt; b &amp; c</p>", MathsHtml.Page("a < b & c"));
        Assert.Contains("<h2>Title</h2>", MathsHtml.Page("## Title"));
        Assert.Contains("<strong>bold</strong>", MathsHtml.Page("**bold**"));
    }

    [Fact]
    public void A_formula_is_handed_over_as_tex_data()
    {
        var page = MathsHtml.Page("Before $x^2$ after");

        Assert.Contains("<span class=\"math\" data-tex=\"x^2\" data-display=\"false\"></span>", page);
        Assert.Contains("Before ", page);
        Assert.Contains(" after", page);
    }

    [Fact]
    public void Display_maths_says_so()
    {
        Assert.Contains("data-tex=\"E = mc^2\" data-display=\"true\"", MathsHtml.Page("$$E = mc^2$$"));
    }

    [Fact]
    public void Money_stays_prose()
    {
        var page = MathsHtml.Page("It costs $5 and $10 today");

        // The page's own script mentions data-tex; what must be absent is a formula element.
        Assert.DoesNotContain("class=\"math\"", page);
        Assert.Contains("It costs $5 and $10 today", page);
    }

    [Fact]
    public void The_page_loads_the_bundled_katex_and_reports_its_height()
    {
        var page = MathsHtml.Page("$x^2$");

        Assert.Contains("katex.min.css", page);
        Assert.Contains("katex.min.js", page);
        Assert.Contains("invokeCSharpAction", page);
        Assert.Contains("katex.render(", page);
    }

    [Fact]
    public void The_katex_assets_are_copied_beside_the_app()
    {
        // The page loads them from disk, so they have to survive the build.
        var katex = Path.Combine(AppContext.BaseDirectory, "Assets", "katex");

        Assert.True(File.Exists(Path.Combine(katex, "katex.min.js")));
        Assert.True(File.Exists(Path.Combine(katex, "katex.min.css")));
        Assert.NotEmpty(Directory.GetFiles(Path.Combine(katex, "fonts"), "*.woff2"));
    }

    [Fact]
    public void Tex_with_quotes_cannot_break_out_of_the_attribute()
    {
        var page = MathsHtml.Page("$\\text{\"a\"}^2$");

        Assert.Contains("&quot;a&quot;", page);
        Assert.DoesNotContain("data-tex=\"\\text{\"a\"}^2\"", page);
    }
}
