namespace Lecturo.Domain;

/// <summary>One entry in the skills marketplace (S38).</summary>
public sealed record RemoteSkill(
    string SkillId,
    string Name,
    string Description,
    string Author,
    string Version,
    string Scope,
    string Trigger,
    string Prompt,
    string Price)
{
    public bool IsPaid => Price.Length > 0 && Price != "free";

    public string Meta => string.Join(" · ", new[] { Author, Version }.Where(p => p.Length > 0));
}

/// <summary>An installed skill (S39).</summary>
public sealed record Skill(
    string Id,
    string SkillId,
    string Name,
    string Description,
    string Author,
    string Version,
    string Scope,
    string Trigger,
    string Prompt,
    string Price,
    bool Enabled)
{
    public bool IsPaid => Price.Length > 0 && Price != "free";
}

/// <summary>One background run of a skill against a document (S41/S42).</summary>
public sealed record SkillRun(
    string Id,
    string? DocumentId,
    string SkillId,
    string SkillName,
    RunStatus Status,
    string Result,
    string Error,
    DateTime CreatedAt)
{
    public string StatusLabel => Status switch
    {
        RunStatus.Running => "Running",
        RunStatus.Done => "Done",
        RunStatus.Failed => "Failed",
        _ => Status.ToString(),
    };

    public string WhenLabel => CreatedAt.ToLocalTime().ToString("M/d/yy, h:mm tt");
}

/// <summary>How a skill run ended — or that it has not ended yet.</summary>
public enum RunStatus
{
    Running,
    Done,

    /// <summary>
    /// Stored as "failed"; rows written before this spelling existed hold "error",
    /// which the repository still reads as <see cref="Failed"/>.
    /// </summary>
    Failed,
}

public static class RunStatusExtensions
{
    public static string ToStoredValue(this RunStatus status) => status switch
    {
        RunStatus.Running => "running",
        RunStatus.Done => "done",
        RunStatus.Failed => "failed",
        _ => throw new ArgumentOutOfRangeException(nameof(status), status, "Unknown run status."),
    };

    /// <summary>Reads a stored status, accepting the legacy "error" spelling for failures.</summary>
    public static RunStatus? FromStoredValue(string? value) => value?.ToLowerInvariant() switch
    {
        "running" => RunStatus.Running,
        "done" => RunStatus.Done,
        "failed" or "error" => RunStatus.Failed,
        _ => null,
    };
}
