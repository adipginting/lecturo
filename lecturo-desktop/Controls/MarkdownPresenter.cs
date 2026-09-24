using System.Collections.Generic;
using System.Linq;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.Documents;
using Avalonia.Media;
using Avalonia.VisualTree;
using Markdig;
using Markdig.Syntax;
using Markdig.Syntax.Inlines;

namespace Lecturo.Controls;

/// <summary>
/// Renders a Markdown string as Avalonia content. The whole chapter is a single
/// <see cref="SelectableTextBlock"/> with inline runs, rather than one block per
/// paragraph, so a drag can select across paragraphs the way a browser does.
/// </summary>
public class MarkdownPresenter : ContentControl
{
    private static readonly MarkdownPipeline Pipeline = new MarkdownPipelineBuilder()
        .UseSoftlineBreakAsHardlineBreak()
        .UseAutoLinks()
        .Build();

    private static readonly FontFamily Mono = new("monospace");

    private static readonly IBrush AccentBrush = new SolidColorBrush(Color.Parse("#6650A4"));

    public static readonly StyledProperty<string> MarkdownProperty =
        AvaloniaProperty.Register<MarkdownPresenter, string>(nameof(Markdown), string.Empty);

    public string Markdown
    {
        get => GetValue(MarkdownProperty);
        set => SetValue(MarkdownProperty, value);
    }

    protected override void OnPropertyChanged(AvaloniaPropertyChangedEventArgs change)
    {
        base.OnPropertyChanged(change);
        if (change.Property == MarkdownProperty)
            Render();
    }

    private void Render()
    {
        var text = NewText();
        var markdown = Markdown ?? string.Empty;
        if (markdown.Length > 0)
        {
            var document = Markdig.Markdown.Parse(markdown, Pipeline);
            var first = true;
            foreach (var block in document)
            {
                if (!first)
                    BlankLine(text.Inlines!);
                first = false;
                AppendBlock(text.Inlines!, block);
            }
        }
        Content = text;
    }

    /// <summary>The text the user has selected (one block, so a single value spans paragraphs).</summary>
    public string GetSelectedText()
    {
        var parts = new List<string>();
        foreach (var block in this.GetVisualDescendants().OfType<SelectableTextBlock>())
        {
            if (!string.IsNullOrEmpty(block.SelectedText))
                parts.Add(block.SelectedText);
        }
        return string.Join("\n", parts).Trim();
    }

    /// <summary>Selects the whole chapter (the pill's "Select all").</summary>
    public void SelectAll()
    {
        foreach (var block in this.GetVisualDescendants().OfType<SelectableTextBlock>())
            block.SelectAll();
    }

    /// <summary>Clears any selection.</summary>
    public void ClearSelection()
    {
        foreach (var block in this.GetVisualDescendants().OfType<SelectableTextBlock>())
            block.ClearSelection();
    }

    private SelectableTextBlock NewText(double? size = null, FontWeight? weight = null)
    {
        var text = new SelectableTextBlock { TextWrapping = TextWrapping.Wrap };
        if (FontFamily is { } family)
            text.FontFamily = family;
        text.FontSize = size ?? (FontSize > 0 ? FontSize : 14);
        if (weight.HasValue)
            text.FontWeight = weight.Value;
        return text;
    }

    private static void BlankLine(InlineCollection target)
    {
        target.Add(new LineBreak());
        target.Add(new LineBreak());
    }

    private void AppendBlock(InlineCollection target, Block block)
    {
        switch (block)
        {
            case HeadingBlock heading:
                AppendHeading(target, heading);
                break;
            case ParagraphBlock paragraph:
                if (paragraph.Inline is not null)
                    AppendInlines(target, paragraph.Inline);
                break;
            case ListBlock list:
                AppendList(target, list);
                break;
            case QuoteBlock quote:
                AppendQuote(target, quote);
                break;
            case CodeBlock code:
                AppendCode(target, code);
                break;
            case ThematicBreakBlock:
                target.Add(new Run("\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500") { Foreground = AccentBrush });
                break;
        }
    }

    private void AppendHeading(InlineCollection target, HeadingBlock heading)
    {
        var baseSize = FontSize > 0 ? FontSize : 14;
        var size = heading.Level switch
        {
            1 => baseSize + 6,
            2 => baseSize + 4,
            3 => baseSize + 2,
            _ => baseSize + 1,
        };
        var span = new Span { FontSize = size, FontWeight = FontWeight.SemiBold };
        if (heading.Inline is not null)
            AppendInlines(span.Inlines, heading.Inline);
        target.Add(span);
    }

    private void AppendList(InlineCollection target, ListBlock list)
    {
        var index = list.OrderedStart is { Length: > 0 } start && int.TryParse(start, out var parsed) ? parsed : 1;
        var first = true;
        foreach (var item in list)
        {
            if (item is not ListItemBlock listItem)
                continue;

            if (!first)
                target.Add(new LineBreak());
            first = false;

            target.Add(new Run(list.IsOrdered ? $"{index}. " : "\u2022 "));
            foreach (var inner in listItem)
                AppendBlock(target, inner);
            index++;
        }
    }

    private void AppendQuote(InlineCollection target, QuoteBlock quote)
    {
        var first = true;
        foreach (var inner in quote)
        {
            if (!first)
                target.Add(new LineBreak());
            first = false;

            target.Add(new Run("\u2502 ") { Foreground = AccentBrush });
            AppendBlock(target, inner);
        }
    }

    private static void AppendCode(InlineCollection target, CodeBlock code)
    {
        var lines = code.Lines.Lines.Take(code.Lines.Count).Select(line => line.ToString()).ToList();
        for (var i = 0; i < lines.Count; i++)
        {
            if (i > 0)
                target.Add(new LineBreak());
            target.Add(new Run(lines[i]) { FontFamily = Mono, FontSize = 12 });
        }
    }

    private static void AppendInlines(InlineCollection target, ContainerInline container)
    {
        foreach (var inline in container)
        {
            switch (inline)
            {
                case LiteralInline literal:
                    target.Add(new Run(literal.Content.ToString()));
                    break;

                case CodeInline code:
                    target.Add(new Run(code.Content) { FontFamily = Mono });
                    break;

                case EmphasisInline emphasis:
                    var span = new Span();
                    if (emphasis.DelimiterCount >= 2)
                        span.FontWeight = FontWeight.Bold;
                    else
                        span.FontStyle = FontStyle.Italic;
                    AppendInlines(span.Inlines, emphasis);
                    target.Add(span);
                    break;

                case LinkInline link:
                    var linkSpan = new Span
                    {
                        Foreground = AccentBrush,
                        TextDecorations = TextDecorations.Underline,
                    };
                    AppendInlines(linkSpan.Inlines, link);
                    target.Add(linkSpan);
                    break;

                case LineBreakInline:
                    target.Add(new LineBreak());
                    break;

                case ContainerInline nested:
                    AppendInlines(target, nested);
                    break;
            }
        }
    }
}
