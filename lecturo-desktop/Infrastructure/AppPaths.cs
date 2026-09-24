namespace Lecturo.Infrastructure;

/// <summary>
/// Per-platform config and data locations: XDG on Linux, the roaming/local
/// application-data folders on Windows.
/// </summary>
public static class AppPaths
{
    private static bool IsWindows => OperatingSystem.IsWindows();

    private static string Home => Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);

    public static string ConfigDir { get; } = IsWindows
        ? Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "lecturo")
        : Path.Combine(
            Environment.GetEnvironmentVariable("XDG_CONFIG_HOME") ?? Path.Combine(Home, ".config"),
            "lecturo");

    public static string DataDir { get; } = IsWindows
        ? Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "lecturo")
        : Path.Combine(
            Environment.GetEnvironmentVariable("XDG_DATA_HOME") ?? Path.Combine(Home, ".local", "share"),
            "lecturo");

    public static string DocumentsDir => Path.Combine(DataDir, "documents");
    public static string DatabasePath => Path.Combine(DataDir, "lecturo.db");
    public static string SettingsPath => Path.Combine(ConfigDir, "settings.json");

    /// <summary>On-disk copies of the embedded fonts, for the WebView reading surface.</summary>
    public static string UiFontPath => Path.Combine(AppContext.BaseDirectory, "Assets", "Fonts", "PlusJakartaSans-Regular.ttf");
    public static string ReadingFontPath => Path.Combine(AppContext.BaseDirectory, "Assets", "Fonts", "SourceSerif4-Regular.ttf");

    public static void EnsureDirectories()
    {
        Directory.CreateDirectory(ConfigDir);
        Directory.CreateDirectory(DataDir);
        Directory.CreateDirectory(DocumentsDir);
    }
}
