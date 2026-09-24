using Avalonia;
using Avalonia.Media;
using System;

namespace Lecturo;

sealed class Program
{
    // Initialization code. Don't use any Avalonia, third-party APIs or any
    // SynchronizationContext-reliant code before AppMain is called: things aren't initialized
    // yet and stuff might break.
    [STAThread]
    public static void Main(string[] args) => BuildAvaloniaApp()
        .StartWithClassicDesktopLifetime(args);

    // Avalonia configuration, don't remove; also used by visual designer.
    public static AppBuilder BuildAvaloniaApp()
        => AppBuilder.Configure<App>()
            .UsePlatformDetect()
#if DEBUG
            .WithDeveloperTools()
#endif
            // Plus Jakarta Sans is the app's UI face (per the design decision); the
            // reading surface overrides it with Source Serif 4.
            .With(new FontManagerOptions
            {
                DefaultFamilyName = "avares://Lecturo/Assets/Fonts#Plus Jakarta Sans",
            })
            .LogToTrace();
}
