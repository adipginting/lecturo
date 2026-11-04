namespace Lecturo.Domain;

/// <summary>
/// The record that a document was downloaded, and from which remote item. It is
/// what lets a remote list mark an item as already local.
/// </summary>
public sealed record Provenance(
    string Id,
    string DocumentId,
    string Source,
    string RemoteId,
    string Url,
    string Title,
    DateTime CreatedAt)
{
    /// <summary>The identity of a remote item, as a remote list recognises it.</summary>
    public static string Key(string source, string remoteId) => source + "\u001f" + remoteId;

    public string RemoteKey => Key(Source, RemoteId);
}
