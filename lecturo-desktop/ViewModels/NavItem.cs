using Avalonia.Media;
using Avalonia.Media.Imaging;
using CommunityToolkit.Mvvm.ComponentModel;
using Lecturo.Domain;
using Lecturo.Services;

namespace Lecturo.ViewModels;

/// <summary>An entry in the left navigation rail.</summary>
public sealed record NavItem(string Key, string Label, Geometry Icon);

/// <summary>A library row: the document plus the values its card needs.</summary>
public partial class DocumentItem : ObservableObject
{
    public DocumentItem(Document document) => Document = document;

    public Document Document { get; }

    [ObservableProperty] public partial bool IsSelected { get; set; }
    [ObservableProperty] public partial Bitmap? Cover { get; set; }

    public bool HasCover => Cover is not null;

    partial void OnCoverChanged(Bitmap? value) => OnPropertyChanged(nameof(HasCover));

    public long Id => Document.Id;
    public string Title => Document.Title;

    public string Initial =>
        string.IsNullOrWhiteSpace(Title) ? "?" : Title.Trim()[..1].ToUpperInvariant();

    public IBrush CoverBackground => CoverPalette.Background(Document.Id);
    public IBrush CoverForeground => CoverPalette.Foreground(Document.Id);

    public string FormatLabel => Document.Format.ToLabel();

    public string LocationLabel => Document.LastLocator switch
    {
        null or "" => "Not started",
        var locator when Document.Format == DocumentFormat.Pdf => $"page {locator}",
        var locator => $"chapter {locator}",
    };

    public string Meta => $"{FormatLabel} · {LocationLabel}";
}
