using System.Text;
using Lecturo.Domain.Chat;

namespace Lecturo.Application.Chat;

/// <summary>What one send did, for the caller to render. <see cref="Error"/> is set instead of throwing.</summary>
public sealed record SendResult(
    Message User,
    Message? Assistant,
    bool BecameSent,
    string Title,
    int? PromptTokens,
    DateTime AnsweredAt,
    string? Error);

/// <summary>What one compaction did, for the caller to render. <see cref="Error"/> is set instead of throwing.</summary>
public sealed record CompactResult(
    Message? Summary,
    IReadOnlyList<string> SupersededIds,
    string? Error)
{
    public bool NothingToCompact => Summary is null && Error is null;
}

/// <summary>
/// The chat use cases. This is where the life of a conversation is decided —
/// born a draft, sent once by its first reply, named from its first message,
/// compacted when it outgrows the model — and the rules are the domain's
/// (<see cref="Conversation"/>, <see cref="ConversationTitle"/>). The view model
/// renders results; the repositories persist; the provider client answers.
/// </summary>
public sealed class ConversationService
{
    /// <summary>A failed compaction must not read as "nothing to compact".</summary>
    private const string SummarizeInstruction =
        "Summarize the conversation above into a compact summary that preserves the facts, decisions and the user's intent. Reply with the summary only.";

    /// <summary>The fewest active turns worth folding into a summary.</summary>
    public const int MinimumTurnsToCompact = 4;

    private readonly IConversationRepository _conversations;
    private readonly IPromptRepository _prompts;
    private readonly IChatClient _chat;

    public ConversationService(
        IConversationRepository conversations,
        IPromptRepository prompts,
        IChatClient chat)
    {
        _conversations = conversations;
        _prompts = prompts;
        _chat = chat;
    }

    // ---- Queries ----

    public IReadOnlyList<Conversation> ListDrafts() => _conversations.ListDrafts();

    public IReadOnlyList<Conversation> ListConversations() => _conversations.ListConversations();

    public Conversation? Get(string id) => _conversations.Get(id);

    public Conversation? DraftForDocument(string documentId) => _conversations.FindDraftForDocument(documentId);

    public Conversation? ActiveForDocument(string documentId) => _conversations.FindActiveConversationForDocument(documentId);

    public IReadOnlyList<Message> Messages(string conversationId) => _conversations.ListMessages(conversationId);

    // ---- The life of a conversation ----

    /// <summary>Records a freshly captured excerpt as a draft, and nothing more.</summary>
    public string CreateDraft(
        Excerpt excerpt,
        string locator,
        string provider,
        string? documentId = null,
        string? promptId = null,
        string systemPrompt = "") =>
        _conversations.CreateDraft(excerpt, locator, provider, documentId, promptId, systemPrompt);

    /// <summary>Records a conversation that arrives already answered — a skill run.</summary>
    public string CreateSentConversation(
        string title,
        Excerpt excerpt,
        string locator,
        string provider,
        DateTime sentAt,
        string? documentId = null,
        string? promptId = null,
        string systemPrompt = "") =>
        _conversations.CreateSentConversation(title, excerpt, locator, provider, sentAt, documentId, promptId, systemPrompt);

    public void Delete(string id) => _conversations.DeleteConversation(id);

    /// <summary>A name with nothing in it is refused; <c>null</c> says so.</summary>
    public Conversation? Rename(string id, string name)
    {
        if (ConversationTitle.Normalize(name) is not { } clean)
            return null;
        _conversations.RenameConversation(id, clean);
        return _conversations.Get(id);
    }

    public void SwitchProvider(string id, string provider) =>
        _conversations.SetConversationProvider(id, provider);

    /// <summary>Adds a turn directly — for a run that has already been answered.</summary>
    public Message AppendMessage(string conversationId, MessageRole role, string content)
    {
        var id = _conversations.AddMessage(conversationId, role, content);
        return new Message(id, conversationId, role, content, DateTime.UtcNow);
    }

    /// <summary>Marks a conversation as engaged with, so it sorts as the current one.</summary>
    public void Touch(string id) => _conversations.TouchConversation(id);

    /// <summary>
    /// Sends a message. The reply is what sends a draft, names it and moves it
    /// out of Drafts; a failed send leaves the user's message in place and the
    /// draft unsent, so retrying sends it rather than rolling anything back.
    /// </summary>
    public async Task<SendResult> SendAsync(
        string conversationId,
        string providerId,
        string text,
        CancellationToken ct = default)
    {
        text = text.Trim();
        if (_conversations.Get(conversationId) is not { } conversation)
            throw new InvalidOperationException("The conversation no longer exists.");
        if (text.Length == 0)
            throw new InvalidOperationException("The message is empty.");

        var askedAt = DateTime.UtcNow;
        var user = Persist(conversationId, MessageRole.User, text, askedAt);

        try
        {
            var reply = await _chat.CompleteAsync(
                providerId,
                BuildSystemPrompt(conversation),
                ActiveHistory(conversationId),
                conversation.Excerpt.ImagePath,
                ct);

            var answeredAt = DateTime.UtcNow;
            var assistant = Persist(conversationId, MessageRole.Assistant, reply.Text, answeredAt);
            _conversations.RecordUsage(conversationId, reply.PromptTokens, answeredAt);

            if (conversation.IsDraft)
            {
                var title = ConversationTitle.FromMessage(text) ?? ConversationTitle.Untitled;
                _conversations.MarkSent(conversationId, title, answeredAt);
                return new SendResult(user, assistant, true, title, reply.PromptTokens, answeredAt, null);
            }

            _conversations.TouchConversation(conversationId);
            return new SendResult(user, assistant, false, conversation.Title, reply.PromptTokens, answeredAt, null);
        }
        catch (Exception ex)
        {
            // The draft is still a draft: retry sends it, it never became a conversation.
            return new SendResult(user, null, false, conversation.Title, null, askedAt, ex.Message);
        }
    }

    /// <summary>
    /// Folds the active turns into one summary. A failed compaction is swallowed
    /// into <see cref="CompactResult.Error"/> and must not cost the reader a turn.
    /// </summary>
    public async Task<CompactResult> CompactAsync(
        string conversationId,
        string providerId,
        CancellationToken ct = default)
    {
        var active = ActiveConversationTurns(conversationId);
        if (active.Count < MinimumTurnsToCompact)
            return new CompactResult(null, Array.Empty<string>(), null);

        try
        {
            var reply = await _chat.CompleteAsync(providerId, SummarizeInstruction, active, null, ct);
            var summary = Persist(conversationId, MessageRole.Summary, reply.Text, DateTime.UtcNow);
            _conversations.MarkSuperseded(active.Select(m => m.Id).ToList());
            // The summary replaces the history those counts described.
            _conversations.ClearUsage(conversationId);
            _conversations.TouchConversation(conversationId);
            return new CompactResult(summary, active.Select(m => m.Id).ToList(), null);
        }
        catch (Exception ex)
        {
            return new CompactResult(null, Array.Empty<string>(), ex.Message);
        }
    }

    // ---- Context ----

    /// <summary>The standing instruction plus the frozen excerpt, for the next call.</summary>
    public string BuildSystemPrompt(Conversation conversation)
    {
        var builder = new StringBuilder();

        if (conversation.PromptId is { } promptId &&
            _prompts.Get(promptId) is { } prompt &&
            !string.IsNullOrWhiteSpace(prompt.Content))
        {
            builder.AppendLine(prompt.Content.Trim());
            builder.AppendLine();
        }

        if (conversation.SystemPrompt.Length > 0)
        {
            builder.AppendLine(conversation.SystemPrompt.Trim());
            builder.AppendLine();
        }

        if (conversation.Excerpt.HasImage)
        {
            builder.AppendLine("Answer the reader's questions about the screenshot attached to their first message. Stay close to what the image shows.");
        }
        else
        {
            builder.AppendLine("Answer the reader's questions about the following excerpt. Stay close to it.");
            builder.AppendLine();
            builder.AppendLine(conversation.Excerpt.TextOrEmpty);
        }

        return builder.ToString();
    }

    /// <summary>What the next call sends: the summary plus every non-superseded turn.</summary>
    public IReadOnlyList<Message> ActiveHistory(string conversationId) =>
        _conversations.ListMessages(conversationId).Where(m => m.IsActive).ToList();

    private IReadOnlyList<Message> ActiveConversationTurns(string conversationId) =>
        _conversations.ListMessages(conversationId)
            .Where(m => m.IsActive && m.Role != MessageRole.Summary)
            .ToList();

    private Message Persist(string conversationId, MessageRole role, string content, DateTime at)
    {
        var id = _conversations.AddMessage(conversationId, role, content);
        return new Message(id, conversationId, role, content, at);
    }
}
