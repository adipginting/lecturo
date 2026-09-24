namespace Lecturo.Domain.Chat;

/// <summary>
/// What the next call would carry. Once a provider has reported how many prompt
/// tokens a reply actually took, the counter starts from that number and adds only
/// what has arrived since; until then it estimates the whole payload.
/// </summary>
public static class ContextCounter
{
    /// <summary>Rough token count for prose: four characters to a token.</summary>
    public static int EstimateTokens(int chars) => (int)Math.Ceiling(chars / 4.0);

    /// <param name="reportedPromptTokens">What the provider counted for the last reply, if it said.</param>
    /// <param name="systemChars">The standing instruction that goes with every call.</param>
    /// <param name="activeChars">Every non-superseded turn, used when nothing has been reported.</param>
    /// <param name="charsSinceLastCall">The turns that arrived after the last reported call.</param>
    /// <param name="draftChars">Whatever is typed and not yet sent.</param>
    public static int Tokens(
        int? reportedPromptTokens,
        int systemChars,
        int activeChars,
        int charsSinceLastCall,
        int draftChars) =>
        reportedPromptTokens is { } reported and > 0
            ? reported + EstimateTokens(charsSinceLastCall + draftChars)
            : EstimateTokens(systemChars + activeChars + draftChars);
}
