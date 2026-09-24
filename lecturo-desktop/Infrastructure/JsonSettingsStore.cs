using System.Text.Json;
using Lecturo.Application.Settings;

namespace Lecturo.Infrastructure;

/// <summary>Non-secret settings as a JSON file in ~/.config/lecturo.</summary>
public sealed class JsonSettingsStore : ISettingsStore
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
        PropertyNameCaseInsensitive = true,
    };

    private readonly string _path;

    public AppSettings Settings { get; private set; } = new();

    public JsonSettingsStore(string path)
    {
        _path = path;
        Load();
    }

    private void Load()
    {
        try
        {
            if (File.Exists(_path))
                Settings = JsonSerializer.Deserialize<AppSettings>(File.ReadAllText(_path)) ?? new AppSettings();
        }
        catch
        {
            Settings = new AppSettings();
        }
        Settings.SeedDefaults();

        if (string.IsNullOrWhiteSpace(Settings.DeviceId))
        {
            Settings.DeviceId = Guid.NewGuid().ToString("N");
            Save();
        }
    }

    public void Save()
    {
        Directory.CreateDirectory(Path.GetDirectoryName(_path)!);
        File.WriteAllText(_path, JsonSerializer.Serialize(Settings, JsonOptions));
    }
}
