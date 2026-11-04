package com.adipginting.lecturo.domain.skills

import kotlinx.coroutines.flow.Flow

/** The skills context's persistence port: installed skills, applications, runs. */
interface Skills {
    fun observeInstalled(): Flow<List<Skill>>

    /** The built-ins land once; a catalog skill with the same id wins. */
    suspend fun ensureStarters()

    suspend fun install(skill: Skill)

    suspend fun uninstall(id: String)

    /** Skills applied to one book, as skill ids. */
    fun observeApplied(docId: String): Flow<List<String>>

    suspend fun apply(docId: String, skillId: String)

    suspend fun unapply(docId: String, skillId: String)

    /** The runs recorded against one book, newest first. */
    fun observeRuns(docId: String): Flow<List<Insight>>

    suspend fun startRun(docId: String, skillId: String): String

    suspend fun finishRun(id: String, result: String?, error: String?)
}
