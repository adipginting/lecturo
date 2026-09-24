using Lecturo.Application.Chat;
using Lecturo.Domain;
using Lecturo.Domain.Chat;
using Microsoft.Data.Sqlite;

namespace Lecturo.Data;

/// <summary>
/// Conversations and their messages. A draft is a conversation with no
/// <c>sent_at</c>, so the two live in one table and are told apart by that column
/// rather than by living in different ones. Implements the application's
/// <see cref="IConversationRepository"/> port; prompt templates are their own
/// aggregate and their own repository.
/// </summary>
public sealed class ChatRepository : IConversationRepository
{
    private const string Columns =
        "c.id, c.title, c.prompt_id, c.document_id, d.title, c.excerpt, c.context_image_path, " +
        "c.locator, c.provider, c.system_prompt, c.created_at, c.updated_at, c.sent_at, d.format, " +
        "c.last_prompt_tokens, c.last_call_at";

    private const string Joined =
        "FROM conversations c LEFT JOIN documents d ON d.id = c.document_id";

    private readonly Database _db;

    public ChatRepository(Database db) => _db = db;

    // ---- Conversations ----

    /// <summary>Every unsent conversation, newest first: the Drafts list.</summary>
    public List<Conversation> ListDrafts() =>
        List("c.sent_at IS NULL", "c.created_at DESC");

    /// <summary>Every conversation that has been sent, most recently engaged first.</summary>
    public List<Conversation> ListConversations() =>
        List("c.sent_at IS NOT NULL", "c.updated_at DESC");

    private List<Conversation> List(string where, string orderBy)
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = $"SELECT {Columns} {Joined} WHERE {where} ORDER BY {orderBy};";
        using var reader = cmd.ExecuteReader();
        var result = new List<Conversation>();
        while (reader.Read())
            result.Add(ReadConversation(reader));
        return result;
    }

    public Conversation? Get(long id)
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = $"SELECT {Columns} {Joined} WHERE c.id = $id;";
        cmd.Parameters.AddWithValue("$id", id);
        using var reader = cmd.ExecuteReader();
        return reader.Read() ? ReadConversation(reader) : null;
    }

    /// <summary>The document's newest unsent draft, if it has one.</summary>
    public Conversation? FindDraftForDocument(long documentId)
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = $"""
            SELECT {Columns} {Joined}
            WHERE c.document_id = $docId AND c.sent_at IS NULL
            ORDER BY c.created_at DESC, c.id DESC LIMIT 1;
            """;
        cmd.Parameters.AddWithValue("$docId", documentId);
        using var reader = cmd.ExecuteReader();
        return reader.Read() ? ReadConversation(reader) : null;
    }

    /// <summary>The sent conversation fired from this document that was engaged with most recently.</summary>
    public Conversation? FindActiveConversationForDocument(long documentId)
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = $"""
            SELECT {Columns} {Joined}
            WHERE c.document_id = $docId AND c.sent_at IS NOT NULL
            ORDER BY c.updated_at DESC, c.id DESC LIMIT 1;
            """;
        cmd.Parameters.AddWithValue("$docId", documentId);
        using var reader = cmd.ExecuteReader();
        return reader.Read() ? ReadConversation(reader) : null;
    }

    /// <summary>
    /// Records a freshly captured excerpt as a draft. Asking about a passage makes
    /// one of these and nothing more: it is a conversation only once it is sent.
    /// </summary>
    public long CreateDraft(
        Excerpt excerpt,
        string locator,
        string provider,
        long? documentId = null,
        long? promptId = null,
        string systemPrompt = "") =>
        Insert(ConversationTitle.Untitled, excerpt, locator, provider, documentId, promptId, systemPrompt, sentAt: null);

    /// <summary>
    /// Records a conversation that arrives already answered — a skill run does its
    /// own asking, so its result is not a draft.
    /// </summary>
    public long CreateSentConversation(
        string title,
        Excerpt excerpt,
        string locator,
        string provider,
        DateTime sentAt,
        long? documentId = null,
        long? promptId = null,
        string systemPrompt = "") =>
        Insert(title, excerpt, locator, provider, documentId, promptId, systemPrompt, sentAt);

    private long Insert(
        string title,
        Excerpt excerpt,
        string locator,
        string provider,
        long? documentId,
        long? promptId,
        string systemPrompt,
        DateTime? sentAt)
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = """
            INSERT INTO conversations(title, prompt_id, document_id, excerpt, locator, provider,
                                      context_image_path, system_prompt, created_at, updated_at, sent_at)
            VALUES ($title, $promptId, $docId, $excerpt, $locator, $provider,
                    $imagePath, $systemPrompt, $now, $now, $sentAt);
            SELECT last_insert_rowid();
            """;
        var now = DateTime.UtcNow.ToString("O");
        cmd.Parameters.AddWithValue("$title", title);
        cmd.Parameters.AddWithValue("$promptId", (object?)promptId ?? DBNull.Value);
        cmd.Parameters.AddWithValue("$docId", (object?)documentId ?? DBNull.Value);
        cmd.Parameters.AddWithValue("$excerpt", excerpt.TextOrEmpty);
        cmd.Parameters.AddWithValue("$locator", locator);
        cmd.Parameters.AddWithValue("$provider", provider);
        cmd.Parameters.AddWithValue("$imagePath", excerpt.ImagePath ?? "");
        cmd.Parameters.AddWithValue("$systemPrompt", systemPrompt);
        cmd.Parameters.AddWithValue("$now", now);
        cmd.Parameters.AddWithValue("$sentAt", sentAt is { } at ? at.ToUniversalTime().ToString("O") : DBNull.Value);
        return (long)(cmd.ExecuteScalar() ?? 0L);
    }

    /// <summary>
    /// Marks a draft sent and records the name it took from its first message.
    /// Nothing else changes: the excerpt was frozen when the draft was made.
    /// </summary>
    public void MarkSent(long id, string title, DateTime sentAt)
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = """
            UPDATE conversations SET title = $title, sent_at = $sentAt, updated_at = $sentAt
            WHERE id = $id AND sent_at IS NULL;
            """;
        var at = sentAt.ToUniversalTime().ToString("O");
        cmd.Parameters.AddWithValue("$title", title);
        cmd.Parameters.AddWithValue("$sentAt", at);
        cmd.Parameters.AddWithValue("$id", id);
        cmd.ExecuteNonQuery();
    }

    public void RenameConversation(long id, string title)
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "UPDATE conversations SET title = $title, updated_at = $now WHERE id = $id;";
        cmd.Parameters.AddWithValue("$title", title);
        cmd.Parameters.AddWithValue("$now", DateTime.UtcNow.ToString("O"));
        cmd.Parameters.AddWithValue("$id", id);
        cmd.ExecuteNonQuery();
    }

    public void SetConversationProvider(long id, string provider)
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "UPDATE conversations SET provider = $provider, updated_at = $now WHERE id = $id;";
        cmd.Parameters.AddWithValue("$provider", provider);
        cmd.Parameters.AddWithValue("$now", DateTime.UtcNow.ToString("O"));
        cmd.Parameters.AddWithValue("$id", id);
        cmd.ExecuteNonQuery();
    }

    public void TouchConversation(long id)
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "UPDATE conversations SET updated_at = $now WHERE id = $id;";
        cmd.Parameters.AddWithValue("$now", DateTime.UtcNow.ToString("O"));
        cmd.Parameters.AddWithValue("$id", id);
        cmd.ExecuteNonQuery();
    }

    public void DeleteConversation(long id)
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "DELETE FROM conversations WHERE id = $id;";
        cmd.Parameters.AddWithValue("$id", id);
        cmd.ExecuteNonQuery();
    }

    /// <summary>
    /// Records what the provider said a reply cost. A missing count is stored as
    /// missing rather than zero — "we do not know" and "nothing" are different.
    /// </summary>
    public void RecordUsage(long id, int? promptTokens, DateTime at)
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = """
            UPDATE conversations
               SET last_prompt_tokens = $tokens, last_call_at = $at, updated_at = $at
             WHERE id = $id;
            """;
        var stamp = at.ToUniversalTime().ToString("O");
        cmd.Parameters.AddWithValue("$tokens", (object?)promptTokens ?? DBNull.Value);
        cmd.Parameters.AddWithValue("$at", stamp);
        cmd.Parameters.AddWithValue("$id", id);
        cmd.ExecuteNonQuery();
    }

    /// <summary>
    /// Forgets what the last reply cost. Compaction rewrites the payload the
    /// reported count described, so keeping it would mislead the counter.
    /// </summary>
    public void ClearUsage(long id)
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "UPDATE conversations SET last_prompt_tokens = NULL, last_call_at = NULL WHERE id = $id;";
        cmd.Parameters.AddWithValue("$id", id);
        cmd.ExecuteNonQuery();
    }

    private static Conversation ReadConversation(SqliteDataReader r) => new(
        Id: r.GetInt64(0),
        Title: r.GetString(1),
        PromptId: r.IsDBNull(2) ? null : r.GetInt64(2),
        DocumentId: r.IsDBNull(3) ? null : r.GetInt64(3),
        DocumentTitle: r.IsDBNull(4) ? null : r.GetString(4),
        Excerpt: new Excerpt(NullIfEmpty(r.GetString(5)), NullIfEmpty(r.GetString(6))),
        Locator: r.GetString(7),
        Provider: r.GetString(8),
        SystemPrompt: r.GetString(9),
        CreatedAt: Time(r.GetString(10)),
        UpdatedAt: r.GetString(11).Length == 0 ? Time(r.GetString(10)) : Time(r.GetString(11)),
        SentAt: r.IsDBNull(12) ? null : Time(r.GetString(12)),
        DocumentFormat: r.IsDBNull(13) ? null : DocumentFormatExtensions.FromStoredValue(r.GetString(13)),
        LastPromptTokens: r.IsDBNull(14) ? null : r.GetInt32(14),
        LastCallAt: r.IsDBNull(15) ? null : Time(r.GetString(15)));

    private static string? NullIfEmpty(string value) => value.Length == 0 ? null : value;

    private static DateTime Time(string value) =>
        DateTime.Parse(value, null, System.Globalization.DateTimeStyles.RoundtripKind);

    // ---- Messages ----

    public List<Message> ListMessages(long conversationId)
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = """
            SELECT id, conversation_id, role, content, created_at, superseded
            FROM messages WHERE conversation_id = $id ORDER BY created_at ASC, id ASC;
            """;
        cmd.Parameters.AddWithValue("$id", conversationId);
        using var reader = cmd.ExecuteReader();
        var result = new List<Message>();
        while (reader.Read())
        {
            result.Add(new Message(
                reader.GetInt64(0),
                reader.GetInt64(1),
                MessageRoleExtensions.ParseStoredValue(reader.GetString(2)),
                reader.GetString(3),
                Time(reader.GetString(4)),
                reader.GetInt64(5) != 0));
        }
        return result;
    }

    public void MarkSuperseded(IReadOnlyCollection<long> messageIds)
    {
        if (messageIds.Count == 0)
            return;

        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        var parameters = string.Join(",", messageIds.Select((_, i) => "$p" + i));
        cmd.CommandText = $"UPDATE messages SET superseded = 1 WHERE id IN ({parameters});";
        var index = 0;
        foreach (var id in messageIds)
            cmd.Parameters.AddWithValue("$p" + index++, id);
        cmd.ExecuteNonQuery();
    }

    public long AddMessage(long conversationId, MessageRole role, string content)
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = """
            INSERT INTO messages(conversation_id, role, content, created_at)
            VALUES ($convId, $role, $content, $now);
            SELECT last_insert_rowid();
            """;
        cmd.Parameters.AddWithValue("$convId", conversationId);
        cmd.Parameters.AddWithValue("$role", role.ToStoredValue());
        cmd.Parameters.AddWithValue("$content", content);
        cmd.Parameters.AddWithValue("$now", DateTime.UtcNow.ToString("O"));
        return (long)(cmd.ExecuteScalar() ?? 0L);
    }
}
