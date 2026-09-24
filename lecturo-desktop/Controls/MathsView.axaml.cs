using System.Text.Json;
using Avalonia;
using Avalonia.Controls;
using Lecturo.Services;

namespace Lecturo.Controls;

/// <summary>
/// One chat message with formulas in it, typeset by the bundled KaTeX in a
/// WebView. A WebView per message is more than prose is worth, so this is only
/// used when a reply actually carries maths — see <see cref="Domain.MathSplit"/>.
/// </summary>
public partial class MathsView : UserControl
{
    public static readonly StyledProperty<string?> MarkdownTextProperty =
        AvaloniaProperty.Register<MathsView, string?>(nameof(MarkdownText));

    public static readonly StyledProperty<string> TextColorProperty =
        AvaloniaProperty.Register<MathsView, string>(nameof(TextColor), "#1C1B1F");

    public static readonly StyledProperty<int> TextSizeProperty =
        AvaloniaProperty.Register<MathsView, int>(nameof(TextSize), 14);

    private string? _rendered;

    public MathsView()
    {
        InitializeComponent();
        Web.WebMessageReceived += OnWebMessage;
    }

    /// <summary>The reply, as Markdown, formulas and all.</summary>
    public string? MarkdownText
    {
        get => GetValue(MarkdownTextProperty);
        set => SetValue(MarkdownTextProperty, value);
    }

    public string TextColor
    {
        get => GetValue(TextColorProperty);
        set => SetValue(TextColorProperty, value);
    }

    public int TextSize
    {
        get => GetValue(TextSizeProperty);
        set => SetValue(TextSizeProperty, value);
    }

    protected override void OnPropertyChanged(AvaloniaPropertyChangedEventArgs change)
    {
        base.OnPropertyChanged(change);
        if (change.Property == MarkdownTextProperty
            || change.Property == TextColorProperty
            || change.Property == TextSizeProperty)
        {
            Render();
        }
    }

    private void Render()
    {
        var markdown = MarkdownText;
        if (string.IsNullOrEmpty(markdown))
            return;

        // The height depends on what is rendered, so a colour or size change has to
        // re-measure even when the text is the same.
        var key = $"{TextSize}\u001f{TextColor}\u001f{markdown}";
        if (key == _rendered)
            return;
        _rendered = key;

        try
        {
            Web.Navigate(new Uri(MathsHtml.WritePageUrl(markdown, TextColor, TextSize)));
        }
        catch
        {
            // A formula that will not render must not take the bubble down.
        }
    }

    /// <summary>KaTeX measures the page and reports the height it needs, in CSS pixels.</summary>
    private void OnWebMessage(object? sender, WebMessageReceivedEventArgs e)
    {
        try
        {
            using var json = JsonDocument.Parse(e.Body);
            if (json.RootElement.TryGetProperty("px", out var value)
                && value.TryGetDouble(out var pixels)
                && pixels > 0)
            {
                Height = pixels;
            }
        }
        catch
        {
            // Anything that is not a height report is not for us.
        }
    }
}
