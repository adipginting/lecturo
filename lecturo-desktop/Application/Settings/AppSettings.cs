using System.Text.Json.Serialization;
using Lecturo.Domain.Chat;

namespace Lecturo.Application.Settings;

/// <summary>Per-provider, non-secret configuration (base URL + model).</summary>
public sealed class ProviderConfig
{
    public string BaseUrl { get; set; } = string.Empty;
    public string Model { get; set; } = string.Empty;
}

/// <summary>Non-secret application settings, stored as JSON in ~/.config/lecturo.</summary>
public sealed class AppSettings
{
    /// <summary>Active provider id; see <see cref="ProviderCatalog"/>.</summary>
    public string ChatProvider { get; set; } = "deepseek";

    public Dictionary<string, ProviderConfig> Providers { get; set; } = new();

    /// <summary>Anonymous per-install id, the "key" Lecturo Cloud sends as a bearer token.</summary>
    public string DeviceId { get; set; } = "";

    /// <summary>
    /// A token limit the reader sets by hand. Left at
    /// <see cref="ModelWindow.DefaultBudget"/> it means "ask the model": the
    /// budget is derived from the chosen model's window instead.
    /// </summary>
    public int ContextThreshold { get; set; } = ModelWindow.DefaultBudget;

    /// <summary>Per-provider "current model reads images" overrides (S22).</summary>
    public Dictionary<string, bool> VisionOverrides { get; set; } = new();

    /// <summary>Whether a reply carrying TeX is typeset by the bundled KaTeX.</summary>
    public bool TypesetFormulas { get; set; } = true;

    // Remote library sources.
    public string? CalibreUrl { get; set; }
    public string? CalibreUsername { get; set; }
    public string? ZoteroUserId { get; set; }

    /// <summary>Zotero API base; defaults to the public Web API.</summary>
    public string ZoteroBaseUrl { get; set; } = "https://api.zotero.org";

    /// <summary>Skills marketplace JSON URL (S38).</summary>
    public string? MarketplaceUrl { get; set; }

    /// <summary>Returns the config for a provider, seeding defaults on first use.</summary>
    [JsonIgnore]
    public ProviderConfig this[string providerId]
    {
        get
        {
            if (!Providers.TryGetValue(providerId, out var cfg))
            {
                var info = ProviderCatalog.Get(providerId);
                cfg = new ProviderConfig { BaseUrl = info.DefaultBaseUrl, Model = info.DefaultModel };
                Providers[providerId] = cfg;
            }
            return cfg;
        }
    }

    public void SeedDefaults()
    {
        foreach (var info in ProviderCatalog.All)
        {
            if (!Providers.TryGetValue(info.Id, out var cfg))
            {
                Providers[info.Id] = new ProviderConfig
                {
                    BaseUrl = info.DefaultBaseUrl,
                    Model = info.DefaultModel,
                };
            }
            else
            {
                if (string.IsNullOrWhiteSpace(cfg.BaseUrl))
                    cfg.BaseUrl = info.DefaultBaseUrl;
                if (string.IsNullOrWhiteSpace(cfg.Model))
                    cfg.Model = info.DefaultModel;
            }
        }
    }
}
