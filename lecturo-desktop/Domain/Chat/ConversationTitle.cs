namespace Lecturo.Domain.Chat;

/// <summary>
/// The rules for naming a conversation. Names arrive from two directions — the
/// first message, which names a chat after what was asked, and the reader, who
/// types whatever they like — so both go through one sieve: whitespace is layout
/// rather than content, a name has to read as one line in a list, and nothing is
/// cut through the middle of an emoji.
/// </summary>
public static class ConversationTitle
{
    /// <summary>What a conversation is called until someone or something names it.</summary>
    public const string Untitled = "New conversation";

    /// <summary>Long enough for a sentence, short enough to stay one line in a list.</summary>
    public const int MaxLength = 60;

    /// <summary>A first message is squeezed harder: it sits beside a name and a date.</summary>
    private const int FromMessageLength = 40;

    /// <summary><paramref name="input"/> as a name to store, or null when there is nothing in it.</summary>
    public static string? Normalize(string? input) => Shorten(input, MaxLength);

    /// <summary>The name a conversation takes from its first message.</summary>
    public static string? FromMessage(string? message) => Shorten(message, FromMessageLength);

    private static string? Shorten(string? input, int maxLength)
    {
        if (string.IsNullOrWhiteSpace(input))
            return null;

        var collapsed = string.Join(' ',
            input.Split((char[]?)null, StringSplitOptions.RemoveEmptyEntries));
        var name = collapsed.Length <= maxLength ? collapsed : collapsed[..maxLength].TrimEnd();

        // A cut can land between the halves of an emoji, leaving half a pair.
        if (name.Length > 0 && char.IsHighSurrogate(name[^1]))
            name = name[..^1];

        return name.Length > 0 ? name : null;
    }
}
