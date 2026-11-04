using Lecturo.Domain;

namespace Lecturo.Application.Skills;

/// <summary>The persistence port for installed skills and their runs.</summary>
public interface ISkillRepository
{
    List<Skill> ListInstalled();
    Skill? Get(string skillId);
    void Install(RemoteSkill skill);
    void Uninstall(string skillId);
    void SetEnabled(string skillId, bool enabled);
    string AddRun(string? documentId, Skill skill, RunStatus status);
    void CompleteRun(string runId, RunStatus status, string result, string error);
    List<SkillRun> ListRuns(string? documentId);
}
