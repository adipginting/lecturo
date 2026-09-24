namespace Lecturo.Application.Library;

/// <summary>
/// The app-owned file storage the library copies documents into. A port so the
/// import use cases can be tested without touching the real filesystem.
/// </summary>
public interface IFileStore
{
    /// <summary>The absolute path an app-owned file with this name lives at.</summary>
    string PathFor(string fileName);

    bool Exists(string path);
    byte[] ReadAllBytes(string path);
    void Write(string fileName, byte[] bytes);
    void Delete(string path);
}
