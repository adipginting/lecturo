using Xunit;

namespace Lecturo.Tests;

/// <summary>
/// The layering the DDD refactor promises: the interfaces layer (ViewModels) and
/// the application layer never reach into infrastructure. This guards the rule
/// that used to be broken by view models taking concrete repositories.
/// </summary>
public class ArchitectureTests
{
    [Fact]
    public void View_models_and_application_do_not_reference_infrastructure()
    {
        var root = FindRepoRoot();

        var offenders = SourceFiles(root, "ViewModels")
            .Concat(SourceFiles(root, "Application"))
            .Where(file => File.ReadAllText(file).Contains("Lecturo.Infrastructure", StringComparison.Ordinal))
            .Select(file => Path.GetRelativePath(root, file))
            .ToList();

        Assert.Empty(offenders);
    }

    [Fact]
    public void Application_does_not_reference_the_desktop_ui_toolkit()
    {
        var root = FindRepoRoot();

        var offenders = SourceFiles(root, "Application")
            .Where(file => File.ReadAllText(file).Contains("using Avalonia", StringComparison.Ordinal))
            .Select(file => Path.GetRelativePath(root, file))
            .ToList();

        Assert.Empty(offenders);
    }

    private static IEnumerable<string> SourceFiles(string root, string folder) =>
        Directory.EnumerateFiles(Path.Combine(root, folder), "*.cs", SearchOption.AllDirectories);

    private static string FindRepoRoot()
    {
        var dir = new DirectoryInfo(AppContext.BaseDirectory);
        while (dir is not null && !File.Exists(Path.Combine(dir.FullName, "Lecturo.csproj")))
            dir = dir.Parent;
        if (dir is null)
            throw new InvalidOperationException("Could not find the repository root from " + AppContext.BaseDirectory);
        return dir.FullName;
    }
}
