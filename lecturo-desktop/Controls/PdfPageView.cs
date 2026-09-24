using Avalonia;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Interactivity;
using Avalonia.Media;
using Avalonia.Media.Imaging;
using Lecturo.Domain.Reading;

namespace Lecturo.Controls;

/// <summary>Text selected on a rendered PDF page, with where the drag ended.</summary>
public sealed class PageSelectionEventArgs : RoutedEventArgs
{
    public string Text { get; }

    public Point Position { get; }

    public PageSelectionEventArgs(RoutedEvent routedEvent, string text, Point position)
        : base(routedEvent)
    {
        Text = text;
        Position = position;
    }
}

/// <summary>
/// One rendered PDF page: the page image with a drag-to-select layer over its
/// words. Raises <see cref="SelectionMadeEvent"/> up the tree when a drag ends on a
/// non-empty selection, so the reader can offer Ask AI without knowing about PDFs.
/// </summary>
public sealed class PdfPageView : UserControl
{
    public static readonly StyledProperty<Bitmap?> ImageProperty =
        AvaloniaProperty.Register<PdfPageView, Bitmap?>(nameof(Image));

    public static readonly StyledProperty<IReadOnlyList<PdfWord>?> WordsProperty =
        AvaloniaProperty.Register<PdfPageView, IReadOnlyList<PdfWord>?>(nameof(Words));

    public static readonly StyledProperty<int> PagePixelWidthProperty =
        AvaloniaProperty.Register<PdfPageView, int>(nameof(PagePixelWidth));

    public static readonly StyledProperty<int> PagePixelHeightProperty =
        AvaloniaProperty.Register<PdfPageView, int>(nameof(PagePixelHeight));

    public static readonly RoutedEvent<PageSelectionEventArgs> SelectionMadeEvent =
        RoutedEvent.Register<PdfPageView, PageSelectionEventArgs>(nameof(SelectionMade), RoutingStrategies.Bubble);

    public event EventHandler<PageSelectionEventArgs> SelectionMade
    {
        add => AddHandler(SelectionMadeEvent, value);
        remove => RemoveHandler(SelectionMadeEvent, value);
    }

    private readonly Grid _grid = new();
    private readonly Image _image = new() { Stretch = Stretch.Uniform };

    private readonly Canvas _canvas = new() { IsHitTestVisible = false };
    private readonly Border _selection = new()
    {
        IsVisible = false,
        Background = new SolidColorBrush(Color.Parse("#5533A0FF")),
        BorderBrush = new SolidColorBrush(Color.Parse("#6650A4")),
        BorderThickness = new Thickness(1),
        CornerRadius = new CornerRadius(2),
    };

    private bool _dragging;
    private string _current = "";
    private (double X, double Y) _start;

    public PdfPageView()
    {
        _canvas.Children.Add(_selection);
        _grid.Children.Add(_image);
        _grid.Children.Add(_canvas);
        Content = _grid;

        AddHandler(PointerPressedEvent, OnPressed, RoutingStrategies.Tunnel, handledEventsToo: true);
        AddHandler(PointerMovedEvent, OnMoved, RoutingStrategies.Tunnel, handledEventsToo: true);
        AddHandler(PointerReleasedEvent, OnReleased, RoutingStrategies.Bubble, handledEventsToo: true);
    }

    public Bitmap? Image { get => GetValue(ImageProperty); set => SetValue(ImageProperty, value); }

    public IReadOnlyList<PdfWord>? Words { get => GetValue(WordsProperty); set => SetValue(WordsProperty, value); }

    public int PagePixelWidth { get => GetValue(PagePixelWidthProperty); set => SetValue(PagePixelWidthProperty, value); }

    public int PagePixelHeight { get => GetValue(PagePixelHeightProperty); set => SetValue(PagePixelHeightProperty, value); }

    protected override void OnPropertyChanged(AvaloniaPropertyChangedEventArgs change)
    {
        base.OnPropertyChanged(change);
        if (change.Property == ImageProperty)
            _image.Source = Image;
    }

    private (double Width, double Height, double OffsetX, double OffsetY) Frame()
    {
        var size = _grid.Bounds.Size;
        var width = PagePixelWidth;
        var height = PagePixelHeight;
        if (width <= 0 || height <= 0 || size.Width <= 0 || size.Height <= 0)
            return (0, 0, 0, 0);

        var scale = Math.Min(size.Width / width, size.Height / height);
        var drawnWidth = width * scale;
        var drawnHeight = height * scale;
        return (drawnWidth, drawnHeight, (size.Width - drawnWidth) / 2, (size.Height - drawnHeight) / 2);
    }

    private (double X, double Y) Normalize(Point point)
    {
        var (width, height, offsetX, offsetY) = Frame();
        return width <= 0 || height <= 0 ? (0, 0) : ((point.X - offsetX) / width, (point.Y - offsetY) / height);
    }

    private void OnPressed(object? sender, PointerPressedEventArgs e)
    {
        if (Words is not { Count: > 0 })
            return;
        if (!e.GetCurrentPoint(this).Properties.IsLeftButtonPressed)
            return;

        _dragging = true;
        _current = "";
        _start = Normalize(e.GetPosition(this));
        _selection.IsVisible = false;
        e.Pointer.Capture(this);
    }

    private void OnMoved(object? sender, PointerEventArgs e)
    {
        if (!_dragging || Words is not { Count: > 0 })
            return;

        var point = Normalize(e.GetPosition(this));
        var (width, height, offsetX, offsetY) = Frame();
        if (width <= 0 || height <= 0)
            return;

        var left = Math.Min(_start.X, point.X);
        var right = Math.Max(_start.X, point.X);
        var top = Math.Min(_start.Y, point.Y);
        var bottom = Math.Max(_start.Y, point.Y);

        Canvas.SetLeft(_selection, offsetX + left * width);
        Canvas.SetTop(_selection, offsetY + top * height);
        _selection.Width = Math.Max(0, (right - left) * width);
        _selection.Height = Math.Max(0, (bottom - top) * height);

        _current = PdfSelection.TextIn(Words, _start.X, _start.Y, point.X, point.Y);
        _selection.IsVisible = !string.IsNullOrWhiteSpace(_current);
    }

    private void OnReleased(object? sender, PointerReleasedEventArgs e)
    {
        if (!_dragging)
            return;
        _dragging = false;
        e.Pointer.Capture(null);

        if (!string.IsNullOrWhiteSpace(_current))
            RaiseEvent(new PageSelectionEventArgs(SelectionMadeEvent, _current, e.GetPosition(this)));
    }
}
