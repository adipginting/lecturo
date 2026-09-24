namespace Lecturo.Domain.Chat;

/// <summary>
/// The passage, screenshot or crop a conversation is about. Captured once and
/// frozen: compaction never folds it, and a conversation never loses it.
/// </summary>
public sealed record Excerpt(string? Text = null, string? ImagePath = null)
{
    public static Excerpt FromText(string text) => new(Text: text);

    public static Excerpt FromImage(string imagePath) => new(ImagePath: imagePath);

    /// <summary>The excerpt's text, or an empty string when this one is a picture.</summary>
    public string TextOrEmpty => Text ?? "";

    public bool HasImage => !string.IsNullOrWhiteSpace(ImagePath);

    public bool IsEmpty => string.IsNullOrWhiteSpace(Text) && !HasImage;
}
