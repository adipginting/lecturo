namespace Lecturo.Domain.Chat;

/// <summary>A reusable prompt template: written once, named, and picked per conversation.</summary>
public sealed record Prompt(string Id, string Name, string Content);
