using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using Lecturo.Application.Chat;
using Lecturo.Application.Settings;
using Lecturo.Domain;
using Lecturo.Domain.Chat;

namespace Lecturo.Infrastructure;

/// <summary>
/// Chat completions behind one interface. OpenAI, Kimi, OpenRouter and DeepSeek
/// share the OpenAI-compatible client; Anthropic uses its native Messages API.
/// An optional context image (a screenshot) rides on the first user message.
/// Implements the application's <see cref="IChatClient"/> port: it speaks the
/// providers' wire formats and knows nothing about drafts or naming.
/// </summary>
public sealed class ChatClient : IChatClient
{
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(90) };

    private readonly ISettingsStore _settings;
    private readonly ISecretStore _secrets;

    public ChatClient(ISettingsStore settings, ISecretStore secrets)
    {
        _settings = settings;
        _secrets = secrets;
    }

    public bool IsConfigured(string providerId)
    {
        var info = ProviderCatalog.Get(providerId);
        if (info.Stubbed)
            return false;
        return !info.RequiresKey || !string.IsNullOrWhiteSpace(_secrets.Get(info.SecretKey));
    }

    public async Task<Completion> CompleteAsync(
        string providerId,
        string systemPrompt,
        IReadOnlyList<Message> history,
        string? imagePath = null,
        CancellationToken ct = default)
    {
        var info = ProviderCatalog.Get(providerId);
        if (info.Stubbed)
            throw new NotSupportedException($"{info.DisplayName} has no public chat API.");

        // Lecturo Cloud authenticates the install, not a key: the device id is the bearer.
        var apiKey = info.RequiresKey ? _secrets.Get(info.SecretKey) : _settings.Settings.DeviceId;
        if (string.IsNullOrWhiteSpace(apiKey))
            throw new InvalidOperationException($"No API key configured for {info.DisplayName}.");

        var cfg = _settings.Settings[providerId];
        var model = string.IsNullOrWhiteSpace(cfg.Model) ? info.DefaultModel : cfg.Model;
        var baseUrl = string.IsNullOrWhiteSpace(cfg.BaseUrl) ? info.DefaultBaseUrl : cfg.BaseUrl;
        var image = EncodeImage(imagePath);

        return providerId == "anthropic"
            ? await CompleteAnthropicAsync(baseUrl, model, apiKey, systemPrompt, history, image, ct)
            : await CompleteOpenAiAsync(baseUrl, model, apiKey, systemPrompt, history, image, ct);
    }

    /// <summary>The model a provider will actually be asked for, for the budget it implies.</summary>
    public string ModelFor(string providerId)
    {
        var info = ProviderCatalog.Get(providerId);
        var cfg = _settings.Settings[providerId];
        return string.IsNullOrWhiteSpace(cfg.Model) ? info.DefaultModel : cfg.Model;
    }

    private static async Task<Completion> CompleteOpenAiAsync(
        string baseUrl,
        string model,
        string apiKey,
        string systemPrompt,
        IReadOnlyList<Message> history,
        (string MediaType, string Base64)? image,
        CancellationToken ct)
    {
        var messages = new JsonArray
        {
            new JsonObject { ["role"] = "system", ["content"] = systemPrompt },
        };

        var attached = false;
        foreach (var m in history)
        {
            if (image is { } img && !attached && m.Role == MessageRole.User)
            {
                attached = true;
                messages.Add(new JsonObject
                {
                    ["role"] = "user",
                    ["content"] = new JsonArray(
                        new JsonObject { ["type"] = "text", ["text"] = m.Content },
                        new JsonObject
                        {
                            ["type"] = "image_url",
                            ["image_url"] = new JsonObject { ["url"] = $"data:{img.MediaType};base64,{img.Base64}" },
                        }),
                });
                continue;
            }

            messages.Add(new JsonObject { ["role"] = m.Role.ToProviderRole(), ["content"] = m.Content });
        }

        var body = new JsonObject
        {
            ["model"] = model,
            ["messages"] = messages,
        };

        using var request = new HttpRequestMessage(HttpMethod.Post, $"{baseUrl.TrimEnd('/')}/chat/completions")
        {
            Content = new StringContent(body.ToJsonString(), Encoding.UTF8, "application/json"),
        };
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", apiKey);

        using var response = await Http.SendAsync(request, ct);
        var payload = await response.Content.ReadAsStringAsync(ct);
        if (!response.IsSuccessStatusCode)
            throw new InvalidOperationException($"Provider returned {(int)response.StatusCode}: {Truncate(payload)}");

        var json = JsonNode.Parse(payload);
        var content = json?["choices"]?[0]?["message"]?["content"]?.GetValue<string>();
        if (string.IsNullOrWhiteSpace(content))
            throw new InvalidOperationException("Provider returned an empty reply.");

        return new Completion(content, Number(json?["usage"]?["prompt_tokens"]), Number(json?["usage"]?["completion_tokens"]));
    }

    private static async Task<Completion> CompleteAnthropicAsync(
        string baseUrl,
        string model,
        string apiKey,
        string systemPrompt,
        IReadOnlyList<Message> history,
        (string MediaType, string Base64)? image,
        CancellationToken ct)
    {
        var messages = new JsonArray();
        var attached = false;
        foreach (var m in history)
        {
            if (image is { } img && !attached && m.Role == MessageRole.User)
            {
                attached = true;
                messages.Add(new JsonObject
                {
                    ["role"] = "user",
                    ["content"] = new JsonArray(
                        new JsonObject
                        {
                            ["type"] = "image",
                            ["source"] = new JsonObject
                            {
                                ["type"] = "base64",
                                ["media_type"] = img.MediaType,
                                ["data"] = img.Base64,
                            },
                        },
                        new JsonObject { ["type"] = "text", ["text"] = m.Content }),
                });
                continue;
            }

            messages.Add(new JsonObject { ["role"] = m.Role.ToProviderRole(), ["content"] = m.Content });
        }

        var body = new JsonObject
        {
            ["model"] = model,
            ["max_tokens"] = 4096,
            ["system"] = systemPrompt,
            ["messages"] = messages,
        };

        var url = baseUrl.TrimEnd('/').EndsWith("/v1", StringComparison.OrdinalIgnoreCase)
            ? $"{baseUrl.TrimEnd('/')}/messages"
            : $"{baseUrl.TrimEnd('/')}/v1/messages";

        using var request = new HttpRequestMessage(HttpMethod.Post, url)
        {
            Content = new StringContent(body.ToJsonString(), Encoding.UTF8, "application/json"),
        };
        request.Headers.Add("x-api-key", apiKey);
        request.Headers.Add("anthropic-version", "2023-06-01");

        using var response = await Http.SendAsync(request, ct);
        var payload = await response.Content.ReadAsStringAsync(ct);
        if (!response.IsSuccessStatusCode)
            throw new InvalidOperationException($"Provider returned {(int)response.StatusCode}: {Truncate(payload)}");

        var json = JsonNode.Parse(payload);
        var content = json?["content"]?[0]?["text"]?.GetValue<string>();
        if (string.IsNullOrWhiteSpace(content))
            throw new InvalidOperationException("Provider returned an empty reply.");

        return new Completion(content, Number(json?["usage"]?["input_tokens"]), Number(json?["usage"]?["output_tokens"]));
    }

    /// <summary>A provider's reported token count, or null when it reported none.</summary>
    private static int? Number(JsonNode? node) =>
        node is JsonValue value && value.TryGetValue<int>(out var number) ? number : null;

    private static (string MediaType, string Base64)? EncodeImage(string? path)
    {
        if (string.IsNullOrWhiteSpace(path) || !File.Exists(path))
            return null;

        var mediaType = Path.GetExtension(path).ToLowerInvariant() switch
        {
            ".jpg" or ".jpeg" => "image/jpeg",
            ".webp" => "image/webp",
            _ => "image/png",
        };

        return (mediaType, Convert.ToBase64String(File.ReadAllBytes(path)));
    }

    private static string Truncate(string value) =>
        value.Length <= 400 ? value : value[..400] + "…";
}
