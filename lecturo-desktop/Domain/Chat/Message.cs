namespace Lecturo.Domain.Chat;

/// <summary>
/// One turn in a conversation: written by the reader, written by the model, or a
/// summary standing in for several older turns.
/// </summary>
public sealed record Message(
    string Id,
    string ConversationId,
    MessageRole Role,
    string Content,
    DateTime CreatedAt,
    bool Superseded = false)
{
    public bool IsSummary => Role == MessageRole.Summary;

    /// <summary>A message folded into a summary is history; it is never sent again.</summary>
    public bool IsActive => !Superseded;
}

/// <summary>
/// Who wrote a message. A summary is its own role here rather than a flag on an
/// assistant turn, because it is the one that replaces others.
/// </summary>
public enum MessageRole
{
    User,
    Assistant,
    Summary,
}

public static class MessageRoleExtensions
{
    /// <summary>The value stored in the database; existing rows depend on it.</summary>
    public static string ToStoredValue(this MessageRole role) => role switch
    {
        MessageRole.User => "user",
        MessageRole.Assistant => "assistant",
        MessageRole.Summary => "summary",
        _ => throw new ArgumentOutOfRangeException(nameof(role), role, "Unknown message role."),
    };

    /// <summary>The role a provider is told about: a summary is an assistant turn.</summary>
    public static string ToProviderRole(this MessageRole role) =>
        role == MessageRole.Summary ? "assistant" : role.ToStoredValue();

    public static MessageRole? FromStoredValue(string? value) => value?.ToLowerInvariant() switch
    {
        "user" => MessageRole.User,
        "assistant" => MessageRole.Assistant,
        "summary" => MessageRole.Summary,
        _ => null,
    };

    public static MessageRole ParseStoredValue(string value) =>
        FromStoredValue(value) ?? throw new FormatException($"Unknown message role: \u201c{value}\u201d.");
}
