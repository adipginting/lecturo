package com.adipginting.lecturo.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Zotero Web API. Lists the user's top-level PDF/EPUB attachments; downloads
 * go through `/items/<key>/file` with the API key header.
 * [apiBase] is overridable for tests and self-hosted proxies.
 */
class ZoteroSource(
    private val userId: String,
    private val apiKey: String,
    private val apiBase: String = "https://api.zotero.org",
    private val client: OkHttpClient = OkHttpClient(),
) : RemoteLibrarySource {

    override val isConfigured get() = userId.isNotBlank() && apiKey.isNotBlank()

    private val json = Json { ignoreUnknownKeys = true }
    private val base = apiBase.trimEnd('/')

    override suspend fun browse(url: String?): List<RemoteItem> = withContext(Dispatchers.IO) {
        fetchItems("$base/users/$userId/items?itemType=attachment&limit=100&sort=dateModified&direction=desc")
    }

    override suspend fun search(query: String): List<RemoteItem> = withContext(Dispatchers.IO) {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        fetchItems("$base/users/$userId/items?itemType=attachment&limit=100&q=$q&qmode=everything")
    }

    private fun fetchItems(url: String): List<RemoteItem> {
        val request = Request.Builder().url(url)
            .header("Zotero-API-Key", apiKey)
            .header("Zotero-API-Version", "3")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalArgumentException("HTTP ${response.code} from Zotero")
            val body = response.body?.string() ?: throw IllegalArgumentException("Empty response")
            return parseItems(body)
        }
    }

    internal fun parseItems(body: String): List<RemoteItem> =
        json.decodeFromString<List<ZoteroItem>>(body).mapNotNull { item ->
            val data = item.data
            val format = formatOf(data.contentType, data.filename)
                ?: return@mapNotNull null
            val title = data.title?.takeIf { it.isNotBlank() }
                ?: data.filename?.substringBeforeLast('.')
                ?: item.key
            RemoteItem(
                id = item.key,
                title = title,
                downloadUrl = "$base/users/$userId/items/${item.key}/file",
                format = format,
                headers = mapOf("Zotero-API-Key" to apiKey),
            )
        }

    private fun formatOf(contentType: String?, filename: String?): String? = when {
        contentType == "application/pdf" -> "pdf"
        contentType == "application/epub+zip" -> "epub"
        filename?.endsWith(".pdf", ignoreCase = true) == true -> "pdf"
        filename?.endsWith(".epub", ignoreCase = true) == true -> "epub"
        else -> null
    }
}

@Serializable
internal data class ZoteroItem(
    val key: String,
    val data: ZoteroData,
)

@Serializable
internal data class ZoteroData(
    val title: String? = null,
    val filename: String? = null,
    val contentType: String? = null,
    @SerialName("itemType") val itemType: String? = null,
)
