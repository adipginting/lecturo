using Lecturo.Data;
using Lecturo.Domain;
using Lecturo.Domain.Chat;
using Xunit;

namespace Lecturo.Tests;

/// <summary>
/// The SQLite adapter generates UUIDv7 keys itself (SQLite has no v7), so a fresh
/// database — which still runs the v9 rebuild — must round-trip every aggregate on
/// TEXT ids and keep foreign references pointing at those ids.
/// </summary>
public class RepositoryIdTests
{
    private static Database NewDatabase()
    {
        var directory = Path.Combine(Path.GetTempPath(), "lecturo-tests");
        Directory.CreateDirectory(directory);
        var path = Path.Combine(directory, Guid.NewGuid().ToString("N") + ".db");
        return new Database(path);
    }

    private static bool IsUuidV7(string value) =>
        value.Length == 36 &&
        value[8] == '-' && value[13] == '-' && value[18] == '-' && value[23] == '-' &&
        value[14] == '7' &&
        "89ab".Contains(value[19]);

    [Fact]
    public void A_fresh_database_stores_uuid_v7_keys_across_the_aggregates()
    {
        var database = NewDatabase();

        var documents = new DocumentRepository(database);
        var docId = documents.Add("A Book", "a.epub", DocumentFormat.Epub);
        Assert.True(IsUuidV7(docId));
        Assert.Equal(docId, documents.Get(docId)!.Id);

        var prompts = new PromptRepository(database);
        var promptId = prompts.Add("Explain", "Explain this simply.");
        Assert.True(IsUuidV7(promptId));
        Assert.Equal(promptId, prompts.Get(promptId)!.Id);

        var provenance = new DocumentSourceRepository(database);
        var sourceId = provenance.Add(docId, "calibre", "book:epub", "https://example.org/a.epub", "A Book");
        Assert.True(IsUuidV7(sourceId));
        Assert.Equal(docId, provenance.FindDocumentId("calibre", "book:epub"));

        var skills = new SkillRepository(database);
        skills.Install(new RemoteSkill("summarize", "Summarize", "", "", "1.0", "message", "on_demand", "Summarize this.", "free"));
        var installed = Assert.Single(skills.ListInstalled());
        Assert.True(IsUuidV7(installed.Id));
        Assert.Equal("summarize", installed.SkillId);

        // Re-installing updates the same row and keeps its id.
        skills.Install(new RemoteSkill("summarize", "Summarize", "", "", "1.1", "message", "on_demand", "Summarize this.", "free"));
        Assert.Equal(installed.Id, Assert.Single(skills.ListInstalled()).Id);

        var runId = skills.AddRun(docId, installed, RunStatus.Running);
        Assert.True(IsUuidV7(runId));
        skills.CompleteRun(runId, RunStatus.Done, "the insight", "");
        var run = Assert.Single(skills.ListRuns(docId));
        Assert.Equal(runId, run.Id);
        Assert.Equal(docId, run.DocumentId);

        var chat = new ChatRepository(database);
        var conversationId = chat.CreateDraft(Excerpt.FromText("a passage"), "1", "deepseek", documentId: docId, promptId: promptId);
        Assert.True(IsUuidV7(conversationId));
        var messageId = chat.AddMessage(conversationId, MessageRole.User, "hello");
        Assert.True(IsUuidV7(messageId));

        var conversation = chat.Get(conversationId)!;
        Assert.Equal(docId, conversation.DocumentId);
        Assert.Equal(promptId, conversation.PromptId);
        Assert.Equal(conversationId, Assert.Single(chat.ListMessages(conversationId)).ConversationId);
    }
}
