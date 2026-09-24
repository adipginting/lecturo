using Lecturo.Domain;
using Lecturo.Domain.Chat;
using Xunit;

namespace Lecturo.Tests;

/// <summary>What the next call carries, before and after a provider reports usage.</summary>
public class ContextCounterTests
{
    [Fact]
    public void Without_usage_the_whole_payload_is_estimated()
    {
        // 400 system + 800 active + 0 draft = 1200 characters → 300 tokens.
        var tokens = ContextCounter.Tokens(
            reportedPromptTokens: null,
            systemChars: 400,
            activeChars: 800,
            charsSinceLastCall: 0,
            draftChars: 0);

        Assert.Equal(300, tokens);
    }

    [Fact]
    public void With_usage_only_what_arrived_since_is_added()
    {
        // The provider counted 900; 80 characters have arrived since → +20.
        var tokens = ContextCounter.Tokens(
            reportedPromptTokens: 900,
            systemChars: 400,
            activeChars: 880,
            charsSinceLastCall: 80,
            draftChars: 0);

        Assert.Equal(920, tokens);
    }

    [Fact]
    public void What_is_typed_counts_either_way()
    {
        var withUsage = ContextCounter.Tokens(900, 400, 880, 80, 40);
        var without = ContextCounter.Tokens(null, 0, 0, 0, 40);

        Assert.Equal(930, withUsage);
        Assert.Equal(10, without);
    }

    [Fact]
    public void A_useless_reported_number_falls_back_to_estimating()
    {
        Assert.Equal(250, ContextCounter.Tokens(0, 1000, 0, 0, 0));
    }

    [Fact]
    public void Rounding_is_up_so_a_partial_token_still_counts()
    {
        Assert.Equal(2, ContextCounter.EstimateTokens(5));
        Assert.Equal(0, ContextCounter.EstimateTokens(0));
    }
}
