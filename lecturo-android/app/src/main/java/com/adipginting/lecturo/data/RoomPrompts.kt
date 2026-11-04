package com.adipginting.lecturo.data

import com.adipginting.lecturo.domain.chat.Prompt
import com.adipginting.lecturo.domain.chat.Prompts
import com.adipginting.lecturo.domain.shared.uuidV7
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The Room adapter for prompt chips. */
class RoomPrompts(private val db: LecturoDatabase) : Prompts {

    private val prompts = db.promptDao()

    override fun observeAll(): Flow<List<Prompt>> =
        prompts.observeAll().map { rows -> rows.map { Prompt(it.id, it.title, it.body) } }

    override suspend fun get(id: String): Prompt? =
        prompts.get(id)?.let { Prompt(it.id, it.title, it.body) }

    override suspend fun save(prompt: Prompt): String {
        val id = prompt.id.ifBlank { uuidV7() }
        prompts.upsert(PromptEntity(id = id, title = prompt.title, body = prompt.body))
        return id
    }

    override suspend fun delete(id: String) = prompts.delete(id)
}
