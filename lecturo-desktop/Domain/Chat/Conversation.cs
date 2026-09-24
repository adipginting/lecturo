namespace Lecturo.Domain.Chat;

/// <summary>
/// The life of a conversation: born a draft, sent only once, named from its first
/// message, never left nameless. Its excerpt is frozen for that life. Persistence
/// and providers adapt to this, not the other way round.
/// </summary>
public sealed record Conversation(
    long Id,
    string Title,
    Excerpt Excerpt,
    string Locator,
    string Provider,
    DateTime CreatedAt,
    DateTime UpdatedAt,
    DateTime? SentAt = null,
    long? PromptId = null,
    long? DocumentId = null,
    string? DocumentTitle = null,
    string SystemPrompt = "",
    DocumentFormat? DocumentFormat = null,
    int? LastPromptTokens = null,
    DateTime? LastCallAt = null)
{
    /// <summary>A conversation whose first message has not been sent.</summary>
    public bool IsDraft => SentAt is null;

    /// <summary>The first message is what sends a draft and names the conversation.</summary>
    public Conversation Send(string firstMessage, DateTime at) =>
        IsDraft
            ? this with
            {
                Title = ConversationTitle.FromMessage(firstMessage) ?? ConversationTitle.Untitled,
                SentAt = at,
                UpdatedAt = at,
            }
            : this;

    /// <summary>A name with nothing in it is refused; the old one stands.</summary>
    public Conversation Rename(string name) =>
        ConversationTitle.Normalize(name) is { } clean ? this with { Title = clean } : this;

    /// <summary>A newly captured excerpt, waiting to be asked about.</summary>
    public static Conversation Draft(
        long id,
        Excerpt excerpt,
        string locator,
        string provider,
        DateTime createdAt,
        long? documentId = null,
        string? documentTitle = null,
        long? promptId = null,
        string systemPrompt = "") =>
        new(
            id,
            ConversationTitle.Untitled,
            excerpt,
            locator,
            provider,
            createdAt,
            createdAt,
            SentAt: null,
            PromptId: promptId,
            DocumentId: documentId,
            DocumentTitle: documentTitle,
            SystemPrompt: systemPrompt);
}
