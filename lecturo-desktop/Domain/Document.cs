namespace Lecturo.Domain;

/// <summary>A document in the local library: a copy the app owns.</summary>
public sealed record Document(
    string Id,
    string Title,
    string FileName,
    DocumentFormat Format,
    string? LastLocator,
    DateTime UpdatedAt);
