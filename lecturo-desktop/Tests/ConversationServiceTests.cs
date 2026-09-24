using Lecturo.Application.Chat;
using Lecturo.Domain;
using Lecturo.Domain.Chat;
using Xunit;

namespace Lecturo.Tests;

/// <summary>
/// The chat use cases, tested without a database or a network: fakes implement
/// the ports. These pin the life of a conversation that used to live — untested —
/// inside the 471-line ChatPanelViewModel: a send names and sends a draft, a
/// failed send leaves it a draft, compaction folds the old turns, and a blank
/// name is refused.
/// </summary>
public class ConversationServiceTests
{
    private const string Provider = "deepseek";

    private static (ConversationService Service, FakeConversationRepository Conversations, FakePromptRepository Prompts) Build(IChatClient chat)
    {
        var conversations = new FakeConversationRepository();
        var prompts = new FakePromptRepository();
        return (new ConversationService(conversations, prompts, chat), conversations, prompts);
    }

    [Fact]
    public async Task Sending_the_first_message_names_the_draft_and_sends_it()
    {
        var (service, conversations, _) = Build(new FakeChatClient("the answer"));
        var id = service.CreateDraft(Excerpt.FromText("a passage"), "12", Provider, documentId: 7);

        var result = await service.SendAsync(id, Provider, "What does this mean?");

        Assert.Null(result.Error);
        Assert.True(result.BecameSent);
        Assert.Equal("What does this mean?", result.Title);
        Assert.Equal("the answer", result.Assistant!.Content);

        var saved = conversations.Get(id)!;
        Assert.False(saved.IsDraft);
        Assert.Equal("What does this mean?", saved.Title);
        Assert.Equal(2, conversations.ListMessages(id).Count);
    }

    [Fact]
    public async Task A_failed_send_leaves_the_draft_a_draft_and_keeps_the_user_message()
    {
        var (service, conversations, _) = Build(new FakeChatClient(new InvalidOperationException("No API key configured.")));
        var id = service.CreateDraft(Excerpt.FromText("a passage"), "12", Provider);

        var result = await service.SendAsync(id, Provider, "hello");

        Assert.Equal("No API key configured.", result.Error);
        Assert.False(result.BecameSent);
        Assert.Null(result.Assistant);
        Assert.True(conversations.Get(id)!.IsDraft);
        Assert.Single(conversations.ListMessages(id));
    }

    [Fact]
    public async Task An_empty_first_message_is_refused_before_anything_is_written()
    {
        var (service, conversations, _) = Build(new FakeChatClient("the answer"));
        var id = service.CreateDraft(Excerpt.FromText("a passage"), "12", Provider);

        await Assert.ThrowsAsync<InvalidOperationException>(() => service.SendAsync(id, Provider, "   "));

        Assert.Empty(conversations.ListMessages(id));
        Assert.True(conversations.Get(id)!.IsDraft);
    }

    [Fact]
    public async Task Compaction_with_too_few_turns_does_nothing()
    {
        var (service, conversations, _) = Build(new FakeChatClient("summary"));
        var id = service.CreateDraft(Excerpt.FromText("a passage"), "12", Provider);
        conversations.AddMessage(id, MessageRole.User, "one");
        conversations.AddMessage(id, MessageRole.Assistant, "two");

        var result = await service.CompactAsync(id, Provider);

        Assert.True(result.NothingToCompact);
        Assert.Null(result.Summary);
        Assert.Empty(result.SupersededIds);
    }

    [Fact]
    public async Task Compaction_folds_the_active_turns_and_clears_the_reported_usage()
    {
        var (service, conversations, _) = Build(new FakeChatClient("a summary"));
        var id = service.CreateDraft(Excerpt.FromText("a passage"), "12", Provider);
        for (var i = 0; i < 4; i++)
            conversations.AddMessage(id, i % 2 == 0 ? MessageRole.User : MessageRole.Assistant, $"turn {i}");
        conversations.RecordUsage(id, 1234, DateTime.UtcNow);

        var result = await service.CompactAsync(id, Provider);

        Assert.Null(result.Error);
        Assert.NotNull(result.Summary);
        Assert.Equal(MessageRole.Summary, result.Summary!.Role);
        Assert.Equal(4, result.SupersededIds.Count);

        var messages = conversations.ListMessages(id);
        Assert.Equal(5, messages.Count);
        Assert.Equal(4, messages.Count(m => m.Superseded));
        Assert.Single(messages.Where(m => m.IsActive));
        Assert.Null(conversations.Get(id)!.LastPromptTokens);
    }

    [Fact]
    public void A_blank_name_is_refused_and_the_old_one_stands()
    {
        var (service, conversations, _) = Build(new FakeChatClient("x"));
        var id = service.CreateDraft(Excerpt.FromText("a passage"), "12", Provider);

        Assert.Null(service.Rename(id, "   "));
        Assert.Equal(ConversationTitle.Untitled, conversations.Get(id)!.Title);

        var renamed = service.Rename(id, "  My  chat  ");
        Assert.NotNull(renamed);
        Assert.Equal("My chat", renamed!.Title);
    }

    [Fact]
    public void Switching_provider_is_persisted_on_the_conversation()
    {
        var (service, conversations, _) = Build(new FakeChatClient("x"));
        var id = service.CreateDraft(Excerpt.FromText("a passage"), "12", Provider);

        service.SwitchProvider(id, "anthropic");

        Assert.Equal("anthropic", conversations.Get(id)!.Provider);
    }

    [Fact]
    public void The_system_prompt_carries_the_chosen_prompt_and_the_frozen_excerpt()
    {
        var (service, conversations, prompts) = Build(new FakeChatClient("x"));
        var promptId = prompts.Add("Tone", "Be terse.");
        var id = service.CreateDraft(Excerpt.FromText("The passage under discussion."), "1", Provider, promptId: promptId);

        var systemPrompt = service.BuildSystemPrompt(conversations.Get(id)!);

        Assert.Contains("Be terse.", systemPrompt);
        Assert.Contains("The passage under discussion.", systemPrompt);
    }

    [Fact]
    public void A_screenshot_prompt_points_at_the_image_not_an_excerpt()
    {
        var (service, conversations, _) = Build(new FakeChatClient("x"));
        var id = service.CreateDraft(Excerpt.FromImage("/tmp/shot.png"), "", Provider);

        var systemPrompt = service.BuildSystemPrompt(conversations.Get(id)!);

        Assert.Contains("screenshot", systemPrompt);
    }
}
