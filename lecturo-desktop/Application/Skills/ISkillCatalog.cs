using Lecturo.Domain;

namespace Lecturo.Application.Skills;

/// <summary>
/// The skills marketplace the catalog is browsed from. A port so browsing is
/// tested without the network.
/// </summary>
public interface ISkillCatalog
{
    Task<IReadOnlyList<RemoteSkill>> FetchAsync(string marketplaceUrl, CancellationToken ct = default);
}
