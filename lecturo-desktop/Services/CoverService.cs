using System.IO.Compression;
using System.Text.RegularExpressions;
using System.Xml.Linq;
using Lecturo.Domain;
using Lecturo.Infrastructure;

namespace Lecturo.Services;

/// <summary>
/// Extracts and caches a document cover. EPUB: the cover image named by the OPF
/// (or the one marked <c>properties="cover-image"</c>). PDF first-page rendering
/// is not available here, so PDFs keep their initial tile.
/// </summary>
public sealed partial class CoverService
{
    public string CoverPath(string documentId) => Path.Combine(AppPaths.DataDir, "covers", documentId);

    /// <summary>Returns the cached cover path, extracting it on first use; null if none.</summary>
    public string? EnsureCover(Document doc, string filePath)
    {
        var target = CoverPath(doc.Id);
        if (File.Exists(target))
            return target;

        if (doc.Format != DocumentFormat.Epub || !File.Exists(filePath))
            return null;

        try
        {
            var bytes = ExtractEpubCover(filePath);
            if (bytes is null)
                return null;
            Directory.CreateDirectory(Path.GetDirectoryName(target)!);
            File.WriteAllBytes(target, bytes);
            return target;
        }
        catch
        {
            return null;
        }
    }

    private static byte[]? ExtractEpubCover(string epubPath)
    {
        using var zip = ZipFile.OpenRead(epubPath);

        var container = zip.GetEntry("META-INF/container.xml");
        if (container is null)
            return null;

        var containerXml = ReadAll(container);
        var opfPath = RootfileRegex().Match(containerXml).Groups[1].Value;
        if (opfPath.Length == 0)
            return null;

        var opfEntry = zip.GetEntry(opfPath);
        if (opfEntry is null)
            return null;

        var opfDir = Path.GetDirectoryName(opfPath)?.Replace('\\', '/') ?? "";
        var opf = XDocument.Parse(ReadAll(opfEntry));
        if (opf.Root is null)
            return null;
        var ns = opf.Root.Name.Namespace;

        var coverId = opf.Descendants(ns + "meta")
            .FirstOrDefault(m => (string?)m.Attribute("name") == "cover")
            ?.Attribute("content")?.Value;

        var item = opf.Descendants(ns + "item").FirstOrDefault(i => coverId is not null && (string?)i.Attribute("id") == coverId)
            ?? opf.Descendants(ns + "item").FirstOrDefault(i => ((string?)i.Attribute("properties") ?? "").Contains("cover-image", StringComparison.OrdinalIgnoreCase))
            ?? opf.Descendants(ns + "item").FirstOrDefault(i =>
                ((string?)i.Attribute("media-type") ?? "").StartsWith("image/", StringComparison.OrdinalIgnoreCase) &&
                ((string?)i.Attribute("href") ?? "").Contains("cover", StringComparison.OrdinalIgnoreCase));

        var href = (string?)item?.Attribute("href");
        if (string.IsNullOrWhiteSpace(href))
            return null;

        var entryName = Uri.UnescapeDataString(
            (string.IsNullOrEmpty(opfDir) ? href : opfDir + "/" + href).Replace('\\', '/'));
        var imageEntry = zip.GetEntry(entryName)
            ?? zip.Entries.FirstOrDefault(e => e.FullName.EndsWith(href, StringComparison.OrdinalIgnoreCase));
        if (imageEntry is null)
            return null;

        using var stream = imageEntry.Open();
        using var memory = new MemoryStream();
        stream.CopyTo(memory);
        return memory.ToArray();
    }

    private static string ReadAll(ZipArchiveEntry entry)
    {
        using var stream = entry.Open();
        using var reader = new StreamReader(stream);
        return reader.ReadToEnd();
    }

    [GeneratedRegex(@"full-path\s*=\s*[""']([^""']+)[""']", RegexOptions.IgnoreCase)]
    private static partial Regex RootfileRegex();
}
