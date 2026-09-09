package com.adipginting.lecturo.data

import android.content.Context
import java.io.File

class DocumentRepository(private val context: Context, private val db: LecturoDatabase) {
    private val dao = db.documentDao()

    fun observeAll() = dao.observeAll()

    suspend fun get(id: String) = dao.get(id)

    suspend fun add(doc: DocumentEntity) = dao.upsert(doc)

    suspend fun saveLocator(id: String, locator: String) =
        dao.updateLocator(id, locator, System.currentTimeMillis())

    /** Deletes the local copies: files on disk, EPUB unpack dirs, basket rows, DB rows. */
    suspend fun remove(docs: List<DocumentEntity>) {
        if (docs.isEmpty()) return
        val docsDir = File(context.filesDir, "documents")
        val epubDir = File(context.filesDir, "epub")
        docs.forEach { doc ->
            File(docsDir, doc.fileName).delete()
            if (doc.format == "epub") {
                File(epubDir, doc.fileName.substringBeforeLast('.')).deleteRecursively()
            }
        }
        db.basketDao().deleteByDocIds(docs.map { it.id })
        dao.delete(docs.map { it.id })
    }
}
