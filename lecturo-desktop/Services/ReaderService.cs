using System.IO.Compression;
using System.Text.RegularExpressions;
using Lecturo.Application.Library;
using Lecturo.Application.Reading;
using Lecturo.Domain;
using Lecturo.Domain.Reading;
using Lecturo.Infrastructure;
using ReverseMarkdown;

namespace Lecturo.Services;

/// <summary>One navigable division of a document: Markdown, or a rendered page image.</summary>
public sealed record ReaderChapter(
    string Title,
    string Markdown,
    byte[]? ImagePng = null,
    IReadOnlyList<PdfWord>? Words = null,
    int PixelWidth = 0,
    int PixelHeight = 0)
{
    public bool IsImage => ImagePng is { Length: > 0 };

    /// <summary>A rendered PDF page whose words can be selected over the image.</summary>
    public bool IsSelectablePage => IsImage && Words is { Count: > 0 };
}

/// <summary>The material a reader screen needs: Markdown chapters, or a PDF file.</summary>
public sealed record ReaderContent(
    RenderMode Mode,
    string SourcePath,
    IReadOnlyList<ReaderChapter> Chapters,
    int StartIndex);

/// <summary>
/// Turns a stored document into renderable content. EPUB is unzipped, its OPF
/// spine honoured, and each spine item converted to Markdown for the native
/// reader surface; a PDF is rendered to page images with its words, so the page
/// looks as any viewer shows it and can still be selected.
/// </summary>
public sealed partial class ReaderService
{
    private readonly LibraryService _library;
    private readonly IPdfRenderer _pdf;
    private readonly Converter _markdown = new();

    public ReaderService(LibraryService library, IPdfRenderer pdf)
    {
        _library = library;
        _pdf = pdf;
    }

    public ReaderContent Load(Document doc)
    {
        var path = _library.GetPath(doc);
        if (!File.Exists(path))
            throw new FileNotFoundException("The document's file is missing from the library.", path);

        var start = int.TryParse(doc.LastLocator, out var index) ? index : 0;

        if (doc.Format == DocumentFormat.Pdf)
        {
            var pages = TryRenderPdf(path);
            if (pages is { Count: > 0 })
            {
                if (start < 0 || start >= pages.Count)
                    start = 0;
                return new ReaderContent(RenderMode.PdfPages, path, pages, start);
            }

            // Rendering failed outright; the platform viewer is the last resort.
            return new ReaderContent(RenderMode.Pdf, path, [], 0);
        }

        var chapters = LoadEpub(doc.Id, path);
        if (start < 0 || start >= chapters.Count)
            start = 0;
        return new ReaderContent(RenderMode.Epub, path, chapters, start);
    }

    /// <summary>Renders every page with the PDF engine, or null if it cannot.</summary>
    private List<ReaderChapter>? TryRenderPdf(string path)
    {
        try
        {
            return _pdf.Render(path, 200)
                .Select(page => new ReaderChapter(
                    $"Page {page.Number}",
                    string.Empty,
                    page.Png,
                    page.Words,
                    page.PixelWidth,
                    page.PixelHeight))
                .ToList();
        }
        catch
        {
            return null;
        }
    }

    private List<ReaderChapter> LoadEpub(long documentId, string epubPath)
    {
        var root = Path.Combine(AppPaths.DataDir, "cache", $"epub-{documentId}");
        if (Directory.Exists(root))
            Directory.Delete(root, recursive: true);
        Directory.CreateDirectory(root);
        ZipFile.ExtractToDirectory(epubPath, root);

        var opfPath = FindOpfPath(root)
            ?? throw new InvalidOperationException("This EPUB has no package document.");
        var opfDir = Path.GetDirectoryName(opfPath)!;
        var opf = File.ReadAllText(opfPath);

        var manifest = ParseManifest(opf);
        var spine = ParseSpine(opf);

        var chapters = new List<ReaderChapter>();
        foreach (var idref in spine)
        {
            if (!manifest.TryGetValue(idref, out var href))
                continue;

            var file = Path.GetFullPath(Path.Combine(opfDir, Uri.UnescapeDataString(href)));
            if (!File.Exists(file))
                continue;

            var raw = File.ReadAllText(file);
            var body = ExtractBody(raw);
            var markdown = _markdown.Convert(body).Trim();
            var title = ExtractTitle(raw) ?? $"Chapter {chapters.Count + 1}";
            chapters.Add(new ReaderChapter(title, markdown));
        }

        // Cover/title pages carry no prose; open at the first chapter that does.
        var withText = chapters.Where(c => !string.IsNullOrWhiteSpace(c.Markdown)).ToList();
        return withText.Count > 0 ? withText : chapters;
    }

    private static string? FindOpfPath(string root)
    {
        var container = Path.Combine(root, "META-INF", "container.xml");
        if (!File.Exists(container))
            return null;
        var match = RootfileRegex().Match(File.ReadAllText(container));
        return match.Success ? Path.Combine(root, Uri.UnescapeDataString(match.Groups[1].Value)) : null;
    }

    private static Dictionary<string, string> ParseManifest(string opf)
    {
        var result = new Dictionary<string, string>();
        foreach (Match item in ItemRegex().Matches(opf))
        {
            var id = Attr(item.Value, "id");
            var href = Attr(item.Value, "href");
            if (id is not null && href is not null)
                result[id] = href;
        }
        return result;
    }

    private static List<string> ParseSpine(string opf)
    {
        var result = new List<string>();
        foreach (Match item in ItemrefRegex().Matches(opf))
        {
            var idref = Attr(item.Value, "idref");
            if (idref is not null)
                result.Add(idref);
        }
        return result;
    }

    private static string ExtractBody(string html)
    {
        var match = BodyRegex().Match(html);
        var body = match.Success ? match.Groups[1].Value : html;
        body = ScriptRegex().Replace(body, string.Empty);
        body = StyleRegex().Replace(body, string.Empty);
        body = LinkRegex().Replace(body, string.Empty);
        // Images can't be shown on the native surface; drop them rather than
        // leaving broken Markdown syntax in the prose.
        body = ImgRegex().Replace(body, string.Empty);
        return body.Trim();
    }

    private static string? ExtractTitle(string html)
    {
        foreach (var regex in new[] { H1Regex(), H2Regex(), TitleRegex() })
        {
            var match = regex.Match(html);
            if (match.Success)
            {
                var text = StripTagsRegex().Replace(match.Groups[1].Value, string.Empty).Trim();
                if (text.Length > 0)
                    return System.Net.WebUtility.HtmlDecode(text);
            }
        }
        return null;
    }

    private static string? Attr(string tag, string name)
    {
        var match = Regex.Match(tag, $@"\b{name}\s*=\s*[""']([^""']*)[""']", RegexOptions.IgnoreCase);
        return match.Success ? match.Groups[1].Value : null;
    }

    [GeneratedRegex(@"<rootfile[^>]*full-path\s*=\s*[""']([^""']+)[""']", RegexOptions.IgnoreCase)]
    private static partial Regex RootfileRegex();

    [GeneratedRegex(@"<item\b[^>]*>", RegexOptions.IgnoreCase)]
    private static partial Regex ItemRegex();

    [GeneratedRegex(@"<itemref\b[^>]*>", RegexOptions.IgnoreCase)]
    private static partial Regex ItemrefRegex();

    [GeneratedRegex(@"<body[^>]*>(.*?)</body>", RegexOptions.IgnoreCase | RegexOptions.Singleline)]
    private static partial Regex BodyRegex();

    [GeneratedRegex(@"<script\b[^>]*>.*?</script>", RegexOptions.IgnoreCase | RegexOptions.Singleline)]
    private static partial Regex ScriptRegex();

    [GeneratedRegex(@"<style\b[^>]*>.*?</style>", RegexOptions.IgnoreCase | RegexOptions.Singleline)]
    private static partial Regex StyleRegex();

    [GeneratedRegex(@"<link\b[^>]*>", RegexOptions.IgnoreCase)]
    private static partial Regex LinkRegex();

    [GeneratedRegex(@"<img\b[^>]*>", RegexOptions.IgnoreCase)]
    private static partial Regex ImgRegex();

    [GeneratedRegex(@"<h1[^>]*>(.*?)</h1>", RegexOptions.IgnoreCase | RegexOptions.Singleline)]
    private static partial Regex H1Regex();

    [GeneratedRegex(@"<h2[^>]*>(.*?)</h2>", RegexOptions.IgnoreCase | RegexOptions.Singleline)]
    private static partial Regex H2Regex();

    [GeneratedRegex(@"<title[^>]*>(.*?)</title>", RegexOptions.IgnoreCase | RegexOptions.Singleline)]
    private static partial Regex TitleRegex();

    [GeneratedRegex(@"<[^>]+>")]
    private static partial Regex StripTagsRegex();
}
