using Lecturo.Application.Chat;
using Lecturo.Application.Skills;
using Lecturo.Domain;
using Xunit;

namespace Lecturo.Tests;

/// <summary>
/// Skill runs: a paid skill refuses to run, and a free run lands as a sent
/// conversation with the run recorded (S41/S43).
/// </summary>
public class SkillServiceTests
{
    private const string Provider = "deepseek";

    private static (SkillService Service, FakeSkillRepository Skills, FakeConversationRepository Conversations) Build(IChatClient chat)
    {
        var skills = new FakeSkillRepository();
        var conversations = new FakeConversationRepository();
        var prompts = new FakePromptRepository();
        var conversationService = new ConversationService(conversations, prompts, chat);
        return (new SkillService(skills, new FakeSkillCatalog(), conversationService, chat), skills, conversations);
    }

    private static Skill Skill(string id, string price) =>
        new("0", id, id, "", "", "1.0", "message", "on_demand", "Summarize this.", price, true);

    private static Document Doc() => new("7", "A Book", "book.epub", DocumentFormat.Epub, null, DateTime.UtcNow);

    [Fact]
    public async Task A_paid_skill_is_refused_before_a_run_is_recorded()
    {
        var (service, skills, _) = Build(new FakeChatClient("answer"));

        await Assert.ThrowsAsync<InvalidOperationException>(() =>
            service.RunAsync(Doc(), Skill("paid", "$5"), "context", Provider));

        Assert.Empty(skills.Runs);
    }

    [Fact]
    public async Task A_free_run_becomes_a_sent_conversation_and_records_done()
    {
        var (service, skills, conversations) = Build(new FakeChatClient("the insight"));

        var conversationId = await service.RunAsync(Doc(), Skill("summarize", "free"), "context text", Provider);

        var saved = conversations.Get(conversationId)!;
        Assert.False(saved.IsDraft);
        Assert.Contains(saved.Title, saved.Title); // named from the skill and document
        Assert.Single(skills.Runs);
        Assert.Equal(RunStatus.Done, skills.Runs[0].Status);
        Assert.Equal("the insight", skills.Runs[0].Result);
    }
}
