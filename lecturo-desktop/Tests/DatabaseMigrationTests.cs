using Lecturo.Data;
using Lecturo.Domain;
using Lecturo.Domain.Chat;
using Microsoft.Data.Sqlite;
using Xunit;

namespace Lecturo.Tests;

/// <summary>
/// Schema v7 drops the saved queue and turns a draft into an unsent conversation.
/// The queue's rows have to survive that, and so do the conversations already there.
/// </summary>
public class DatabaseMigrationTests
{
    /// <summary>A database as the app left it at v6, with something in every table that matters.</summary>
    private const string V6Schema = """
        CREATE TABLE documents(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            file_name TEXT NOT NULL,
            format TEXT NOT NULL,
            last_locator TEXT,
            updated_at TEXT NOT NULL
        );
        CREATE TABLE basket_items(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            document_id INTEGER NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
            text TEXT NOT NULL,
            locator TEXT NOT NULL,
            created_at TEXT NOT NULL
        );
        CREATE TABLE saved_prompts(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            content TEXT NOT NULL
        );
        CREATE TABLE conversations(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            saved_prompt_id INTEGER REFERENCES saved_prompts(id) ON DELETE SET NULL,
            created_at TEXT NOT NULL,
            document_id INTEGER REFERENCES documents(id) ON DELETE SET NULL,
            excerpt TEXT NOT NULL DEFAULT '',
            locator TEXT NOT NULL DEFAULT '',
            provider TEXT NOT NULL DEFAULT '',
            updated_at TEXT NOT NULL DEFAULT '',
            context_image_path TEXT NOT NULL DEFAULT '',
            system_prompt TEXT NOT NULL DEFAULT ''
        );
        CREATE TABLE messages(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            conversation_id INTEGER NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
            role TEXT NOT NULL,
            content TEXT NOT NULL,
            created_at TEXT NOT NULL,
            superseded INTEGER NOT NULL DEFAULT 0
        );
        CREATE TABLE document_sources(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            document_id INTEGER NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
            source TEXT NOT NULL,
            remote_id TEXT NOT NULL,
            url TEXT NOT NULL DEFAULT '',
            title TEXT NOT NULL DEFAULT '',
            created_at TEXT NOT NULL
        );
        CREATE INDEX idx_document_sources_remote ON document_sources(source, remote_id);
        CREATE TABLE skills(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            skill_id TEXT NOT NULL UNIQUE,
            name TEXT NOT NULL,
            description TEXT NOT NULL DEFAULT '',
            author TEXT NOT NULL DEFAULT '',
            version TEXT NOT NULL DEFAULT '',
            scope TEXT NOT NULL DEFAULT 'message',
            trigger TEXT NOT NULL DEFAULT 'on_demand',
            prompt TEXT NOT NULL DEFAULT '',
            price TEXT NOT NULL DEFAULT '',
            enabled INTEGER NOT NULL DEFAULT 1,
            installed_at TEXT NOT NULL
        );
        CREATE TABLE skill_runs(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            document_id INTEGER REFERENCES documents(id) ON DELETE CASCADE,
            skill_id TEXT NOT NULL,
            skill_name TEXT NOT NULL,
            status TEXT NOT NULL,
            result TEXT NOT NULL DEFAULT '',
            error TEXT NOT NULL DEFAULT '',
            created_at TEXT NOT NULL
        );

        INSERT INTO documents(id, title, file_name, format, last_locator, updated_at)
        VALUES (1, 'A Book', 'a.epub', 'epub', '2', '2026-09-01T00:00:00.0000000Z');
        INSERT INTO basket_items(id, document_id, text, locator, created_at)
        VALUES (1, 1, 'a passage waiting to be asked about', '2', '2026-09-02T00:00:00.0000000Z');
        INSERT INTO saved_prompts(id, name, content) VALUES (1, 'Explain', 'Explain this simply.');
        INSERT INTO conversations(id, title, saved_prompt_id, created_at, document_id, excerpt, locator, provider, updated_at)
        VALUES (1, 'Earlier chat', 1, '2026-08-30T00:00:00.0000000Z', 1, 'older passage', '1', 'deepseek',
                '2026-08-31T00:00:00.0000000Z');
        INSERT INTO messages(conversation_id, role, content, created_at)
        VALUES (1, 'user', 'a question', '2026-08-30T00:00:00.0000000Z');

        PRAGMA user_version = 6;
        """;

    private static string SeedDatabase()
    {
        var directory = Path.Combine(Path.GetTempPath(), "lecturo-tests");
        Directory.CreateDirectory(directory);
        var path = Path.Combine(directory, Guid.NewGuid().ToString("N") + ".db");
        using var conn = new SqliteConnection($"Data Source={path}");
        conn.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = V6Schema;
        cmd.ExecuteNonQuery();
        return path;
    }

    private static T Scalar<T>(Database database, string sql)
    {
        using var conn = database.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = sql;
        var value = cmd.ExecuteScalar();
        return value is null or DBNull ? default! : (T)Convert.ChangeType(value, typeof(T));
    }

    private static bool TableExists(Database database, string name) =>
        Scalar<long>(database, $"SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = '{name}';") == 1;

    [Fact]
    public void The_queue_becomes_drafts_and_its_table_goes()
    {
        var path = SeedDatabase();
        var database = new Database(path);

        Assert.Equal(8L, Scalar<long>(database, "PRAGMA user_version;"));
        Assert.False(TableExists(database, "basket_items"));

        // The saved passage is now an unsent conversation carrying the same words.
        Assert.Equal(1L, Scalar<long>(database,
            "SELECT COUNT(*) FROM conversations WHERE sent_at IS NULL AND title = 'New conversation' AND excerpt = 'a passage waiting to be asked about' AND locator = '2' AND document_id = 1;"));
    }

    [Fact]
    public void A_conversation_that_already_existed_counts_as_sent()
    {
        var path = SeedDatabase();
        var database = new Database(path);

        // Under the old model a row survived only if a reply landed, so its last
        // update is the moment it was sent — not null, which would make it a draft.
        Assert.Equal("2026-08-31T00:00:00.0000000Z", Scalar<string>(database,
            "SELECT sent_at FROM conversations WHERE id = 1;"));
    }

    [Fact]
    public void The_prompt_table_and_its_foreign_key_survive_the_rename()
    {
        var path = SeedDatabase();
        var database = new Database(path);

        Assert.True(TableExists(database, "prompts"));
        Assert.False(TableExists(database, "saved_prompts"));
        Assert.Equal("Explain", Scalar<string>(database, "SELECT name FROM prompts WHERE id = 1;"));
        Assert.Equal(1L, Scalar<long>(database, "SELECT prompt_id FROM conversations WHERE id = 1;"));

        // A dangling reference must still be refused, or the rename quietly broke the key.
        using var conn = database.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "INSERT INTO conversations(title, prompt_id, created_at) VALUES ('x', 999, '');";
        Assert.Throws<SqliteException>(() => cmd.ExecuteNonQuery());
    }

    [Fact]
    public void The_repository_reads_the_migrated_rows_as_a_draft_and_a_conversation()
    {
        var path = SeedDatabase();
        var database = new Database(path);
        var chat = new ChatRepository(database);

        var draft = Assert.Single(chat.ListDrafts());
        Assert.True(draft.IsDraft);
        Assert.Equal("a passage waiting to be asked about", draft.Excerpt.Text);
        Assert.Equal("A Book", draft.DocumentTitle);

        var conversation = Assert.Single(chat.ListConversations());
        Assert.False(conversation.IsDraft);
        Assert.Equal("Earlier chat", conversation.Title);
        Assert.Equal(1, conversation.PromptId);

        Assert.Equal(draft.Id, chat.FindDraftForDocument(1)!.Id);
        Assert.Equal(conversation.Id, chat.FindActiveConversationForDocument(1)!.Id);
    }

    [Fact]
    public void Messaging_a_draft_sends_it_and_names_it()
    {
        var path = SeedDatabase();
        var database = new Database(path);
        var chat = new ChatRepository(database);
        var draft = chat.FindDraftForDocument(1)!;

        var at = new DateTime(2026, 9, 21, 12, 0, 0, DateTimeKind.Utc);
        chat.AddMessage(draft.Id, MessageRole.User, "Where does it say X?");
        chat.MarkSent(draft.Id, ConversationTitle.FromMessage("Where does it say X?")!, at);

        Assert.Empty(chat.ListDrafts());
        // The database also holds the conversation that was already there.
        Assert.Equal(2, chat.ListConversations().Count);
        var sent = chat.FindActiveConversationForDocument(1)!;
        Assert.Equal("Where does it say X?", sent.Title);
        Assert.Equal(at, sent.SentAt!.Value.ToUniversalTime());
        Assert.Single(chat.ListMessages(sent.Id));
    }
}
