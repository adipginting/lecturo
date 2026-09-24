namespace Lecturo.Application.Settings;

/// <summary>
/// The non-secret settings the application reads and writes. Implemented by the
/// JSON store in infrastructure; a fake can stand in for tests.
/// </summary>
public interface ISettingsStore
{
    AppSettings Settings { get; }
    void Save();
}
