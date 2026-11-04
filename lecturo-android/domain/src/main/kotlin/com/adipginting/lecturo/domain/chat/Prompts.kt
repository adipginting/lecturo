package com.adipginting.lecturo.domain.chat

import kotlinx.coroutines.flow.Flow

/** The prompt chips the user has saved. */
interface Prompts {
    fun observeAll(): Flow<List<Prompt>>

    suspend fun get(id: String): Prompt?

    suspend fun save(prompt: Prompt): String

    suspend fun delete(id: String)
}
