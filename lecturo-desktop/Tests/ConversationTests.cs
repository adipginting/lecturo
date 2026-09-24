using Lecturo.Domain;
using Lecturo.Domain.Chat;
using Xunit;

namespace Lecturo.Tests;

/// <summary>The life of a conversation: born a draft, sent once, never left nameless.</summary>
public class ConversationTests
{
    private static Conversation NewDraft() =>
        Conversation.Draft(7, Excerpt.FromText("the passage"), "3", "deepseek",
            new DateTime(2026, 9, 21, 9, 0, 0, DateTimeKind.Utc), documentId: 42, documentTitle: "A Book");

    [Fact]
    public void A_new_conversation_is_a_draft()
    {
        var draft = NewDraft();

        Assert.True(draft.IsDraft);
        Assert.Null(draft.SentAt);
        Assert.Equal(ConversationTitle.Untitled, draft.Title);
        Assert.Equal("the passage", draft.Excerpt.Text);
    }

    [Fact]
    public void Sending_names_it_from_the_first_message_and_marks_it_sent()
    {
        var at = new DateTime(2026, 9, 21, 10, 0, 0, DateTimeKind.Utc);
        var sent = NewDraft().Send("Where does it say X?", at);

        Assert.False(sent.IsDraft);
        Assert.Equal(at, sent.SentAt);
        Assert.Equal("Where does it say X?", sent.Title);
        Assert.Equal(at, sent.UpdatedAt);
    }

    [Fact]
    public void A_sent_conversation_cannot_be_sent_again()
    {
        var sent = NewDraft().Send("first", DateTime.UtcNow);
        var again = sent.Send("second", DateTime.UtcNow.AddHours(1));

        Assert.Equal(sent, again);
    }

    [Fact]
    public void Renaming_never_leaves_it_nameless()
    {
        var named = NewDraft().Send("first", DateTime.UtcNow);

        Assert.Equal(named, named.Rename("   "));
        Assert.Equal("A better name", named.Rename("  A   better\nname ").Title);
    }

    [Fact]
    public void Sending_keeps_the_excerpt_frozen()
    {
        var draft = NewDraft();
        var sent = draft.Send("anything", DateTime.UtcNow);

        Assert.Equal(draft.Excerpt, sent.Excerpt);
        Assert.Equal(draft.Locator, sent.Locator);
        Assert.Equal(draft.DocumentId, sent.DocumentId);
    }
}
