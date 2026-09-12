package com.adipginting.lecturo.library

import android.content.Context
import com.adipginting.lecturo.data.DocumentEntity
import com.adipginting.lecturo.data.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Downloads a document from a web URL into app-private storage and registers
 * it in the library — the app owns its library, same as SAF import.
 */
class DocumentDownloader(
    private val context: Context,
    private val repo: DocumentRepository,
) {
    private val fetch = BookFetch()

    suspend fun download(
        url: String,
        headers: Map<String, String> = emptyMap(),
        title: String? = null,
    ): DocumentEntity = withContext(Dispatchers.IO) {
        fetch.fetch(url, headers).use { book ->
            val id = UUID.randomUUID().toString()
            val fileName = "$id.${book.format}"
            val dir = File(context.filesDir, "documents").apply { mkdirs() }
            book.body.byteStream().use { input ->
                File(dir, fileName).outputStream().use { output -> input.copyTo(output) }
            }
            val doc = DocumentEntity(
                id = id,
                title = title ?: titleFor(book.url),
                fileName = fileName,
                format = book.format,
            )
            repo.add(doc)
            doc
        }
    }

    companion object {
        internal fun titleFor(url: String): String =
            url.substringBefore('?').substringAfterLast('/')
                .substringBeforeLast('.')
                .ifBlank { "download" }
    }
}
