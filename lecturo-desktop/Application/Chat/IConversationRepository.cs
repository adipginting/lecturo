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
    Conversation? Get(string id);
    Conversation? FindDraftForDocument(string documentId);
    Conversation? FindActiveConversationForDocument(string documentId);
    string CreateDraft(Excerpt excerpt, string locator, string provider, string? documentId = null, string? promptId = null, string systemPrompt = "");
    string CreateSentConversation(string title, Excerpt excerpt, string locator, string provider, DateTime sentAt, string? documentId = null, string? promptId = null, string systemPrompt = "");
    void MarkSent(string id, string title, DateTime sentAt);
    void RenameConversation(string id, string title);
    void SetConversationProvider(string id, string provider);
    void TouchConversation(string id);
    void DeleteConversation(string id);
    void RecordUsage(string id, int? promptTokens, DateTime at);
    void ClearUsage(string id);
    List<Message> ListMessages(string conversationId);
    void MarkSuperseded(IReadOnlyCollection<string> messageIds);
    string AddMessage(string conversationId, MessageRole role, string content);
}
