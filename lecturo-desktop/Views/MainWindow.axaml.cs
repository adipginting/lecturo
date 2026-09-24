using Avalonia.Controls;
using Avalonia.Input;
using Lecturo.ViewModels;

namespace Lecturo.Views;

public partial class MainWindow : Window
{
    public MainWindow()
    {
        InitializeComponent();
        // The global back stack (G1): Esc / Alt+Left. Bubbling, so a control that
        // handles Esc itself (a rename field, the composer) keeps it.
        AddHandler(KeyDownEvent, OnKeyDown);
    }

    private void OnKeyDown(object? sender, KeyEventArgs e)
    {
        if (DataContext is not MainViewModel vm)
            return;

        var isBack = e.Key == Key.Escape
            || (e.Key == Key.Left && e.KeyModifiers.HasFlag(KeyModifiers.Alt));

        if (isBack && vm.TryGoBack())
            e.Handled = true;
    }
}
