using Lecturo.Application.Skills;
using Lecturo.Domain;

namespace Lecturo.Tests;

internal sealed class FakeSkillRepository : ISkillRepository
{
    public List<Skill> Installed { get; } = new();
    public List<SkillRun> Runs { get; } = new();

    public List<Skill> ListInstalled() => Installed.ToList();

    public Skill? Get(string skillId) => Installed.FirstOrDefault(s => s.SkillId == skillId);

    public void Install(RemoteSkill skill) => Installed.Add(new Skill(
        "0", skill.SkillId, skill.Name, skill.Description, skill.Author, skill.Version,
        skill.Scope, skill.Trigger, skill.Prompt, skill.Price, true));

    public void Uninstall(string skillId) => Installed.RemoveAll(s => s.SkillId == skillId);

    public void SetEnabled(string skillId, bool enabled)
    {
        var index = Installed.FindIndex(s => s.SkillId == skillId);
        if (index >= 0)
            Installed[index] = Installed[index] with { Enabled = enabled };
    }

    public string AddRun(string? documentId, Skill skill, RunStatus status)
    {
        Runs.Add(new SkillRun((Runs.Count + 1).ToString(), documentId, skill.SkillId, skill.Name, status, "", "", DateTime.UtcNow));
        return Runs.Count.ToString();
    }

    public void CompleteRun(string runId, RunStatus status, string result, string error)
    {
        var index = Runs.FindIndex(r => r.Id == runId);
        if (index >= 0)
            Runs[index] = Runs[index] with { Status = status, Result = result, Error = error };
    }

    public List<SkillRun> ListRuns(string? documentId) =>
        Runs.Where(r => documentId is null || r.DocumentId == documentId).ToList();
}

internal sealed class FakeSkillCatalog : ISkillCatalog
{
    private readonly IReadOnlyList<RemoteSkill> _skills;

    public FakeSkillCatalog(params RemoteSkill[] skills) => _skills = skills;

    public Task<IReadOnlyList<RemoteSkill>> FetchAsync(string marketplaceUrl, CancellationToken ct = default) =>
        Task.FromResult(_skills);
}
