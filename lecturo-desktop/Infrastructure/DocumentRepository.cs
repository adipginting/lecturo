using Lecturo.Application.Library;
using Lecturo.Domain;
using Microsoft.Data.Sqlite;

namespace Lecturo.Data;

public sealed class DocumentRepository : IDocumentRepository
{
    private readonly Database _db;

    public DocumentRepository(Database db) => _db = db;

    public List<Document> List()
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "SELECT id, title, file_name, format, last_locator, updated_at FROM documents ORDER BY updated_at DESC;";
        using var reader = cmd.ExecuteReader();
        var result = new List<Document>();
        while (reader.Read())
            result.Add(Read(reader));
        return result;
    }

    public Document? Get(long id)
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "SELECT id, title, file_name, format, last_locator, updated_at FROM documents WHERE id = $id;";
        cmd.Parameters.AddWithValue("$id", id);
        using var reader = cmd.ExecuteReader();
        return reader.Read() ? Read(reader) : null;
    }

    public long Add(string title, string fileName, DocumentFormat format)
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = """
            INSERT INTO documents(title, file_name, format, last_locator, updated_at)
            VALUES ($title, $fileName, $format, NULL, $now);
            SELECT last_insert_rowid();
            """;
        cmd.Parameters.AddWithValue("$title", title);
        cmd.Parameters.AddWithValue("$fileName", fileName);
        cmd.Parameters.AddWithValue("$format", format.ToStoredValue());
        cmd.Parameters.AddWithValue("$now", DateTime.UtcNow.ToString("O"));
        return (long)(cmd.ExecuteScalar() ?? 0L);
    }

    public void UpdateLocator(long id, string locator)
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "UPDATE documents SET last_locator = $locator, updated_at = $now WHERE id = $id;";
        cmd.Parameters.AddWithValue("$locator", locator);
        cmd.Parameters.AddWithValue("$now", DateTime.UtcNow.ToString("O"));
        cmd.Parameters.AddWithValue("$id", id);
        cmd.ExecuteNonQuery();
    }

    public void Delete(long id)
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "DELETE FROM documents WHERE id = $id;";
        cmd.Parameters.AddWithValue("$id", id);
        cmd.ExecuteNonQuery();
    }

    private static Document Read(SqliteDataReader r) => new(
        r.GetInt64(0),
        r.GetString(1),
        r.GetString(2),
        DocumentFormatExtensions.ParseStoredValue(r.GetString(3)),
        r.IsDBNull(4) ? null : r.GetString(4),
        DateTime.Parse(r.GetString(5), null, System.Globalization.DateTimeStyles.RoundtripKind));
}
