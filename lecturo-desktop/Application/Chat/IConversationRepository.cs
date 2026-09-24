using Lecturo.Domain.Chat;

namespace Lecturo.Application.Chat;

/// <summary>
/// The persistence port for the conversation aggregate. A draft is a
/// conversation with no <c>sent_at</c>, so both states live behind these
/// methods and are told apart by <see cref="Conversation.IsDraft"/>.
/// Implemented by the SQLite adapter in infrastructure.
/// </summary>
public interface IConversationRepository
{
    List<Conversation> ListDrafts();
    List<Conversation> ListConversations();
    Conversation? Get(long id);
    Conversation? FindDraftForDocument(long documentId);
    Conversation? FindActiveConversationForDocument(long documentId);
    long CreateDraft(Excerpt excerpt, string locator, string provider, long? documentId = null, long? promptId = null, string systemPrompt = "");
    long CreateSentConversation(string title, Excerpt excerpt, string locator, string provider, DateTime sentAt, long? documentId = null, long? promptId = null, string systemPrompt = "");
    void MarkSent(long id, string title, DateTime sentAt);
    void RenameConversation(long id, string title);
    void SetConversationProvider(long id, string provider);
    void TouchConversation(long id);
    void DeleteConversation(long id);
    void RecordUsage(long id, int? promptTokens, DateTime at);
    void ClearUsage(long id);
    List<Message> ListMessages(long conversationId);
    void MarkSuperseded(IReadOnlyCollection<long> messageIds);
    long AddMessage(long conversationId, MessageRole role, string content);
}
