package com.adipginting.lecturo.library

import android.content.Context
import com.adipginting.lecturo.data.DocumentEntity
import com.adipginting.lecturo.data.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
    private val client = OkHttpClient()

    suspend fun download(
        url: String,
        headers: Map<String, String> = emptyMap(),
        title: String? = null,
    ): DocumentEntity = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).apply {
            headers.forEach { (name, value) -> header(name, value) }
        }.build()
        val response = client.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) throw IllegalArgumentException("HTTP ${resp.code}")
            val body = resp.body ?: throw IllegalArgumentException("Empty response")
            val format = formatFor(resp.header("Content-Type"), url)
                ?: throw IllegalArgumentException("Not a PDF or EPUB: $url")
            val id = UUID.randomUUID().toString()
            val fileName = "$id.$format"
            val dir = File(context.filesDir, "documents").apply { mkdirs() }
            body.byteStream().use { input ->
                File(dir, fileName).outputStream().use { output -> input.copyTo(output) }
            }
            val doc = DocumentEntity(
                id = id,
                title = title ?: titleFor(url),
                fileName = fileName,
                format = format,
            )
            repo.add(doc)
            doc
        }
    }

    companion object {
        internal fun formatFor(contentType: String?, url: String): String? {
            val mime = contentType?.substringBefore(';')?.trim()?.lowercase()
            return when {
                mime == "application/pdf" -> "pdf"
                mime == "application/epub+zip" -> "epub"
                url.substringBefore('?').endsWith(".pdf", ignoreCase = true) -> "pdf"
                url.substringBefore('?').endsWith(".epub", ignoreCase = true) -> "epub"
                else -> null
            }
        }

        internal fun titleFor(url: String): String =
            url.substringBefore('?').substringAfterLast('/')
                .substringBeforeLast('.')
                .ifBlank { "download" }
    }
}
