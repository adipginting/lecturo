package com.adipginting.lecturo.sync

/** Source ids: stored with a downloaded document and returned on its remote items. */
const val SOURCE_CALIBRE = "calibre"
const val SOURCE_ZOTERO = "zotero"

/** A document (or, for OPDS, a sub-catalog) listed by a remote library. */
data class RemoteItem(
    /** Which library this came from; with [id] it identifies the item. */
    val source: String,
    val id: String,
    val title: String,
    val author: String? = null,
    /** Direct document URL; null for navigation-only entries. */
    val downloadUrl: String? = null,
    /** OPDS sub-catalog URL; tapping the item browses into it. */
    val childrenUrl: String? = null,
    /** Cover image URL the source offered, if any. */
    val coverUrl: String? = null,
    /** "pdf" or "epub" when known. */
    val format: String? = null,
    /** Extra request headers needed to download (e.g. Zotero API key). */
    val headers: Map<String, String> = emptyMap(),
)

/**
 * A remote library behind one interface. Manual sync only: the UI calls
 * [browse]/[search] on demand; nothing happens in the background.
 */
interface RemoteLibrarySource {
    /** False until required settings (server URL, API key, ...) are entered. */
    val isConfigured: Boolean

    /** Lists items; [url] browses into a sub-catalog (OPDS), null = root. */
    suspend fun browse(url: String? = null): List<RemoteItem>

    suspend fun search(query: String): List<RemoteItem>
}
