using Lecturo.Domain.Chat;

namespace Lecturo.Application.Chat;

/// <summary>
/// The reuse of prompt templates, separate from the conversation aggregate: a
/// prompt is reference data a conversation points at, not a part of its life.
/// </summary>
public interface IPromptRepository
{
    List<Prompt> List();
    Prompt? Get(string id);
    string Add(string name, string content);
    void Update(string id, string name, string content);
    void Delete(string id);
}
