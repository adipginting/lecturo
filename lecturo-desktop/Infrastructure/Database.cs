using Microsoft.Data.Sqlite;

namespace Lecturo.Data;

/// <summary>
/// Owns the SQLite database file and creates the schema (tracked via
/// <c>PRAGMA user_version</c>). Hand-written SQL, no ORM.
///
/// Pre-release, so the migration history was reset to zero: version 1 is the
/// whole schema at its current shape. Every generated primary key is a UUIDv7
/// string (see <c>Lecturo.Domain.UuidV7</c>) — SQLite cannot generate one, so the
/// repositories mint it on insert. A draft is a conversation with no
/// <c>sent_at</c>; a compaction summary is a message whose role is
/// <c>summary</c>.
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
        if (GetUserVersion(conn) >= 1)
            return;

        ApplyMigration1(conn);
        SetUserVersion(conn, 1);
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

    /// <summary>The whole schema, as one baseline.</summary>
    private static void ApplyMigration1(SqliteConnection conn)
    {
        using var cmd = conn.CreateCommand();
        cmd.CommandText = """
            CREATE TABLE documents(
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                file_name TEXT NOT NULL,
                format TEXT NOT NULL,
                last_locator TEXT,
                updated_at TEXT NOT NULL
            );
            CREATE TABLE prompts(
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                content TEXT NOT NULL
            );
            CREATE TABLE conversations(
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                prompt_id TEXT REFERENCES prompts(id) ON DELETE SET NULL,
                created_at TEXT NOT NULL,
                document_id TEXT REFERENCES documents(id) ON DELETE SET NULL,
                excerpt TEXT NOT NULL DEFAULT '',
                locator TEXT NOT NULL DEFAULT '',
                provider TEXT NOT NULL DEFAULT '',
                updated_at TEXT NOT NULL DEFAULT '',
                context_image_path TEXT NOT NULL DEFAULT '',
                system_prompt TEXT NOT NULL DEFAULT '',
                sent_at TEXT,
                last_prompt_tokens INTEGER,
                last_call_at TEXT
            );
            CREATE TABLE messages(
                id TEXT PRIMARY KEY,
                conversation_id TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                created_at TEXT NOT NULL,
                superseded INTEGER NOT NULL DEFAULT 0
            );
            CREATE TABLE document_sources(
                id TEXT PRIMARY KEY,
                document_id TEXT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
                source TEXT NOT NULL,
                remote_id TEXT NOT NULL,
                url TEXT NOT NULL DEFAULT '',
                title TEXT NOT NULL DEFAULT '',
                created_at TEXT NOT NULL
            );
            CREATE INDEX idx_document_sources_remote ON document_sources(source, remote_id);
            CREATE TABLE skills(
                id TEXT PRIMARY KEY,
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
                id TEXT PRIMARY KEY,
                document_id TEXT REFERENCES documents(id) ON DELETE CASCADE,
                skill_id TEXT NOT NULL,
                skill_name TEXT NOT NULL,
                status TEXT NOT NULL,
                result TEXT NOT NULL DEFAULT '',
                error TEXT NOT NULL DEFAULT '',
                created_at TEXT NOT NULL
            );
            """;
        cmd.ExecuteNonQuery();
    }
}
