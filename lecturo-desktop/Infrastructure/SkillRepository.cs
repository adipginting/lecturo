using Lecturo.Application.Skills;
using Lecturo.Domain;
using Microsoft.Data.Sqlite;

namespace Lecturo.Data;

public sealed class SkillRepository : ISkillRepository
{
    private readonly Database _db;

    public SkillRepository(Database db) => _db = db;

    public List<Skill> ListInstalled()
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = """
            SELECT id, skill_id, name, description, author, version, scope, trigger, prompt, price, enabled
            FROM skills ORDER BY name COLLATE NOCASE ASC;
            """;
        using var reader = cmd.ExecuteReader();
        var result = new List<Skill>();
        while (reader.Read())
            result.Add(ReadSkill(reader));
        return result;
    }

    public Skill? Get(string skillId)
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = """
            SELECT id, skill_id, name, description, author, version, scope, trigger, prompt, price, enabled
            FROM skills WHERE skill_id = $id;
            """;
        cmd.Parameters.AddWithValue("$id", skillId);
        using var reader = cmd.ExecuteReader();
        return reader.Read() ? ReadSkill(reader) : null;
    }

    public void Install(RemoteSkill skill)
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = """
            INSERT INTO skills(id, skill_id, name, description, author, version, scope, trigger, prompt, price, enabled, installed_at)
            VALUES ($rowId, $skillId, $name, $desc, $author, $version, $scope, $trigger, $prompt, $price, 1, $now)
            ON CONFLICT(skill_id) DO UPDATE SET
                name = excluded.name, description = excluded.description, author = excluded.author,
                version = excluded.version, scope = excluded.scope, trigger = excluded.trigger,
                prompt = excluded.prompt, price = excluded.price;
            """;
        cmd.Parameters.AddWithValue("$rowId", UuidV7.New());
        cmd.Parameters.AddWithValue("$skillId", skill.SkillId);
        cmd.Parameters.AddWithValue("$name", skill.Name);
        cmd.Parameters.AddWithValue("$desc", skill.Description);
        cmd.Parameters.AddWithValue("$author", skill.Author);
        cmd.Parameters.AddWithValue("$version", skill.Version);
        cmd.Parameters.AddWithValue("$scope", skill.Scope);
        cmd.Parameters.AddWithValue("$trigger", skill.Trigger);
        cmd.Parameters.AddWithValue("$prompt", skill.Prompt);
        cmd.Parameters.AddWithValue("$price", skill.Price);
        cmd.Parameters.AddWithValue("$now", DateTime.UtcNow.ToString("O"));
        cmd.ExecuteNonQuery();
    }

    public void Uninstall(string skillId)
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "DELETE FROM skills WHERE skill_id = $id;";
        cmd.Parameters.AddWithValue("$id", skillId);
        cmd.ExecuteNonQuery();
    }

    public void SetEnabled(string skillId, bool enabled)
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "UPDATE skills SET enabled = $enabled WHERE skill_id = $id;";
        cmd.Parameters.AddWithValue("$enabled", enabled ? 1 : 0);
        cmd.Parameters.AddWithValue("$id", skillId);
        cmd.ExecuteNonQuery();
    }

    public string AddRun(string? documentId, Skill skill, RunStatus status)
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = """
            INSERT INTO skill_runs(id, document_id, skill_id, skill_name, status, result, error, created_at)
            VALUES ($id, $docId, $skillId, $name, $status, '', '', $now);
            """;
        var id = UuidV7.New();
        cmd.Parameters.AddWithValue("$id", id);
        cmd.Parameters.AddWithValue("$docId", (object?)documentId ?? DBNull.Value);
        cmd.Parameters.AddWithValue("$skillId", skill.SkillId);
        cmd.Parameters.AddWithValue("$name", skill.Name);
        cmd.Parameters.AddWithValue("$status", status.ToStoredValue());
        cmd.Parameters.AddWithValue("$now", DateTime.UtcNow.ToString("O"));
        cmd.ExecuteNonQuery();
        return id;
    }

    public void CompleteRun(string runId, RunStatus status, string result, string error)
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = "UPDATE skill_runs SET status = $status, result = $result, error = $error WHERE id = $id;";
        cmd.Parameters.AddWithValue("$status", status.ToStoredValue());
        cmd.Parameters.AddWithValue("$result", result);
        cmd.Parameters.AddWithValue("$error", error);
        cmd.Parameters.AddWithValue("$id", runId);
        cmd.ExecuteNonQuery();
    }

    public List<SkillRun> ListRuns(string? documentId)
    {
        using var conn = _db.Open();
        using var cmd = conn.CreateCommand();
        cmd.CommandText = documentId is null
            ? "SELECT id, document_id, skill_id, skill_name, status, result, error, created_at FROM skill_runs ORDER BY created_at DESC LIMIT 50;"
            : "SELECT id, document_id, skill_id, skill_name, status, result, error, created_at FROM skill_runs WHERE document_id = $docId ORDER BY created_at DESC LIMIT 50;";
        if (documentId is not null)
            cmd.Parameters.AddWithValue("$docId", documentId);
        using var reader = cmd.ExecuteReader();
        var result = new List<SkillRun>();
        while (reader.Read())
        {
            result.Add(new SkillRun(
                reader.GetString(0),
                reader.IsDBNull(1) ? null : reader.GetString(1),
                reader.GetString(2),
                reader.GetString(3),
                RunStatusExtensions.FromStoredValue(reader.GetString(4)) ?? RunStatus.Failed,
                reader.GetString(5),
                reader.GetString(6),
                DateTime.Parse(reader.GetString(7), null, System.Globalization.DateTimeStyles.RoundtripKind)));
        }
        return result;
    }

    private static Skill ReadSkill(SqliteDataReader r) => new(
        r.GetString(0),
        r.GetString(1),
        r.GetString(2),
        r.GetString(3),
        r.GetString(4),
        r.GetString(5),
        r.GetString(6),
        r.GetString(7),
        r.GetString(8),
        r.GetString(9),
        r.GetInt64(10) != 0);
}
