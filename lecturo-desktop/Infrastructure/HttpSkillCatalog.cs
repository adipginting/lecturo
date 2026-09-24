using System.Text.Json;
using Lecturo.Application.Skills;
using Lecturo.Domain;

namespace Lecturo.Infrastructure;

/// <summary>
/// Fetches and parses the skills marketplace JSON. Accepts either a
/// <c>{"skills":[…]}</c> envelope or a bare array; an entry without an id is
/// dropped rather than installed nameless.
/// </summary>
public sealed class HttpSkillCatalog : ISkillCatalog
{
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(45) };

    public async Task<IReadOnlyList<RemoteSkill>> FetchAsync(string marketplaceUrl, CancellationToken ct = default)
    {
        if (string.IsNullOrWhiteSpace(marketplaceUrl))
            throw new InvalidOperationException("Set the skills marketplace URL in Settings.");

        using var response = await Http.GetAsync(marketplaceUrl, ct);
        response.EnsureSuccessStatusCode();
        return Parse(await response.Content.ReadAsStringAsync(ct));
    }

    internal static List<RemoteSkill> Parse(string json)
    {
        using var document = JsonDocument.Parse(json);
        var root = document.RootElement;
        var array = root.ValueKind == JsonValueKind.Array
            ? root
            : root.TryGetProperty("skills", out var skills) && skills.ValueKind == JsonValueKind.Array
                ? skills
                : default;

        var result = new List<RemoteSkill>();
        if (array.ValueKind != JsonValueKind.Array)
            return result;

        foreach (var entry in array.EnumerateArray())
        {
            string Str(string name) => entry.TryGetProperty(name, out var value) ? value.GetString() ?? "" : "";

            var id = Str("id");
            if (id.Length == 0)
                continue;

            var name = Str("name");
            var scope = Str("scope");
            var trigger = Str("trigger");
            result.Add(new RemoteSkill(
                id,
                name.Length > 0 ? name : id,
                Str("description"),
                Str("author"),
                Str("version"),
                scope.Length > 0 ? scope : "message",
                trigger.Length > 0 ? trigger : "on_demand",
                Str("prompt"),
                Str("price")));
        }

        return result;
    }
}
