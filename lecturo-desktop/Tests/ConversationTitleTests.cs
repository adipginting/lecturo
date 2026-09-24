using Lecturo.Domain;
using Lecturo.Domain.Chat;
using Xunit;

namespace Lecturo.Tests;

/// <summary>
/// The sieve every conversation name goes through, whichever direction it came from.
/// </summary>
public class ConversationTitleTests
{
    [Fact]
    public void A_name_keeps_its_words_and_loses_its_layout()
    {
        Assert.Equal("what is this line", ConversationTitle.Normalize("  what   is\n this\tline  "));
    }

    [Fact]
    public void Blank_input_names_nothing()
    {
        Assert.Null(ConversationTitle.Normalize(""));
        Assert.Null(ConversationTitle.Normalize("   \n\t "));
        Assert.Null(ConversationTitle.Normalize(null));
    }

    [Fact]
    public void A_typed_name_is_cut_to_a_readable_length()
    {
        var name = ConversationTitle.Normalize(new string('a', 200));

        Assert.Equal(ConversationTitle.MaxLength, name!.Length);
    }

    [Fact]
    public void A_cut_name_does_not_end_in_a_space()
    {
        // 150 characters of words; the cut lands on the space after the twelfth.
        var name = ConversationTitle.Normalize(string.Concat(Enumerable.Repeat("word ", 30)))!;

        Assert.False(name.EndsWith(" "));
        Assert.Equal(59, name.Length);
    }

    [Fact]
    public void A_first_message_is_named_more_tightly_than_a_typed_name()
    {
        var message = new string('b', 100);

        Assert.Equal(40, ConversationTitle.FromMessage(message)!.Length);
        Assert.Equal(ConversationTitle.MaxLength, ConversationTitle.Normalize(message)!.Length);
    }

    [Fact]
    public void A_name_is_never_cut_through_an_emoji()
    {
        // Cutting at 60 lands between the surrogate halves of the emoji below.
        var name = ConversationTitle.Normalize(new string('a', 59) + "\U0001F600" + "bbbb");

        Assert.Equal(new string('a', 59), name);
        Assert.False(char.IsHighSurrogate(name![^1]));
    }
}
