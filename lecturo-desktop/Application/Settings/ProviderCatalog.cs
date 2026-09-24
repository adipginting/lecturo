namespace Lecturo.Application.Settings;

/// <summary>Static description of a chat provider (Lecturo's F5 list).</summary>
public sealed record ProviderInfo(
    string Id,
    string DisplayName,
    string DefaultBaseUrl,
    string DefaultModel,
    string SecretKey,
    bool Stubbed = false,
    bool RequiresKey = true);

/// <summary>
/// The provider catalogue behind the one OpenAI-compatible client plus the native
/// Anthropic client. GitHub Copilot is listed but not callable (no public chat API).
/// Lecturo Cloud is the bundled relay: no key, an anonymous device id instead.
/// </summary>
public static class ProviderCatalog
{
    public static readonly ProviderInfo[] All =
    [
        new("openai", "OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", "openai-api-key"),
        new("kimi", "Kimi", "https://api.moonshot.ai/v1", "kimi-k2-0905-preview", "kimi-api-key"),
        new("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "deepseek/deepseek-chat", "openrouter-api-key"),
        new("deepseek", "DeepSeek", "https://api.deepseek.com", "deepseek-chat", "deepseek-api-key"),
        new("anthropic", "Anthropic Claude", "https://api.anthropic.com", "claude-sonnet-4-5", "anthropic-api-key"),
        new("lecturo", "Lecturo Cloud", "http://localhost:8787", "lecturo-cloud", "", RequiresKey: false),
        new("copilot", "GitHub Copilot", "", "", "copilot-api-key", Stubbed: true),
    ];

    public static ProviderInfo Get(string id) =>
        All.FirstOrDefault(p => p.Id == id) ?? All[0];

    public static string DisplayName(string id) => Get(id).DisplayName;
}
