package com.adipginting.lecturo.data

import com.adipginting.lecturo.domain.skills.Insight
import com.adipginting.lecturo.domain.skills.STARTER_SKILLS
import com.adipginting.lecturo.domain.skills.Skill
import com.adipginting.lecturo.domain.skills.Skills
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The Room adapter for the skills context. */
class RoomSkills(private val db: LecturoDatabase) : Skills {

    private val skills = db.skillDao()
    private val applied = db.bookSkillDao()
    private val runs = db.skillRunDao()

    override fun observeInstalled(): Flow<List<Skill>> =
        skills.observeAll().map { rows -> rows.map { it.toDomain() } }

    /** The built-ins land once; a catalog skill with the same id wins. */
    override suspend fun ensureStarters() {
        STARTER_SKILLS.forEach { if (skills.get(it.id) == null) skills.upsert(it.toEntity()) }
    }

    override suspend fun install(skill: Skill) = skills.upsert(skill.toEntity())

    override suspend fun uninstall(id: String) = skills.delete(id)

    override fun observeApplied(docId: String): Flow<List<String>> =
        applied.observeForDoc(docId).map { rows -> rows.map { it.skillId } }

    override suspend fun apply(docId: String, skillId: String) =
        applied.upsert(BookSkillEntity(docId = docId, skillId = skillId))

    override suspend fun unapply(docId: String, skillId: String) = applied.delete(docId, skillId)

    override fun observeRuns(docId: String): Flow<List<Insight>> =
        runs.observeForDoc(docId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun startRun(docId: String, skillId: String): Long =
        runs.insert(SkillRunEntity(docId = docId, skillId = skillId, status = RUN_RUNNING))

    override suspend fun finishRun(id: Long, result: String?, error: String?) = runs.finish(
        id = id,
        status = if (error == null) RUN_DONE else RUN_FAILED,
        result = result,
        error = error,
        finishedAt = System.currentTimeMillis(),
    )

    private companion object {
        const val RUN_RUNNING = "running"
        const val RUN_DONE = "done"
        const val RUN_FAILED = "failed"
    }
}
