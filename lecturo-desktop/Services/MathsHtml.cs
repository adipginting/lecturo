using System.Security.Cryptography;
using System.Text;
using Lecturo.Domain;
using Lecturo.Domain.Chat;
using Lecturo.Infrastructure;
using Markdig;

namespace Lecturo.Services;

/// <summary>
/// The page the maths view is handed. Prose keeps its Markdown shapes and its
/// escaping; every formula is left as TeX in a <c>data-tex</c> attribute for the
/// page's own KaTeX to typeset, so markup never reaches the renderer as markup.
/// </summary>
public static class MathsHtml
{
    private static readonly MarkdownPipeline Pipeline = new MarkdownPipelineBuilder().Build();

    /// <summary>The reply as HTML, formulas and all.</summary>
    public static string Page(string markdown, string color = "#1C1B1F", int fontSize = 14)
    {
        var placeholders = new List<(string Tex, bool Display)>();
        var builder = new StringBuilder();

        foreach (var part in MathSplit.Split(markdown))
        {
            switch (part)
            {
                case MathPart.Text text:
                    builder.Append(text.Value);
                    break;
                case MathPart.Math math:
                    // A placeholder survives Markdig untouched, and is swapped for the
                    // real element afterwards so the markdown around it still parses.
                    builder.Append(Placeholder(placeholders.Count));
                    placeholders.Add((math.Tex, math.Display));
                    break;
            }
        }

        var body = Markdown.ToHtml(builder.ToString(), Pipeline);
        for (var i = 0; i < placeholders.Count; i++)
        {
            var (tex, display) = placeholders[i];
            body = body.Replace(Placeholder(i), Formula(tex, display), StringComparison.Ordinal);
        }

        return Wrap(body, color, fontSize);
    }

    /// <summary>
    /// Writes the page beside the assets and returns its file URL. The name is the
    /// content's hash, so the same reply reuses the same file instead of rewriting.
    /// </summary>
    public static string WritePageUrl(string markdown, string color = "#1C1B1F", int fontSize = 14)
    {
        var directory = EnsureAssets();
        var page = Page(markdown, color, fontSize);
        var name = Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(page)))[..16] + ".html";
        var path = Path.Combine(directory, name);
        if (!File.Exists(path))
            File.WriteAllText(path, page, Encoding.UTF8);
        return new Uri(path).AbsoluteUri;
    }

    /// <summary>
    /// The KaTeX files, copied out of the app's assets into the data directory on
    /// first use — beside the pages that load them, because a WebView document may
    /// reach its own directory and not much else.
    /// </summary>
    public static string EnsureAssets()
    {
        var target = Path.Combine(AppPaths.DataDir, "maths");
        var source = Path.Combine(AppContext.BaseDirectory, "Assets", "katex");
        if (!Directory.Exists(target))
            Directory.CreateDirectory(target);

        foreach (var file in Directory.GetFiles(source, "*", SearchOption.AllDirectories))
        {
            var relative = Path.GetRelativePath(source, file);
            var destination = Path.Combine(target, relative);
            if (File.Exists(destination))
                continue;
            Directory.CreateDirectory(Path.GetDirectoryName(destination)!);
            File.Copy(file, destination);
        }

        return target;
    }

    private static string Placeholder(int index) => $"LECTUROMATHS{index}PLACEHOLDER";

    private static string Formula(string tex, bool display) =>
        $"<span class=\"math\" data-tex=\"{Attribute(tex)}\" data-display=\"{(display ? "true" : "false")}\"></span>";

    private static string Attribute(string value) => value
        .Replace("&", "&amp;", StringComparison.Ordinal)
        .Replace("\"", "&quot;", StringComparison.Ordinal)
        .Replace("<", "&lt;", StringComparison.Ordinal)
        .Replace(">", "&gt;", StringComparison.Ordinal);

    private static string Wrap(string body, string color, int fontSize) => $$"""
        <!doctype html><html><head><meta charset="utf-8">
        <link rel="stylesheet" href="katex.min.css">
        <style>
          body { margin:0; padding:0; background:transparent; color:{{color}};
                 font-size:{{fontSize}}px; font-family:sans-serif; line-height:1.4;
                 word-wrap:break-word; overflow-wrap:break-word; }
          p { margin:0 0 0.5em 0; } p:last-child { margin-bottom:0; }
          h1,h2,h3,h4,h5,h6 { margin:0.4em 0 0.2em 0; font-size:1.1em; }
          ul,ol { margin:0.3em 0; padding-left:1.4em; }
          blockquote { margin:0.3em 0; padding-left:0.6em; border-left:2px solid currentColor; opacity:0.8; }
          code { font-family:monospace; }
          pre { margin:0.4em 0; white-space:pre-wrap; }
          a { color:inherit; }
          .math { }
          .math[data-display="true"] { display:block; margin:0.5em 0; text-align:center; }
        </style></head><body>{{body}}</body>
        <script src="katex.min.js"></script>
        <script>
          function report() {
            if (typeof invokeCSharpAction !== 'function') return;
            invokeCSharpAction(JSON.stringify({
              action: 'height',
              px: Math.ceil(document.body.getBoundingClientRect().height) + 1
            }));
          }
          document.querySelectorAll('.math').forEach(function (el) {
            try {
              katex.render(el.getAttribute('data-tex'), el, {
                displayMode: el.getAttribute('data-display') === 'true',
                throwOnError: false
              });
            } catch (e) {
              el.textContent = el.getAttribute('data-tex');
            }
          });
          // Display maths grows once KaTeX's fonts land, so measure again then —
          // and on every later reflow.
          if (window.ResizeObserver) new ResizeObserver(report).observe(document.body);
          if (document.fonts && document.fonts.ready) document.fonts.ready.then(report);
          window.addEventListener('load', report);
          requestAnimationFrame(report);
          report();
        </script></html>
        """;
}
