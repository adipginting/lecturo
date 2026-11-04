using Lecturo.Application.Chat;
using Lecturo.Domain;
using Lecturo.Domain.Chat;

namespace Lecturo.Application.Skills;

/// <summary>
/// Book-level skills (S38–S43): browse and install from a marketplace, and run
/// one skill against one book. A finished run becomes a conversation. The
/// marketplace, the store and the chat are ports.
/// </summary>
public sealed class SkillService
{
    private readonly ISkillRepository _skills;
    private readonly ISkillCatalog _catalog;
    private readonly ConversationService _conversations;
    private readonly IChatClient _chat;

    public SkillService(
        ISkillRepository skills,
        ISkillCatalog catalog,
        ConversationService conversations,
        IChatClient chat)
    {
        _skills = skills;
        _catalog = catalog;
        _conversations = conversations;
        _chat = chat;
    }

    public IReadOnlyList<Skill> ListInstalled() => _skills.ListInstalled();

    public IReadOnlyList<SkillRun> ListRuns(string? documentId) => _skills.ListRuns(documentId);

    public void Install(RemoteSkill skill) => _skills.Install(skill);

    public void Uninstall(string skillId) => _skills.Uninstall(skillId);

    public void SetEnabled(string skillId, bool enabled) => _skills.SetEnabled(skillId, enabled);

    public Task<IReadOnlyList<RemoteSkill>> BrowseAsync(string marketplaceUrl, CancellationToken ct = default) =>
        _catalog.FetchAsync(marketplaceUrl, ct);

    /// <summary>
    /// Runs a skill against a document. Returns the id of the conversation the run
    /// produced; the run is recorded either way (S41/S42).
    /// </summary>
    public async Task<string> RunAsync(
        Document document,
        Skill skill,
        string contextText,
        string providerId,
        CancellationToken ct = default)
    {
        if (skill.IsPaid)
            throw new InvalidOperationException($"\u201c{skill.Name}\u201d is a paid skill; it is not runnable yet.");

        var runId = _skills.AddRun(document.Id, skill, RunStatus.Running);
        try
        {
            var title = $"{skill.Name} · {document.Title}";
            string conversationId;
            string result;
            try
            {
                var excerpt = contextText.Length <= 4000 ? contextText : contextText[..4000];
                // A run does its own asking, so its result is not a draft. It is about
                // the document rather than a position in it, hence no locator.
                conversationId = _conversations.CreateSentConversation(
                    title,
                    Excerpt.FromText(excerpt),
                    "",
                    providerId,
                    DateTime.UtcNow,
                    documentId: document.Id,
                    systemPrompt: skill.Prompt);

                var history = new[]
                {
                    new Message(UuidV7.New(), conversationId, MessageRole.User, contextText, DateTime.UtcNow),
                };
                result = (await _chat.CompleteAsync(providerId, skill.Prompt, history, null, ct)).Text;
            }
            catch (Exception ex)
            {
                _skills.CompleteRun(runId, RunStatus.Failed, "", ex.Message);
                throw;
            }

            _conversations.AppendMessage(conversationId, MessageRole.Assistant, result);
            _conversations.Touch(conversationId);
            _skills.CompleteRun(runId, RunStatus.Done, result, "");
            return conversationId;
        }
        catch
        {
            throw;
        }
    }
}
