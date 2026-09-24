using Lecturo.Domain;
using Lecturo.Domain.Chat;
using Xunit;

namespace Lecturo.Tests;

/// <summary>
/// The window decides the budget, less room for the reply and a margin, capped
/// hard and never so low that a short chat compacts itself.
/// </summary>
public class ModelWindowTests
{
    [Fact]
    public void A_large_window_is_capped_not_spent()
    {
        Assert.Equal(32_000, ModelWindow.BudgetFor("gpt-4o"));
        Assert.Equal(32_000, ModelWindow.BudgetFor("claude-sonnet-4-5"));
        Assert.Equal(32_000, ModelWindow.BudgetFor("deepseek-chat"));
    }

    [Fact]
    public void A_small_window_gives_a_small_budget_floor_and_all()
    {
        Assert.Equal(3_400, ModelWindow.BudgetFor("moonshot-v1-8k"));
    }

    [Fact]
    public void An_unknown_model_keeps_the_conservative_default()
    {
        Assert.Equal(8_000, ModelWindow.BudgetFor("some-new-model"));
        Assert.Null(ModelWindow.WindowFor("some-new-model"));
    }

    [Fact]
    public void An_override_wins_outright()
    {
        Assert.Equal(12_000, ModelWindow.BudgetFor("gpt-4o", 12_000));
        Assert.Equal(12_000, ModelWindow.BudgetFor("unknown", 12_000));
    }

    [Fact]
    public void Nonsense_overrides_are_ignored()
    {
        Assert.Equal(32_000, ModelWindow.BudgetFor("gpt-4o", 0));
        Assert.Equal(32_000, ModelWindow.BudgetFor("gpt-4o", -5));
    }

    [Fact]
    public void A_model_name_is_matched_case_insensitively_and_by_containment()
    {
        Assert.Equal(64_000, ModelWindow.WindowFor("DeepSeek-V3"));
        Assert.Equal(1_000_000, ModelWindow.WindowFor("openai/gpt-4.1-mini"));
    }
}
