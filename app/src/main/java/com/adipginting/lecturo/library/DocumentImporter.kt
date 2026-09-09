package com.adipginting.lecturo.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.adipginting.lecturo.data.DocumentEntity
import com.adipginting.lecturo.data.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Copies a document from a content URI into app-private storage and
 * registers it in the library. Import = copy: the app owns its library.
 */
class DocumentImporter(
    private val context: Context,
    private val repo: DocumentRepository,
) {
    suspend fun import(uri: Uri): DocumentEntity = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri) ?: "document"
        val format = when {
            displayName.endsWith(".pdf", ignoreCase = true) -> "pdf"
            displayName.endsWith(".epub", ignoreCase = true) -> "epub"
            else -> throw IllegalArgumentException("Unsupported file: $displayName")
        }
        val id = UUID.randomUUID().toString()
        val fileName = "$id.$format"
        val dir = File(context.filesDir, "documents").apply { mkdirs() }
        val dest = File(dir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalArgumentException("Cannot open $uri")
        val doc = DocumentEntity(
            id = id,
            title = displayName.substringBeforeLast('.'),
            fileName = fileName,
            format = format,
        )
        repo.add(doc)
        doc
    }

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
}
