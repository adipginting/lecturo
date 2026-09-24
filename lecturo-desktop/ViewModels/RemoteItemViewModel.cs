using Avalonia.Media;
using CommunityToolkit.Mvvm.ComponentModel;
using Lecturo.Domain;
using Lecturo.Services;

namespace Lecturo.ViewModels;

/// <summary>A remote-library row, with the UI state the list needs.</summary>
public partial class RemoteItemViewModel : ObservableObject
{
    public RemoteItem Item { get; }

    [ObservableProperty] public partial bool IsDownloaded { get; set; }
    [ObservableProperty] public partial bool IsBusy { get; set; }

    public RemoteItemViewModel(RemoteItem item) => Item = item;

    public string Title => Item.Title;
    public string Meta => Item.Meta;
    public string Initial => Item.Initial;
    public bool IsCatalog => Item.IsCatalog;
    public bool ShowDownload => !IsCatalog && !IsDownloaded;

    partial void OnIsDownloadedChanged(bool value) => OnPropertyChanged(nameof(ShowDownload));

    private long Seed
    {
        get
        {
            unchecked
            {
                long hash = 17;
                foreach (var c in Item.Source + Item.RemoteId)
                    hash = hash * 31 + c;
                return hash;
            }
        }
    }

    public IBrush CoverBackground => CoverPalette.Background(Seed);
    public IBrush CoverForeground => CoverPalette.Foreground(Seed);
}
