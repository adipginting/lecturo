package com.adipginting.lecturo.skills

import android.content.Context
import com.adipginting.lecturo.AppContainer
import com.adipginting.lecturo.chat.ChatMessage
import com.adipginting.lecturo.chat.ChatSettings
import com.adipginting.lecturo.domain.library.Documents
import com.adipginting.lecturo.domain.shared.DocumentId
import com.adipginting.lecturo.domain.skills.Skill
import com.adipginting.lecturo.domain.skills.Skills
import com.adipginting.lecturo.domain.skills.renderSkill
import com.adipginting.lecturo.library.bookText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Runs one skill over one book, on the globally selected provider, and records
 * the outcome as an insight. A failure is recorded, not thrown: a run is a
 * background job whose result is its report.
 */
class SkillRun(context: Context) {

    private val app = context.applicationContext
    private val skills: Skills = AppContainer.skills(app)
    private val documents: Documents = AppContainer.documents(app)
    private val settings = ChatSettings(app)

    suspend fun run(docId: String, skill: Skill): String {
        val runId = skills.startRun(docId, skill.id)
        try {
            val document = documents.get(DocumentId(docId))
                ?: throw IllegalStateException("Document not found")
            val text = withContext(Dispatchers.IO) {
                bookText(
                    File(File(app.filesDir, "documents"), document.fileName),
                    document.format.key,
                )
            } ?: throw IllegalStateException("This book has no text to read yet")

            val providerId = settings.selectedProvider.first()
            val provider = settings.buildProviderFor(providerId)
            if (!provider.isConfigured) {
                throw IllegalStateException(
                    "${provider.displayName} is not configured — add its API key in Settings",
                )
            }
            val prompt = renderSkill(skill.template, book = text, excerpt = null)
            val reply = provider.chat(system = "", messages = listOf(ChatMessage("user", prompt)))
            skills.finishRun(runId, result = reply.text, error = null)
        } catch (e: Exception) {
            skills.finishRun(runId, result = null, error = e.message ?: "The skill failed")
        }
        return runId
    }
}
