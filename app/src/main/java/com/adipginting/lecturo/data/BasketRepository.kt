package com.adipginting.lecturo.data

class BasketRepository(private val db: LecturoDatabase) {
    private val dao = db.basketDao()

    fun observeAll() = dao.observeAllWithTitles()

    suspend fun add(docId: String, text: String, locator: String?) =
        dao.insert(BasketItemEntity(docId = docId, text = text, locator = locator))

    suspend fun remove(id: Long) = dao.delete(id)

    suspend fun clear() = dao.clear()
}
