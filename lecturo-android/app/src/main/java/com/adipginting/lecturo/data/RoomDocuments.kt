package com.adipginting.lecturo.data

import android.content.Context
import com.adipginting.lecturo.domain.library.Document
import com.adipginting.lecturo.domain.library.DocumentFormat
import com.adipginting.lecturo.domain.library.Documents
import com.adipginting.lecturo.domain.library.Provenance
import com.adipginting.lecturo.domain.reading.Locator
import com.adipginting.lecturo.domain.shared.DocumentId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

/** The Room adapter for the library context. */
class RoomDocuments(
    private val context: Context,
    private val db: LecturoDatabase,
) : Documents {

    private val dao = db.documentDao()

    override fun observeAll(): Flow<List<Document>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(id: DocumentId): Document? = dao.get(id.value)?.toDomain()

    override suspend fun add(document: Document) = dao.upsert(document.toEntity())

    override suspend fun saveLocator(id: DocumentId, locator: String) =
        dao.updateLocator(id.value, locator, System.currentTimeMillis())

    /** Deletes the local copies: files on disk, saved rows, book skills, DB rows. */
    override suspend fun remove(ids: List<DocumentId>) {
        if (ids.isEmpty()) return
        val docs = ids.mapNotNull { dao.get(it.value) }
        val docsDir = File(context.filesDir, "documents")
        val epubDir = File(context.filesDir, "epub")
        docs.forEach { doc ->
            File(docsDir, doc.fileName).delete()
            if (doc.format == "epub") {
                File(epubDir, doc.fileName.substringBeforeLast('.')).deleteRecursively()
            }
            db.bookSkillDao().deleteForDoc(doc.id)
            db.skillRunDao().deleteForDoc(doc.id)
        }
        dao.delete(ids.map { it.value })
    }
}

fun DocumentEntity.toDomain(): Document = Document(
    id = DocumentId(id),
    title = title,
    fileName = fileName,
    format = DocumentFormat.of(format),
    lastLocator = Locator.of(isEpub = format.equals("epub", ignoreCase = true), stored = lastLocator),
    provenance = provenanceOrNull(),
    remoteUrl = remoteUrl,
    updatedAt = updatedAt,
)

fun Document.toEntity(): DocumentEntity = DocumentEntity(
    id = id.value,
    title = title,
    fileName = fileName,
    format = format.key,
    lastLocator = lastLocator?.stored,
    source = provenance?.source,
    remoteId = provenance?.remoteId,
    remoteUrl = remoteUrl,
    updatedAt = updatedAt,
)

private fun DocumentEntity.provenanceOrNull(): Provenance? {
    val source = source?.takeIf { it.isNotBlank() } ?: return null
    val remoteId = remoteId?.takeIf { it.isNotBlank() } ?: return null
    return Provenance(source, remoteId)
}
