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
        OpdsFeed.parse(xml).mapNotNull { entry ->
            val acquisition = entry.links.firstOrNull { it.first.contains("acquisition") }
            val subsection = entry.links.firstOrNull {
                it.first in listOf("subsection", "related", "alternate") &&
                    it.third?.contains("atom") == true
            }
            when {
                acquisition != null -> RemoteItem(
                    id = entry.id,
                    title = entry.title,
                    author = entry.author,
                    downloadUrl = resolveHref(feedUrl, acquisition.second),
                    format = formatOf(acquisition.third, acquisition.second),
                )
                subsection != null -> RemoteItem(
                    id = entry.id,
                    title = entry.title,
                    author = entry.author,
                    childrenUrl = resolveHref(feedUrl, subsection.second),
                )
                else -> null
            }
        }

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
}
