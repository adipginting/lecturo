namespace Lecturo.Application.Settings;

/// <summary>
/// Stores secrets (API keys, passwords) encrypted at rest.
/// Keys are well-known names like "openai-api-key", "anthropic-api-key",
/// "kimi-api-key", "calibre-password", "zotero-api-key".
/// </summary>
public interface ISecretStore
{
    string? Get(string key);
    void Set(string key, string value);
    void Delete(string key);

    /// <summary>Non-null when running on a degraded (non-keychain) backend.</summary>
    string? Warning { get; }
}
