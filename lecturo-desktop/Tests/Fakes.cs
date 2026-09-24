using Lecturo.Application.Chat;
using Lecturo.Domain;
using Lecturo.Domain.Chat;

namespace Lecturo.Tests;

/// <summary>An in-memory <see cref="IConversationRepository"/>: no SQLite, just the aggregate's rules.</summary>
internal sealed class FakeConversationRepository : IConversationRepository
{
    private readonly Dictionary<long, Conversation> _conversations = new();
    private readonly Dictionary<long, List<Message>> _messages = new();
    private long _nextConversation = 1;
    private long _nextMessage = 1;

    public List<Conversation> ListDrafts() =>
        _conversations.Values.Where(c => c.IsDraft).OrderByDescending(c => c.CreatedAt).ToList();

    public List<Conversation> ListConversations() =>
        _conversations.Values.Where(c => !c.IsDraft).OrderByDescending(c => c.UpdatedAt).ToList();

    public Conversation? Get(long id) => _conversations.GetValueOrDefault(id);

    public Conversation? FindDraftForDocument(long documentId) =>
        _conversations.Values.Where(c => c.DocumentId == documentId && c.IsDraft)
            .OrderByDescending(c => c.CreatedAt).FirstOrDefault();

    public Conversation? FindActiveConversationForDocument(long documentId) =>
        _conversations.Values.Where(c => c.DocumentId == documentId && !c.IsDraft)
            .OrderByDescending(c => c.UpdatedAt).FirstOrDefault();

    public long CreateDraft(Excerpt excerpt, string locator, string provider, long? documentId = null, long? promptId = null, string systemPrompt = "")
    {
        var id = _nextConversation++;
        _conversations[id] = Conversation.Draft(id, excerpt, locator, provider, DateTime.UtcNow, documentId, null, promptId, systemPrompt);
        _messages[id] = new List<Message>();
        return id;
    }

    public long CreateSentConversation(string title, Excerpt excerpt, string locator, string provider, DateTime sentAt, long? documentId = null, long? promptId = null, string systemPrompt = "")
    {
        var id = _nextConversation++;
        _conversations[id] = new Conversation(id, title, excerpt, locator, provider, sentAt, sentAt, sentAt, promptId, documentId, null, systemPrompt);
        _messages[id] = new List<Message>();
        return id;
    }

    public void MarkSent(long id, string title, DateTime sentAt)
    {
        if (_conversations.TryGetValue(id, out var c))
            _conversations[id] = c with { Title = title, SentAt = sentAt, UpdatedAt = sentAt };
    }

    public void RenameConversation(long id, string title)
    {
        if (_conversations.TryGetValue(id, out var c))
            _conversations[id] = c with { Title = title };
    }

    public void SetConversationProvider(long id, string provider)
    {
        if (_conversations.TryGetValue(id, out var c))
            _conversations[id] = c with { Provider = provider };
    }

    public void TouchConversation(long id)
    {
        if (_conversations.TryGetValue(id, out var c))
            _conversations[id] = c with { UpdatedAt = DateTime.UtcNow };
    }

    public void DeleteConversation(long id)
    {
        _conversations.Remove(id);
        _messages.Remove(id);
    }

    public void RecordUsage(long id, int? promptTokens, DateTime at)
    {
        if (_conversations.TryGetValue(id, out var c))
            _conversations[id] = c with { LastPromptTokens = promptTokens, LastCallAt = at };
    }

    public void ClearUsage(long id)
    {
        if (_conversations.TryGetValue(id, out var c))
            _conversations[id] = c with { LastPromptTokens = null, LastCallAt = null };
    }

    public List<Message> ListMessages(long conversationId) =>
        _messages.TryGetValue(conversationId, out var list) ? list.ToList() : new List<Message>();

    public void MarkSuperseded(IReadOnlyCollection<long> messageIds)
    {
        foreach (var list in _messages.Values)
            for (var i = 0; i < list.Count; i++)
                if (messageIds.Contains(list[i].Id))
                    list[i] = list[i] with { Superseded = true };
    }

    public long AddMessage(long conversationId, MessageRole role, string content)
    {
        var id = _nextMessage++;
        if (!_messages.TryGetValue(conversationId, out var list))
            _messages[conversationId] = list = new List<Message>();
        list.Add(new Message(id, conversationId, role, content, DateTime.UtcNow));
        return id;
    }
}

/// <summary>In-memory prompt templates, with the twelve cap left to the caller.</summary>
internal sealed class FakePromptRepository : IPromptRepository
{
    private readonly List<Prompt> _prompts = new();
    private long _next = 1;

    public List<Prompt> List() => _prompts.OrderBy(p => p.Name).ToList();

    public Prompt? Get(long id) => _prompts.FirstOrDefault(p => p.Id == id);

    public long Add(string name, string content)
    {
        var id = _next++;
        _prompts.Add(new Prompt(id, name, content));
        return id;
    }

    public void Update(long id, string name, string content)
    {
        var index = _prompts.FindIndex(p => p.Id == id);
        if (index >= 0)
            _prompts[index] = new Prompt(id, name, content);
    }

    public void Delete(long id) => _prompts.RemoveAll(p => p.Id == id);
}

/// <summary>Answers with a fixed reply, or fails — the whole point of a fake provider.</summary>
internal sealed class FakeChatClient : IChatClient
{
    private readonly Func<Completion> _reply;

    public FakeChatClient(string text) => _reply = () => new Completion(text, 10, 5);

    public FakeChatClient(Exception error) => _reply = () => throw error;

    public bool IsConfigured(string providerId) => true;

    public string ModelFor(string providerId) => "fake-model";

    public Task<Completion> CompleteAsync(
        string providerId,
        string systemPrompt,
        IReadOnlyList<Message> history,
        string? imagePath = null,
        CancellationToken ct = default) =>
        Task.FromResult(_reply());
}
