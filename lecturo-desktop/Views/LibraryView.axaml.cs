using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Platform.Storage;
using Lecturo.ViewModels;

namespace Lecturo.Views;

public partial class LibraryView : UserControl
{
    public LibraryView()
    {
        InitializeComponent();
        AddHandler(DragDrop.DragOverEvent, OnDragOver);
        AddHandler(DragDrop.DropEvent, OnDrop);
    }

    private static void OnDragOver(object? sender, DragEventArgs e)
    {
        e.DragEffects = e.DataTransfer.Formats.Contains(DataFormat.File)
            ? DragDropEffects.Copy
            : DragDropEffects.None;
    }

    private void OnRemoteSearchKey(object? sender, KeyEventArgs e)
    {
        if (e.Key != Key.Enter)
            return;

        if (DataContext is LibraryViewModel vm && vm.SearchRemoteCommand.CanExecute(null))
            vm.SearchRemoteCommand.Execute(null);
    }

    private async void OnDrop(object? sender, DragEventArgs e)
    {
        if (DataContext is not LibraryViewModel vm)
            return;

        var paths = new List<string>();
        foreach (var item in e.DataTransfer.Items)
        {
            if (item.TryGetRaw(DataFormat.File) is IStorageItem storage
                && storage.TryGetLocalPath() is { } path)
            {
                paths.Add(path);
            }
        }

        if (paths.Count > 0)
            await vm.ImportPathsAsync(paths);
    }
}
