using Lecturo.Domain.Chat;

namespace Lecturo.Application.Chat;

/// <summary>
/// A reply and what the provider said it cost. The token counts are how the
/// context counter stops guessing: the next call starts from the prompt tokens
/// the provider actually counted, plus whatever has arrived since.
/// </summary>
public sealed record Completion(string Text, int? PromptTokens, int? CompletionTokens);

/// <summary>
/// The provider port the chat use cases depend on. Implemented by the
/// infrastructure adapter that speaks the providers' wire formats. It never
/// touches the database and knows nothing about drafts or naming.
/// </summary>
public interface IChatClient
{
    bool IsConfigured(string providerId);

    /// <summary>The model a provider will actually be asked for, for the budget it implies.</summary>
    string ModelFor(string providerId);

    Task<Completion> CompleteAsync(
        string providerId,
        string systemPrompt,
        IReadOnlyList<Message> history,
        string? imagePath = null,
        CancellationToken ct = default);
}
