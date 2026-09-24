using System.Collections.ObjectModel;
using Avalonia;
using Avalonia.Controls.ApplicationLifetimes;
using Avalonia.Media.Imaging;
using Avalonia.Platform.Storage;
using Avalonia.Threading;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Lecturo.Application.Catalog;
using Lecturo.Application.Library;
using Lecturo.Domain;
using Lecturo.Services;

namespace Lecturo.ViewModels;

public partial class LibraryViewModel : ViewModelBase
{
    private static readonly FilePickerFileType DocumentPickerType = new("Documents (PDF, EPUB)")
    {
        Patterns = ["*.pdf", "*.epub"],
    };

    private readonly LibraryService _library;
    private readonly CatalogService _catalog;
    private readonly CoverService _covers;

    private readonly Stack<(string? url, string title)> _catalogPath = new();
    private string? _catalogUrl;
    private string _catalogTitle = "";

    public ObservableCollection<DocumentItem> Documents { get; } = new();
    public ObservableCollection<RemoteItemViewModel> RemoteItems { get; } = new();
    public string[] Tabs { get; } = ["Local", "Calibre", "Zotero"];

    [ObservableProperty] public partial string SelectedTab { get; set; } = "Local";
    [ObservableProperty] public partial string DownloadUrl { get; set; } = "";
    [ObservableProperty] public partial string StatusMessage { get; set; } = "";
    [ObservableProperty] public partial bool IsBusy { get; set; }
    [ObservableProperty] public partial bool HasDocuments { get; set; }

    // ---- Remote library state ----
    [ObservableProperty] public partial string RemoteSearch { get; set; } = "";
    [ObservableProperty] public partial string RemoteStatus { get; set; } = "";
    [ObservableProperty] public partial bool RemoteIsBusy { get; set; }
    [ObservableProperty] public partial bool RemoteHasItems { get; set; }
    [ObservableProperty] public partial bool RemoteIsConfigured { get; set; }
    [ObservableProperty] public partial bool CanGoBack { get; set; }
    [ObservableProperty] public partial string RemoteTitle { get; set; } = "";

    /// <summary>Set by the shell so the card can open the reader.</summary>
    public Action<Document>? OpenDocument { get; set; }

    /// <summary>Raised whenever the local library changes.</summary>
    public event Action? DataChanged;

    public LibraryViewModel(
        LibraryService library,
        CatalogService catalog,
        CoverService covers)
    {
        _library = library;
        _catalog = catalog;
        _covers = covers;
        Refresh();
    }

    public bool IsLocal => SelectedTab == "Local";
    public bool ShowLocalEmpty => IsLocalTab && !HasDocuments;
    public bool IsLocalTab => SelectedTab == "Local";
    public bool IsCalibreTab => SelectedTab == "Calibre";
    public bool IsZoteroTab => SelectedTab == "Zotero";
    public bool IsRemoteTab => IsCalibreTab || IsZoteroTab;

    partial void OnHasDocumentsChanged(bool value) => OnPropertyChanged(nameof(ShowLocalEmpty));

    partial void OnSelectedTabChanged(string value)
    {
        OnPropertyChanged(nameof(IsLocal));
        OnPropertyChanged(nameof(IsLocalTab));
        OnPropertyChanged(nameof(IsCalibreTab));
        OnPropertyChanged(nameof(IsZoteroTab));
        OnPropertyChanged(nameof(IsRemoteTab));
        OnPropertyChanged(nameof(ShowLocalEmpty));
        ResetRemote();
    }

    [RelayCommand]
    private void SelectTab(string tab) => SelectedTab = tab;

    [RelayCommand]
    private void Refresh()
    {
        Documents.Clear();
        foreach (var doc in _library.List())
            Documents.Add(new DocumentItem(doc));
        HasDocuments = Documents.Count > 0;
        NotifySelection();
        ScheduleCovers();
        DataChanged?.Invoke();
    }

    // ---- Selection + delete (S6) ----
    [ObservableProperty] public partial bool IsSelecting { get; set; }
    [ObservableProperty] public partial bool IsConfirmingDelete { get; set; }
    [ObservableProperty] public partial DocumentItem? PendingDelete { get; set; }

    public int SelectedCount => Documents.Count(d => d.IsSelected);
    public bool HasSelection => SelectedCount > 0;

    [RelayCommand]
    private void Activate(DocumentItem item)
    {
        if (IsSelecting)
        {
            item.IsSelected = !item.IsSelected;
            NotifySelection();
            return;
        }

        OpenDocument?.Invoke(item.Document);
    }

    [RelayCommand]
    private void ToggleSelectMode()
    {
        IsSelecting = !IsSelecting;
        if (!IsSelecting)
        {
            foreach (var document in Documents)
                document.IsSelected = false;
            NotifySelection();
        }
    }

    [RelayCommand]
    private void RequestDelete(DocumentItem item)
    {
        PendingDelete = item;
        IsConfirmingDelete = true;
    }

    [RelayCommand]
    private void CancelDelete()
    {
        IsConfirmingDelete = false;
        PendingDelete = null;
    }

    [RelayCommand]
    private void ConfirmDelete()
    {
        if (PendingDelete is { } item)
        {
            _library.Delete(item.Document);
            Documents.Remove(item);
            HasDocuments = Documents.Count > 0;
            StatusMessage = $"Removed \u201c{item.Title}\u201d.";
            DataChanged?.Invoke();
        }

        IsConfirmingDelete = false;
        PendingDelete = null;
        NotifySelection();
    }

    [RelayCommand]
    private void DeleteSelected()
    {
        var selected = Documents.Where(d => d.IsSelected).ToList();
        foreach (var item in selected)
        {
            _library.Delete(item.Document);
            Documents.Remove(item);
        }

        if (selected.Count > 0)
        {
            HasDocuments = Documents.Count > 0;
            StatusMessage = $"Removed {selected.Count} document(s).";
            DataChanged?.Invoke();
        }

        IsSelecting = false;
        NotifySelection();
    }

    private void NotifySelection()
    {
        OnPropertyChanged(nameof(SelectedCount));
        OnPropertyChanged(nameof(HasSelection));
    }

    /// <summary>Extracts missing covers off the UI thread and attaches them as they arrive.</summary>
    private void ScheduleCovers()
    {
        var items = Documents.ToList();
        _ = Task.Run(() =>
        {
            foreach (var item in items)
            {
                var coverPath = _covers.EnsureCover(item.Document, _library.GetPath(item.Document));
                if (coverPath is null)
                    continue;

                Dispatcher.UIThread.Post(() =>
                {
                    try
                    {
                        item.Cover = new Bitmap(coverPath);
                    }
                    catch
                    {
                        // A broken image just leaves the initial tile.
                    }
                });
            }
        });
    }

    [RelayCommand]
    private async Task ImportAsync()
    {
        var storage = (Avalonia.Application.Current?.ApplicationLifetime as IClassicDesktopStyleApplicationLifetime)
            ?.MainWindow?.StorageProvider;
        if (storage is null)
            return;

        var files = await storage.OpenFilePickerAsync(new FilePickerOpenOptions
        {
            Title = "Import documents",
            AllowMultiple = true,
            FileTypeFilter = [DocumentPickerType],
        });

        await ImportPathsAsync(files
            .Select(f => f.TryGetLocalPath())
            .Where(p => p is not null)
            .Cast<string>());
    }

    /// <summary>Entry point for drag-and-drop and the file picker.</summary>
    public async Task ImportPathsAsync(IEnumerable<string> paths)
    {
        var list = paths.ToList();
        if (list.Count == 0)
            return;

        await RunImportAsync(() =>
        {
            foreach (var path in list)
                Documents.Insert(0, new DocumentItem(_library.ImportFromFile(path)));
            HasDocuments = Documents.Count > 0;
            StatusMessage = $"Imported {list.Count} document(s).";
            ScheduleCovers();
            DataChanged?.Invoke();
            return Task.CompletedTask;
        });
    }

    [RelayCommand]
    private async Task DownloadAsync()
    {
        var url = DownloadUrl.Trim();
        if (url.Length == 0)
            return;

        await RunImportAsync(async () =>
        {
            var doc = await _library.ImportFromUrlAsync(url);
            Documents.Insert(0, new DocumentItem(doc));
            HasDocuments = true;
            DownloadUrl = "";
            StatusMessage = $"Downloaded \"{doc.Title}\".";
            ScheduleCovers();
            DataChanged?.Invoke();
        });
    }

    // ===================== Remote libraries (S7–S12) =====================

    [RelayCommand]
    private async Task SyncRemoteAsync()
    {
        var source = _catalog.For(SelectedTab);
        if (source is null)
            return;

        if (!source.IsConfigured)
        {
            RemoteStatus = ConfigHint(SelectedTab);
            return;
        }

        try
        {
            RemoteIsBusy = true;
            RemoteStatus = "";
            var items = await source.BrowseAsync(_catalogUrl);
            ShowRemote(items);
        }
        catch (Exception ex)
        {
            RemoteStatus = $"Could not reach {SelectedTab}: {ex.Message}";
        }
        finally
        {
            RemoteIsBusy = false;
        }
    }

    [RelayCommand]
    private async Task SearchRemoteAsync()
    {
        var source = _catalog.For(SelectedTab);
        if (source is null)
            return;

        if (!source.IsConfigured)
        {
            RemoteStatus = ConfigHint(SelectedTab);
            return;
        }

        var query = RemoteSearch.Trim();
        if (query.Length == 0)
        {
            await SyncRemoteAsync();
            return;
        }

        try
        {
            RemoteIsBusy = true;
            RemoteStatus = "";
            var items = await source.SearchAsync(query);
            ShowRemote(items, $"Search: {query}");
        }
        catch (Exception ex)
        {
            RemoteStatus = $"Search failed: {ex.Message}";
        }
        finally
        {
            RemoteIsBusy = false;
        }
    }

    /// <summary>Development aid: sync then download the first book, to exercise S9.</summary>
    [RelayCommand]
    private async Task BrowseAndDownloadFirstAsync()
    {
        await SyncRemoteAsync();
        var first = RemoteItems.FirstOrDefault(r => !r.IsCatalog && r.ShowDownload);
        if (first is not null)
            await OpenRemoteAsync(first);
    }

    [RelayCommand]
    private async Task BackCatalogAsync()
    {
        if (_catalogPath.Count == 0)
            return;

        var previous = _catalogPath.Pop();
        _catalogUrl = previous.url;
        _catalogTitle = previous.title;
        CanGoBack = _catalogPath.Count > 0;
        await SyncRemoteAsync();
    }

    [RelayCommand]
    private async Task OpenRemoteAsync(RemoteItemViewModel row)
    {
        var source = _catalog.For(SelectedTab);
        if (source is null)
            return;

        if (row.Item.IsCatalog)
        {
            _catalogPath.Push((_catalogUrl, _catalogTitle));
            _catalogUrl = row.Item.CatalogUrl;
            _catalogTitle = row.Item.Title;
            CanGoBack = true;
            await SyncRemoteAsync();
            return;
        }

        // Already downloaded: open the local copy, no network.
        if (row.IsDownloaded &&
            _catalog.FindDocumentId(row.Item.Source, row.Item.RemoteId) is { } localId &&
            _library.Get(localId) is { } local)
        {
            OpenDocument?.Invoke(local);
            return;
        }

        try
        {
            row.IsBusy = true;
            RemoteStatus = $"Downloading \u201c{row.Title}\u201d\u2026";
            var doc = await _catalog.DownloadAsync(source, row.Item, row.Title);
            row.IsDownloaded = true;
            Documents.Insert(0, new DocumentItem(doc));
            HasDocuments = true;
            RemoteStatus = $"Downloaded \u201c{doc.Title}\u201d.";
            ScheduleCovers();
            DataChanged?.Invoke();
            OpenDocument?.Invoke(doc);
        }
        catch (Exception ex)
        {
            RemoteStatus = $"Download failed: {ex.Message}";
        }
        finally
        {
            row.IsBusy = false;
        }
    }

    private void ShowRemote(IReadOnlyList<RemoteItem> items, string? title = null)
    {
        var keys = _catalog.DownloadedKeys();
        RemoteItems.Clear();
        foreach (var item in items)
        {
            RemoteItems.Add(new RemoteItemViewModel(item)
            {
                IsDownloaded = keys.Contains(Provenance.Key(item.Source, item.RemoteId)),
            });
        }

        if (title is not null)
            _catalogTitle = title;
        RemoteTitle = _catalogTitle;
        RemoteHasItems = RemoteItems.Count > 0;
        if (!RemoteHasItems)
            RemoteStatus = "Nothing found.";
    }

    private void ResetRemote()
    {
        RemoteItems.Clear();
        RemoteHasItems = false;
        RemoteStatus = "";
        RemoteTitle = "";
        CanGoBack = false;
        _catalogPath.Clear();
        _catalogUrl = null;
        _catalogTitle = SelectedTab;
        RemoteIsConfigured = IsRemoteTab && _catalog.For(SelectedTab)?.IsConfigured == true;
    }

    private static string ConfigHint(string tab) => tab == "Calibre"
        ? "Set the Calibre Content Server URL in Settings."
        : "Add your Zotero user id and API key in Settings.";

    private async Task RunImportAsync(Func<Task> action)
    {
        if (IsBusy)
            return;
        try
        {
            IsBusy = true;
            StatusMessage = "";
            await action();
        }
        catch (Exception ex)
        {
            StatusMessage = $"Import failed: {ex.Message}";
        }
        finally
        {
            IsBusy = false;
        }
    }
}
