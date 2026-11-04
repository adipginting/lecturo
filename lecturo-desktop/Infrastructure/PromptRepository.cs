using Lecturo.Application.Chat;
using Lecturo.Domain;
using Lecturo.Domain.Chat;
using Microsoft.Data.Sqlite;

namespace Lecturo.Data;

/// <summary>Prompt templates, their own aggregate: written once, named, picked per conversation.</summary>
public sealed class PromptRepository : IPromptRepository
{
    private readonly Database _db;

    public PromptRepository(Database db) => _db = db;

    public List<Prompt> List()
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "SELECT id, name, content FROM prompts ORDER BY name ASC;";
        using var reader = cmd.ExecuteReader();
        var result = new List<Prompt>();
        while (reader.Read())
            result.Add(new Prompt(reader.GetString(0), reader.GetString(1), reader.GetString(2)));
        return result;
    }

    public Prompt? Get(string id)
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "SELECT id, name, content FROM prompts WHERE id = $id;";
        cmd.Parameters.AddWithValue("$id", id);
        using var reader = cmd.ExecuteReader();
        return reader.Read()
            ? new Prompt(reader.GetString(0), reader.GetString(1), reader.GetString(2))
            : null;
    }

    public string Add(string name, string content)
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "INSERT INTO prompts(id, name, content) VALUES ($id, $name, $content);";
        var id = UuidV7.New();
        cmd.Parameters.AddWithValue("$id", id);
        cmd.Parameters.AddWithValue("$name", name);
        cmd.Parameters.AddWithValue("$content", content);
        cmd.ExecuteNonQuery();
        return id;
    }

    public void Update(string id, string name, string content)
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "UPDATE prompts SET name = $name, content = $content WHERE id = $id;";
        cmd.Parameters.AddWithValue("$name", name);
        cmd.Parameters.AddWithValue("$content", content);
        cmd.Parameters.AddWithValue("$id", id);
        cmd.ExecuteNonQuery();
    }

    public void Delete(string id)
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "DELETE FROM prompts WHERE id = $id;";
        cmd.Parameters.AddWithValue("$id", id);
        cmd.ExecuteNonQuery();
    }
}
