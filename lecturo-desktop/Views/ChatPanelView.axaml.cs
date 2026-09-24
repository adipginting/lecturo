using System.Collections.Specialized;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Interactivity;
using Avalonia.Markup.Xaml;
using Avalonia.Threading;
using Lecturo.ViewModels;

namespace Lecturo.Views;

public partial class ChatPanelView : UserControl
{
    private readonly ScrollViewer? _scroll;
    private readonly TextBox? _composer;
    private ChatPanelViewModel? _vm;

    public ChatPanelView()
    {
        AvaloniaXamlLoader.Load(this);
        _scroll = this.FindControl<ScrollViewer>("MessagesScroll");
        _composer = this.FindControl<TextBox>("ComposerBox");
        // Tunnel so Enter is caught before the TextBox's own AcceptsReturn handling.
        _composer?.AddHandler(KeyDownEvent, OnComposerKeyDown, RoutingStrategies.Tunnel, handledEventsToo: true);
        DataContextChanged += (_, _) => Attach();
    }

    private void Attach()
    {
        if (_vm is not null)
            _vm.Messages.CollectionChanged -= OnMessagesChanged;

        _vm = DataContext as ChatPanelViewModel;

        if (_vm is not null)
            _vm.Messages.CollectionChanged += OnMessagesChanged;
    }

    private void OnMessagesChanged(object? sender, NotifyCollectionChangedEventArgs e)
    {
        Dispatcher.UIThread.Post(() => _scroll?.ScrollToEnd(), DispatcherPriority.Background);
    }

    private void OnComposerKeyDown(object? sender, KeyEventArgs e)
    {
        // Enter sends; Shift+Enter stays a newline (the TextBox's AcceptsReturn).
        if (e.Key != Key.Enter || e.KeyModifiers.HasFlag(KeyModifiers.Shift))
            return;

        e.Handled = true;
        if (_vm?.SendCommand.CanExecute(null) == true)
            _vm.SendCommand.Execute(null);
    }
}
