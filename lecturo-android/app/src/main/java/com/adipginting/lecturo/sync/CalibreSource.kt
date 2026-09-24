package com.adipginting.lecturo.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Calibre Content Server over OPDS. Browse starts at `<base>/opds` and
 * follows `subsection` links as sub-catalogs; entries carrying an
 * `acquisition` link are downloadable documents. Search uses the
 * OpenSearch descriptor declared in the root feed, when present.
 */
class CalibreSource(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
) : RemoteLibrarySource {

    override val isConfigured get() = baseUrl.isNotBlank()

    private val root = baseUrl.trimEnd('/') + "/opds"
    private var searchTemplate: String? = null

    override suspend fun browse(url: String?): List<RemoteItem> = withContext(Dispatchers.IO) {
        val feedUrl = url ?: root
        parseFeed(fetch(feedUrl), feedUrl)
    }

    override suspend fun search(query: String): List<RemoteItem> = withContext(Dispatchers.IO) {
        val template = searchTemplate ?: findSearchTemplate()
            ?: throw IllegalArgumentException("This server has no OPDS search endpoint")
        // Substitute before resolving: the {searchTerms} placeholder is not
        // valid in a java.net.URI, so resolve() would reject the raw template.
        val substituted = template.replace("&amp;", "&").replace(
            "{searchTerms}",
            java.net.URLEncoder.encode(query, "UTF-8"),
        )
        val searchUrl = resolveHref(root, substituted)
        parseFeed(fetch(searchUrl), searchUrl)
    }

    private fun findSearchTemplate(): String? {
        val feed = fetch(root)
        val searchHref = parseSearchLink(feed) ?: return null
        val descriptor = fetch(resolveHref(root, searchHref))
        return Regex("""<Url[^>]*template="([^"]+)"""").find(descriptor)
            ?.groupValues?.get(1)
            ?.also { searchTemplate = it }
    }

    private fun parseFeed(xml: String, feedUrl: String): List<RemoteItem> =
        calibreItems(OpdsFeed.parse(xml), feedUrl)

    private fun fetch(url: String): String {
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        response.use {
            if (!it.isSuccessful) throw IllegalArgumentException("HTTP ${it.code} from $url")
            return it.body?.string() ?: throw IllegalArgumentException("Empty response")
        }
    }

    private fun parseSearchLink(xml: String): String? =
        Regex("""<link[^>]*rel="search"[^>]*href="([^"]+)"""").find(xml)?.groupValues?.get(1)
            ?: Regex("""<link[^>]*href="([^"]+)"[^>]*rel="search"""").find(xml)?.groupValues?.get(1)
}

/**
 * One row per downloadable format. An entry can offer an EPUB and a PDF — each a
 * document of its own, so a downloaded EPUB never marks the PDF beside it — and
 * can repeat a format for mirrors, of which the first link wins. An entry with
 * only a sub-catalog link is one browsing row. The id is the entry's own where
 * it has one, plus the format, so the two rows are distinct items.
 */
internal fun calibreItems(entries: List<OpdsFeed.Entry>, feedUrl: String): List<RemoteItem> =
    entries.flatMap { entry ->
        val baseId = entry.id.takeIf { it.isNotBlank() }
        val acquisitions = entry.links
            .filter { it.first.contains("acquisition") }
            .mapNotNull { link -> formatOf(link.third, link.second)?.let { it to link.second } }
            .distinctBy { it.first }
        val subsection = entry.links.firstOrNull {
            it.first in listOf("subsection", "related", "alternate") &&
                it.third?.contains("atom") == true
        }
        val cover = entry.links.firstOrNull {
            it.first in listOf(
                "http://opds-spec.org/image/thumbnail",
                "http://opds-spec.org/image",
                "thumbnail",
            )
        }?.second?.let { resolveHref(feedUrl, it) }
        when {
            acquisitions.isNotEmpty() -> acquisitions.map { (format, href) ->
                RemoteItem(
                    source = SOURCE_CALIBRE,
                    id = (baseId ?: resolveHref(feedUrl, href)) + "#" + format,
                    title = entry.title,
                    author = entry.author,
                    downloadUrl = resolveHref(feedUrl, href),
                    coverUrl = cover,
                    format = format,
                )
            }

            subsection != null -> listOf(
                RemoteItem(
                    source = SOURCE_CALIBRE,
                    id = baseId ?: resolveHref(feedUrl, subsection.second),
                    title = entry.title,
                    author = entry.author,
                    childrenUrl = resolveHref(feedUrl, subsection.second),
                    coverUrl = cover,
                ),
            )

            else -> emptyList()
        }
    }

private fun formatOf(type: String?, href: String): String? {
    val mime = type?.substringBefore(';')?.trim()?.lowercase()
    return when {
        mime == "application/pdf" -> "pdf"
        mime == "application/epub+zip" -> "epub"
        href.substringBefore('?').endsWith(".pdf", true) -> "pdf"
        href.substringBefore('?').endsWith(".epub", true) -> "epub"
        else -> null
    }
}
