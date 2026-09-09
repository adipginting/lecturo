package com.adipginting.lecturo.sync

/** A document (or, for OPDS, a sub-catalog) listed by a remote library. */
data class RemoteItem(
    val id: String,
    val title: String,
    val author: String? = null,
    /** Direct document URL; null for navigation-only entries. */
    val downloadUrl: String? = null,
    /** OPDS sub-catalog URL; tapping the item browses into it. */
    val childrenUrl: String? = null,
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
    val name: String

    /** False until required settings (server URL, API key, ...) are entered. */
    val isConfigured: Boolean

    /** Lists items; [url] browses into a sub-catalog (OPDS), null = root. */
    suspend fun browse(url: String? = null): List<RemoteItem>

    suspend fun search(query: String): List<RemoteItem>
}
