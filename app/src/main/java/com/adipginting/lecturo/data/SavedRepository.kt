package com.adipginting.lecturo.data

class SavedRepository(private val db: LecturoDatabase) {
    private val dao = db.savedDao()

    fun observeAll() = dao.observeAllWithTitles()

    suspend fun add(docId: String, text: String, locator: String?) =
        dao.insert(SavedItemEntity(docId = docId, text = text, locator = locator))

    suspend fun remove(id: Long) = dao.delete(id)

    suspend fun clear() = dao.clear()
}
