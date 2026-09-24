using Lecturo.Domain;
using Lecturo.Domain.Chat;
using Xunit;

namespace Lecturo.Tests;

/// <summary>
/// Reading a reply into text and maths. A formula is whatever is delimited, never
/// whatever looks like one, so prose stays prose and "$5 and $10" stays money.
/// </summary>
public class MathSplitTests
{
    private static MathPart.Text T(string value) => new(value);

    private static MathPart.Math M(string tex, bool display = false) => new(tex, display);

    [Fact]
    public void Prose_without_delimiters_is_one_text_part()
    {
        Assert.Equal([T("The quick brown fox.")], MathSplit.Split("The quick brown fox."));
    }

    [Fact]
    public void Display_delimiters_carry_their_tex()
    {
        Assert.Equal([M("E = mc^2", display: true)], MathSplit.Split("$$E = mc^2$$"));
        Assert.Equal([M("a + b", display: true)], MathSplit.Split(@"\[a + b\]"));
    }

    [Fact]
    public void Parenthesised_delimiters_are_inline_maths()
    {
        Assert.Equal([M("x^2")], MathSplit.Split(@"\(x^2\)"));
    }

    [Fact]
    public void Maths_interleaves_with_the_text_around_it()
    {
        Assert.Equal([T("Before "), M("x^2"), T(" after")], MathSplit.Split("Before $x^2$ after"));
    }

    [Fact]
    public void Money_is_not_maths()
    {
        Assert.Equal([T("It costs $5 and $10 today")], MathSplit.Split("It costs $5 and $10 today"));
    }

    [Fact]
    public void An_inline_dollar_pair_needs_a_maths_only_character()
    {
        Assert.Equal([M("a=1")], MathSplit.Split("$a=1$"));
        Assert.Equal([T("$abc$")], MathSplit.Split("$abc$"));
    }

    [Fact]
    public void A_delimiter_with_a_space_inside_is_not_maths()
    {
        Assert.Equal([T("$ x $")], MathSplit.Split("$ x $"));
    }

    [Fact]
    public void Delimiters_inside_code_are_left_alone()
    {
        Assert.Equal([T("Use `$x^2$` here")], MathSplit.Split("Use `$x^2$` here"));
        Assert.Equal([T("```\n$$E=mc^2$$\n```")], MathSplit.Split("```\n$$E=mc^2$$\n```"));
    }

    [Fact]
    public void An_unmatched_dollar_is_text()
    {
        Assert.Equal([T("half a $ sign")], MathSplit.Split("half a $ sign"));
    }

    [Fact]
    public void A_reply_says_whether_it_has_maths_at_all()
    {
        Assert.True(MathSplit.HasMath("before $x^2$ after"));
        Assert.False(MathSplit.HasMath("It costs $5 and $10 today"));
        Assert.False(MathSplit.HasMath(null));
    }
}
