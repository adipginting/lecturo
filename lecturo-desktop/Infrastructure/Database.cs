using Microsoft.Data.Sqlite;

namespace Lecturo.Data;

/// <summary>
/// Owns the SQLite database file and applies schema migrations
/// (tracked via PRAGMA user_version). Hand-written SQL, no ORM.
/// </summary>
public sealed class Database
{
    private readonly string _connectionString;

    public Database(string path)
    {
        _connectionString = new SqliteConnectionStringBuilder
        {
            DataSource = path,
            Mode = SqliteOpenMode.ReadWriteCreate,
        }.ToString();
        Migrate();
    }

    public SqliteConnection Open()
    {
        var conn = new SqliteConnection(_connectionString);
        conn.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "PRAGMA foreign_keys = ON; PRAGMA journal_mode = WAL;";
        cmd.ExecuteNonQuery();
        return conn;
    }

    private void Migrate()
    {
        using var conn = Open();
        var version = GetUserVersion(conn);
        if (version < 1)
        {
            ApplyMigration1(conn);
            SetUserVersion(conn, 1);
            version = 1;
        }
        if (version < 2)
        {
            ApplyMigration2(conn);
            SetUserVersion(conn, 2);
            version = 2;
        }
        if (version < 3)
        {
            ApplyMigration3(conn);
            SetUserVersion(conn, 3);
            version = 3;
        }
        if (version < 4)
        {
            ApplyMigration4(conn);
            SetUserVersion(conn, 4);
            version = 4;
        }
        if (version < 5)
        {
            ApplyMigration5(conn);
            SetUserVersion(conn, 5);
            version = 5;
        }
        if (version < 6)
        {
            ApplyMigration6(conn);
            SetUserVersion(conn, 6);
            version = 6;
        }
        if (version < 7)
        {
            ApplyMigration7(conn);
            SetUserVersion(conn, 7);
            version = 7;
        }
        if (version < 8)
        {
            ApplyMigration8(conn);
            SetUserVersion(conn, 8);
        }
    }

    private static long GetUserVersion(SqliteConnection conn)
    {
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "PRAGMA user_version;";
        return (long)(cmd.ExecuteScalar() ?? 0L);
    }

    private static void SetUserVersion(SqliteConnection conn, long version)
    {
        using var cmd = conn.CreateCommand();
        cmd.CommandText = $"PRAGMA user_version = {version};";
        cmd.ExecuteNonQuery();
    }

    private static void ApplyMigration1(SqliteConnection conn)
    {
        using var cmd = conn.CreateCommand();
        cmd.CommandText = """
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
                created_at TEXT NOT NULL
            );
            CREATE TABLE messages(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                conversation_id INTEGER NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                created_at TEXT NOT NULL
            );
            """;
        cmd.ExecuteNonQuery();
    }

    /// <summary>
    /// Conversations become per-excerpt (Lecturo semantics): they carry the frozen
    /// context, its source document/locator, the provider used, and an updated_at.
    /// </summary>
    private static void ApplyMigration2(SqliteConnection conn)
    {
        using var cmd = conn.CreateCommand();
        cmd.CommandText = """
            ALTER TABLE conversations ADD COLUMN document_id INTEGER REFERENCES documents(id) ON DELETE SET NULL;
            ALTER TABLE conversations ADD COLUMN excerpt TEXT NOT NULL DEFAULT '';
            ALTER TABLE conversations ADD COLUMN locator TEXT NOT NULL DEFAULT '';
            ALTER TABLE conversations ADD COLUMN provider TEXT NOT NULL DEFAULT '';
            ALTER TABLE conversations ADD COLUMN updated_at TEXT NOT NULL DEFAULT '';
            """;
        cmd.ExecuteNonQuery();
    }

    /// <summary>
    /// Provenance for downloads: which remote item a local document came from,
    /// so the remote list can mark it as downloaded (F2/S11).
    /// </summary>
    private static void ApplyMigration3(SqliteConnection conn)
    {
        using var cmd = conn.CreateCommand();
        cmd.CommandText = """
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
            """;
        cmd.ExecuteNonQuery();
    }

    /// <summary>Compaction: messages folded into a summary are marked superseded.</summary>
    private static void ApplyMigration4(SqliteConnection conn)
    {
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "ALTER TABLE messages ADD COLUMN superseded INTEGER NOT NULL DEFAULT 0;";
        cmd.ExecuteNonQuery();
    }

    /// <summary>A conversation can be seeded by a screenshot instead of an excerpt.</summary>
    private static void ApplyMigration5(SqliteConnection conn)
    {
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "ALTER TABLE conversations ADD COLUMN context_image_path TEXT NOT NULL DEFAULT '';";
        cmd.ExecuteNonQuery();
    }

    /// <summary>Skills (book-level) and their runs; conversations may carry a system prompt.</summary>
    private static void ApplyMigration6(SqliteConnection conn)
    {
        using var cmd = conn.CreateCommand();
        cmd.CommandText = """
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
            ALTER TABLE conversations ADD COLUMN system_prompt TEXT NOT NULL DEFAULT '';
            """;
        cmd.ExecuteNonQuery();
    }

    /// <summary>
    /// Drafts (Lecturo semantics): a draft is a conversation whose first message
    /// has not been sent, so the saved queue has no reason to exist — its rows
    /// become drafts and its table goes. Every conversation that already exists
    /// was sent (under the old model a row survived only if a reply landed), so
    /// each takes its last update as the moment it was sent.
    /// The prompt table loses the "saved" prefix its concept no longer uses.
    /// </summary>
    private static void ApplyMigration7(SqliteConnection conn)
    {
        using var cmd = conn.CreateCommand();
        cmd.CommandText = """
            ALTER TABLE conversations ADD COLUMN sent_at TEXT;
            UPDATE conversations
               SET sent_at = CASE WHEN updated_at = '' THEN created_at ELSE updated_at END;

            INSERT INTO conversations(title, saved_prompt_id, document_id, excerpt, locator, provider,
                                      context_image_path, system_prompt, created_at, updated_at, sent_at)
            SELECT 'New conversation', NULL, b.document_id, b.text, b.locator, '',
                   '', '', b.created_at, b.created_at, NULL
              FROM basket_items b;
            DROP TABLE basket_items;

            ALTER TABLE saved_prompts RENAME TO prompts;
            ALTER TABLE conversations RENAME COLUMN saved_prompt_id TO prompt_id;
            """;
        cmd.ExecuteNonQuery();
    }

    /// <summary>
    /// What the last reply cost. A provider reports the prompt tokens it counted,
    /// so the context counter can start from that truth and add only what has
    /// arrived since, instead of estimating the whole payload every time.
    /// </summary>
    private static void ApplyMigration8(SqliteConnection conn)
    {
        using var cmd = conn.CreateCommand();
        cmd.CommandText = """
            ALTER TABLE conversations ADD COLUMN last_prompt_tokens INTEGER;
            ALTER TABLE conversations ADD COLUMN last_call_at TEXT;
            """;
        cmd.ExecuteNonQuery();
    }
}
