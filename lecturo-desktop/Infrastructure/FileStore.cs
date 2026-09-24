using Lecturo.Application.Library;

namespace Lecturo.Infrastructure;

/// <summary>App-owned document storage on the real filesystem, under the XDG data dir.</summary>
public sealed class FileStore : IFileStore
{
    private readonly string _directory;

    public FileStore(string directory)
    {
        _directory = directory;
        Directory.CreateDirectory(_directory);
    }

    public string PathFor(string fileName) => Path.Combine(_directory, fileName);

    public bool Exists(string path) => File.Exists(path);

    public byte[] ReadAllBytes(string path) => File.ReadAllBytes(path);

    public void Write(string fileName, byte[] bytes) => File.WriteAllBytes(PathFor(fileName), bytes);

    public void Delete(string path) => File.Delete(path);
}
