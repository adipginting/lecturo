using Lecturo.Application.Catalog;
using Lecturo.Domain;
using Microsoft.Data.Sqlite;

namespace Lecturo.Data;

/// <summary>Which remote item each downloaded document came from (provenance).</summary>
public sealed class DocumentSourceRepository : IProvenanceRepository
{
    private readonly Database _db;

    public DocumentSourceRepository(Database db) => _db = db;

    public static string Key(string source, string remoteId) => Provenance.Key(source, remoteId);

    public string Add(string documentId, string source, string remoteId, string url, string title)
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = """
            INSERT INTO document_sources(id, document_id, source, remote_id, url, title, created_at)
            VALUES ($id, $docId, $source, $remoteId, $url, $title, $now);
            """;
        var id = UuidV7.New();
        cmd.Parameters.AddWithValue("$id", id);
        cmd.Parameters.AddWithValue("$docId", documentId);
        cmd.Parameters.AddWithValue("$source", source);
        cmd.Parameters.AddWithValue("$remoteId", remoteId);
        cmd.Parameters.AddWithValue("$url", url);
        cmd.Parameters.AddWithValue("$title", title);
        cmd.Parameters.AddWithValue("$now", DateTime.UtcNow.ToString("O"));
        cmd.ExecuteNonQuery();
        return id;
    }

    /// <summary>Every linked remote item, as "source\u001fremoteId".</summary>
    public HashSet<string> DownloadedKeys()
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "SELECT source, remote_id FROM document_sources;";
        using var reader = cmd.ExecuteReader();
        var result = new HashSet<string>();
        while (reader.Read())
            result.Add(Key(reader.GetString(0), reader.GetString(1)));
        return result;
    }

    /// <summary>The local document a remote item was downloaded into, if any.</summary>
    public string? FindDocumentId(string source, string remoteId)
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = """
            SELECT document_id FROM document_sources
            WHERE source = $source AND remote_id = $remoteId
            ORDER BY id DESC LIMIT 1;
            """;
        cmd.Parameters.AddWithValue("$source", source);
        cmd.Parameters.AddWithValue("$remoteId", remoteId);
        var result = cmd.ExecuteScalar();
        return result is null or DBNull ? null : (string)result;
    }
}
