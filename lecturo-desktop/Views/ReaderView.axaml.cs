using System;
using System.ComponentModel;
using System.IO;
using System.Text.Json;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Interactivity;
using Avalonia.Markup.Xaml;
using Avalonia.Media.Imaging;
using Avalonia.VisualTree;
using Lecturo.Controls;
using Lecturo.Domain.Reading;
using Lecturo.Infrastructure;
using Lecturo.Services;
using Lecturo.ViewModels;

namespace Lecturo.Views;

public partial class ReaderView : UserControl
{
    private readonly NativeWebView? _web;
    private readonly MarkdownPresenter? _epub;
    private readonly ScrollViewer? _reading;
    private readonly ScrollViewer? _pdfScroll;
    private readonly Border? _pill;
    private readonly Grid? _readingGrid;
    private readonly Grid? _cropLayer;
    private readonly Border? _cropRect;
    private bool _cropping;
    private bool _dragging;
    private Point _cropStart;
    private string _selectedText = "";
    private ReaderViewModel? _vm;

    public ReaderView()
    {
        AvaloniaXamlLoader.Load(this);
        _web = this.FindControl<NativeWebView>("Web");
        _epub = this.FindControl<MarkdownPresenter>("EpubContent");
        _reading = this.FindControl<ScrollViewer>("ReadingScroll");
        _pdfScroll = this.FindControl<ScrollViewer>("PdfScroll");
        _pill = this.FindControl<Border>("SelectionPill");
        _readingGrid = this.FindControl<Grid>("ReadingGrid");
        _cropLayer = this.FindControl<Grid>("CropLayer");
        _cropRect = this.FindControl<Border>("CropRect");
        if (_web is not null)
            _web.WebMessageReceived += OnWebMessage;
        // SelectableTextBlock handles the pointer itself, so the release may never
        // bubble; listen regardless, or the selection pill never appears.
        _epub?.AddHandler(PointerReleasedEvent, OnReaderPointerReleased, RoutingStrategies.Bubble, handledEventsToo: true);
        // Capture the selection on the way *down*, before any button press moves
        // focus and collapses it.
        AddHandler(PointerPressedEvent, OnReaderPointerPressed, RoutingStrategies.Tunnel, handledEventsToo: true);
        // A drag on a stacked PDF page raises its selection up to here.
        AddHandler(PdfPageView.SelectionMadeEvent, OnPdfPageSelection, RoutingStrategies.Bubble, handledEventsToo: true);
        DataContextChanged += (_, _) => Attach();
        Loaded += (_, _) =>
        {
            Render();
            Focus();
        };
    }

    /// <summary>The PDF viewer posts {action, text} to us (kept for parity).</summary>
    private void OnWebMessage(object? sender, WebMessageReceivedEventArgs e)
    {
        if (_vm is null || string.IsNullOrWhiteSpace(e.Body))
            return;

        try
        {
            using var document = JsonDocument.Parse(e.Body);
            var root = document.RootElement;
            var action = root.TryGetProperty("action", out var a) ? a.GetString() : null;
            var text = root.TryGetProperty("text", out var t) ? t.GetString() ?? string.Empty : string.Empty;

            switch (action)
            {
                case "ask":
                    _vm.AskAboutSelection(text);
                    break;
            }
        }
        catch
        {
            // Ignore malformed messages.
        }
    }

    private void Attach()
    {
        if (_vm is not null)
            _vm.PropertyChanged -= OnViewModelChanged;

        _vm = DataContext as ReaderViewModel;

        if (_vm is not null)
        {
            _vm.PropertyChanged += OnViewModelChanged;
            _vm.DismissOverlays = TryDismissOverlays;
        }

        Render();
    }

    /// <summary>
    /// The reader's first back step: a crop in progress, then a selection pill.
    /// Returns whether there was an overlay to dismiss.
    /// </summary>
    private bool TryDismissOverlays()
    {
        if (_cropping)
        {
            ExitCrop();
            return true;
        }
        if (_pill?.IsVisible == true)
        {
            HidePill();
            return true;
        }
        return false;
    }

    // ---- PDF page selection (rendered pages) ----

    /// <summary>A drag on one stacked PDF page selected text; offer the pill.</summary>
    private void OnPdfPageSelection(object? sender, PageSelectionEventArgs e)
    {
        if (string.IsNullOrWhiteSpace(e.Text))
            return;

        _selectedText = e.Text;
        if (_pill is null || _readingGrid is null)
            return;

        var point = (e.Source as Visual)?.TranslatePoint(e.Position, _readingGrid) ?? new Point(12, 12);
        _pill.Margin = new Thickness(Math.Max(12, point.X - 150), Math.Max(8, point.Y + 16), 0, 0);
        _pill.IsVisible = true;
    }

    private void OnViewModelChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName is nameof(ReaderViewModel.SourceUrl)
            or nameof(ReaderViewModel.UsesWebView)
            or null)
        {
            Render();
        }
    }

    private void Render()
    {
        // EPUB is rendered natively by MarkdownPresenter; only PDF uses the WebView.
        if (_web is null || _vm is null || !_vm.UsesWebView)
            return;

        try
        {
            if (!string.IsNullOrEmpty(_vm.SourceUrl))
                _web.Navigate(new Uri(_vm.SourceUrl));
        }
        catch
        {
            // The surface stays blank rather than tearing the UI down.
        }
    }

    /// <summary>
    /// Remembers the current selection the instant the pointer goes down. Pressing
    /// a pill button moves focus and collapses the native selection, so this is the
    /// last chance to read it.
    /// </summary>
    private void OnReaderPointerPressed(object? sender, PointerPressedEventArgs e)
    {
        var selected = _epub?.GetSelectedText();
        if (!string.IsNullOrWhiteSpace(selected))
            _selectedText = selected;
    }

    /// <summary>Shows the floating pill just under a fresh selection.</summary>
    private void OnReaderPointerReleased(object? sender, PointerReleasedEventArgs e)
    {
        if (_vm is null || _vm.UsesWebView || _pill is null || _readingGrid is null)
            return;

        var selected = _epub?.GetSelectedText() ?? "";
        if (string.IsNullOrWhiteSpace(selected))
        {
            HidePill();
            return;
        }

        // Remember it: pressing a pill button moves focus and collapses the native
        // selection, so Ask AI / Copy must read the text captured here.
        _selectedText = selected;

        var position = e.GetPosition(_readingGrid);
        _pill.Margin = new Thickness(Math.Max(12, position.X - 150), Math.Max(8, position.Y + 16), 0, 0);
        _pill.IsVisible = true;
    }

    private void OnReadingScroll(object? sender, ScrollChangedEventArgs e) => HidePill();

    private async void OnPillCopy(object? sender, RoutedEventArgs e)
    {
        var text = _epub?.GetSelectedText();
        if (string.IsNullOrWhiteSpace(text))
            text = _selectedText;

        if (!string.IsNullOrEmpty(text) && TopLevel.GetTopLevel(this)?.Clipboard is { } clipboard)
        {
            var data = new DataTransfer();
            data.Add(DataTransferItem.CreateText(text));
            await clipboard.SetDataAsync(data);
        }
        HidePill();
    }

    private void OnPillSelectAll(object? sender, RoutedEventArgs e) => _epub?.SelectAll();

    private void HidePill()
    {
        if (_pill is not null)
            _pill.IsVisible = false;
    }

    private void OnReaderKeyDown(object? sender, KeyEventArgs e)
    {
        if (e.KeyModifiers.HasFlag(KeyModifiers.Control) && e.Key == Key.Enter)
        {
            _ = AskSelectionAsync();
            e.Handled = true;
        }
    }

    private async System.Threading.Tasks.Task AskSelectionAsync()
    {
        var text = await ReadSelectionAsync();
        if (string.IsNullOrWhiteSpace(text))
            text = _selectedText;
        _vm?.AskAboutSelection(text);
    }

    private async void OnAskSelection(object? sender, RoutedEventArgs e)
    {
        // Same path as Ctrl+Enter: it falls back to the selection captured on
        // pointer-down, which is all that survives a button press.
        await AskSelectionAsync();
    }

    /// <summary>Enters crop mode: the user drags a rectangle, then captures it.</summary>
    private void OnScreenshot(object? sender, RoutedEventArgs e)
    {
        if (_vm is null)
            return;

        if (_vm.UsesWebView)
        {
            _vm.StatusMessage = "Screenshots aren't available for scanned PDFs yet.";
            return;
        }

        _cropping = true;
        if (_cropLayer is not null)
            _cropLayer.IsVisible = true;
        if (_cropRect is not null)
            _cropRect.IsVisible = false;
    }

    private void OnCropCancel(object? sender, RoutedEventArgs e) => ExitCrop();

    private void OnCropPressed(object? sender, PointerPressedEventArgs e)
    {
        if (!_cropping || _cropLayer is null || _cropRect is null)
            return;

        // A press on the Capture / Cancel buttons is theirs, not the start of a drag.
        if (e.Source is Visual source && source.FindAncestorOfType<Button>() is not null)
            return;

        _dragging = true;
        _cropStart = e.GetPosition(_cropLayer);
        _cropRect.Margin = new Thickness(_cropStart.X, _cropStart.Y, 0, 0);
        _cropRect.Width = 0;
        _cropRect.Height = 0;
        _cropRect.IsVisible = true;
        e.Pointer.Capture(_cropLayer);
    }

    private void OnCropMoved(object? sender, PointerEventArgs e)
    {
        if (!_cropping || !_dragging || _cropLayer is null || _cropRect is null)
            return;

        var point = e.GetPosition(_cropLayer);
        var x = Math.Min(point.X, _cropStart.X);
        var y = Math.Min(point.Y, _cropStart.Y);
        _cropRect.Margin = new Thickness(x, y, 0, 0);
        _cropRect.Width = Math.Max(1, Math.Abs(point.X - _cropStart.X));
        _cropRect.Height = Math.Max(1, Math.Abs(point.Y - _cropStart.Y));
    }

    private void OnCropReleased(object? sender, PointerReleasedEventArgs e)
    {
        _dragging = false;
        e.Pointer.Capture(null);
    }

    private void OnCropCapture(object? sender, RoutedEventArgs e)
    {
        if (_vm is null || _cropRect is null)
            return;

        if (_cropRect.Width < 6 || _cropRect.Height < 6)
        {
            _vm.StatusMessage = "Drag a rectangle over the page first.";
            return;
        }

        // Crop the surface that is actually showing: the stacked PDF pages, or the
        // paged reading surface.
        var surface = _vm.IsPdfPages && _pdfScroll is not null ? (Control)_pdfScroll : _reading;
        if (surface is null)
        {
            _vm.StatusMessage = "Nothing to capture yet.";
            return;
        }

        try
        {
            var rect = new Rect(_cropRect.Margin.Left, _cropRect.Margin.Top, _cropRect.Width, _cropRect.Height);
            var full = new RenderTargetBitmap(
                new PixelSize(Math.Max(1, (int)surface.Bounds.Width), Math.Max(1, (int)surface.Bounds.Height)),
                new Vector(96, 96));
            full.Render(surface);

            var crop = new RenderTargetBitmap(
                new PixelSize(Math.Max(1, (int)rect.Width), Math.Max(1, (int)rect.Height)),
                new Vector(96, 96));
            using (var context = crop.CreateDrawingContext())
                context.DrawImage(full, rect, new Rect(0, 0, rect.Width, rect.Height));

            var directory = Path.Combine(AppPaths.DataDir, "chat_images");
            Directory.CreateDirectory(directory);
            var path = Path.Combine(directory, Guid.NewGuid().ToString("N") + ".png");
            crop.Save(path);

            ExitCrop();
            _vm.AskAboutImage(path);
        }
        catch (Exception ex)
        {
            _vm.StatusMessage = $"Could not capture the page: {ex.Message}";
        }
    }

    private void ExitCrop()
    {
        _cropping = false;
        _dragging = false;
        if (_cropLayer is not null)
            _cropLayer.IsVisible = false;
        if (_cropRect is not null)
            _cropRect.IsVisible = false;
    }

    private async System.Threading.Tasks.Task<string> ReadSelectionAsync()
    {
        if (_vm is null)
            return string.Empty;

        if (_vm.UsesWebView)
        {
            if (_web is null)
                return string.Empty;
            try
            {
                return await _web.InvokeScript(
                    "(window.getSelection && window.getSelection().toString()) || ''") ?? string.Empty;
            }
            catch
            {
                return string.Empty;
            }
        }

        return _epub?.GetSelectedText() ?? string.Empty;
    }
}
