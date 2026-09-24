package com.adipginting.lecturo.domain.library

import com.adipginting.lecturo.domain.shared.DocumentId
import kotlinx.coroutines.flow.Flow

/** The library context's persistence port. */
interface Documents {
    fun observeAll(): Flow<List<Document>>

    suspend fun get(id: DocumentId): Document?

    suspend fun add(document: Document)

    suspend fun saveLocator(id: DocumentId, locator: String)

    /** Removes the documents and everything that hung off them. */
    suspend fun remove(ids: List<DocumentId>)
}
