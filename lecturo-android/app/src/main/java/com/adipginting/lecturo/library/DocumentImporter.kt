package com.adipginting.lecturo.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.adipginting.lecturo.domain.library.Document
import com.adipginting.lecturo.domain.library.DocumentFormat
import com.adipginting.lecturo.domain.library.Documents
import com.adipginting.lecturo.domain.shared.DocumentId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Copies a document from a content URI into app-private storage and
 * registers it in the library. Import = copy: the app owns its library.
 *
 * The name a file arrives under decides nothing: files are read for their
 * signature first, so a web page saved under an EPUB name is turned away here
 * rather than left in the library to fail when it is opened.
 */
class DocumentImporter(
    private val context: Context,
    private val repo: Documents,
) {
    suspend fun import(uri: Uri): Document = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri) ?: "document"
        val stream = context.contentResolver.openInputStream(uri)?.buffered()
            ?: throw IllegalArgumentException("Cannot open $displayName")
        val id = UUID.randomUUID().toString()
        val dir = File(context.filesDir, "documents").apply { mkdirs() }

        val document = stream.use { input ->
            val header = BookFile.readHeader(input)
            val format = BookFile.sniff(header)
                ?: throw IllegalArgumentException(BookFile.describe(header, displayName))
            val fileName = "$id.$format"
            File(dir, fileName).outputStream().use { output ->
                output.write(header)
                input.copyTo(output)
            }
            Document(
                id = DocumentId(id),
                title = displayName.substringBeforeLast('.'),
                fileName = fileName,
                format = DocumentFormat.of(format),
            )
        }
        repo.add(document)
        document
    }

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
}
