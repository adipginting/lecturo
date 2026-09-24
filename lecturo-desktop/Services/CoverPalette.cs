using Avalonia.Media;

namespace Lecturo.Services;

/// <summary>
/// Deterministic placeholder covers (an initial on a tinted tile), matching the
/// four cover tints in the design. Real covers (F7) can replace these per document.
/// </summary>
public static class CoverPalette
{
    private static readonly string[] Backgrounds = ["#FFD8E4", "#E8DEF8", "#E6E0E9", "#EADDFF"];
    private static readonly string[] Foregrounds = ["#31111D", "#1D192B", "#1C1B1F", "#21005D"];

    public static IBrush Background(long id) =>
        new SolidColorBrush(Color.Parse(Backgrounds[Index(id)]));

    public static IBrush Foreground(long id) =>
        new SolidColorBrush(Color.Parse(Foregrounds[Index(id)]));

    private static int Index(long id) => (int)((id & long.MaxValue) % Backgrounds.Length);
}
