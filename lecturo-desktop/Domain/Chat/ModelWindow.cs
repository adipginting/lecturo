namespace Lecturo.Domain.Chat;

/// <summary>
/// How much a conversation may carry before it is compacted. The model's window
/// decides, less room for the reply and a safety margin — a larger window is not
/// an invitation to spend it, and a small one must not compact a short chat. An
/// unknown model keeps the conservative default, and a number the reader typed
/// wins outright.
/// </summary>
public static class ModelWindow
{
    /// <summary>Context windows we know, matched by the first pattern a model name contains.</summary>
    private static readonly (string Pattern, int Window)[] Known =
    [
        ("moonshot-v1-128k", 128_000),
        ("moonshot-v1-32k", 32_000),
        ("moonshot-v1-8k", 8_000),
        ("moonshot", 8_000),
        ("gpt-4.1", 1_000_000),
        ("gpt-4o", 128_000),
        ("o3", 200_000),
        ("o4", 200_000),
        ("claude", 200_000),
        ("deepseek", 64_000),
        ("gemini", 1_000_000),
    ];

    /// <summary>Room kept for the reply the next call will ask for.</summary>
    private const int ReservedOutput = 4_000;

    /// <summary>A margin, because the estimate is an estimate.</summary>
    private const double Safety = 0.85;

    private const int MinBudget = 2_000;
    private const int MaxBudget = 32_000;

    /// <summary>The conservative budget for a model we have never heard of.</summary>
    public const int DefaultBudget = 8_000;

    /// <summary>The window of a model we know, or null when we do not.</summary>
    public static int? WindowFor(string? model)
    {
        if (string.IsNullOrWhiteSpace(model))
            return null;

        var name = model.ToLowerInvariant();
        foreach (var (pattern, window) in Known)
        {
            if (name.Contains(pattern, StringComparison.Ordinal))
                return window;
        }
        return null;
    }

    /// <summary>A number the reader typed wins outright; otherwise the model decides.</summary>
    public static int BudgetFor(string? model, int? over = null)
    {
        if (over is { } value and > 0)
            return value;

        if (WindowFor(model) is not { } window)
            return DefaultBudget;

        return Math.Clamp((int)((window - ReservedOutput) * Safety), MinBudget, MaxBudget);
    }
}
