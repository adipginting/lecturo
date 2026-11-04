package com.adipginting.lecturo.data

import androidx.room.withTransaction
import com.adipginting.lecturo.chat.ChatImages
import com.adipginting.lecturo.domain.chat.Conversation
import com.adipginting.lecturo.domain.chat.ConversationTitle
import com.adipginting.lecturo.domain.chat.Conversations
import com.adipginting.lecturo.domain.chat.Excerpt
import com.adipginting.lecturo.domain.chat.Message
import com.adipginting.lecturo.domain.shared.DocumentId
import com.adipginting.lecturo.domain.shared.ProviderId
import com.adipginting.lecturo.domain.shared.uuidV7
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The Room adapter for the chat context. Rows become domain objects here, at the
 * seam; nothing above this sees a Room type.
 */
class RoomConversations(
    private val db: LecturoDatabase,
    private val chatImages: ChatImages? = null,
) : Conversations {

    private val conversations = db.conversationDao()
    private val messages = db.messageDao()

    override fun observeSent(): Flow<List<Conversation>> =
        conversations.observeSent().map { rows -> rows.map { it.toDomain() } }

    override fun observeDrafts(): Flow<List<Conversation>> =
        conversations.observeDrafts().map { rows -> rows.map { it.toDomain() } }

    override fun observeForDocument(docId: DocumentId): Flow<List<Conversation>> =
        conversations.observeForDoc(docId.value).map { rows -> rows.map { it.toDomain() } }

    override fun observe(id: String): Flow<Conversation?> =
        conversations.observe(id).map { it?.toDomain() }

    override suspend fun get(id: String): Conversation? = conversations.get(id)?.toDomain()

    override fun observeMessages(id: String): Flow<List<Message>> =
        messages.observeFor(id).map { rows -> rows.map { it.toDomain() } }

    override suspend fun activeMessages(id: String): List<Message> =
        messages.getActiveFor(id).map { it.toDomain() }

    override suspend fun createDraft(
        provider: ProviderId,
        excerpt: Excerpt,
        docId: DocumentId?,
        docTitle: String?,
        locator: String?,
    ): String {
        val id = uuidV7()
        conversations.insert(
            ConversationEntity(
                id = id,
                title = ConversationTitle.UNTITLED,
                providerId = provider.value,
                contextText = excerpt.text?.takeIf { it.isNotBlank() },
                contextImagePath = excerpt.imagePath?.takeIf { it.isNotBlank() },
                docId = docId?.value?.takeIf { it.isNotBlank() },
                docTitle = docTitle?.takeIf { it.isNotBlank() },
                docLocator = locator?.takeIf { it.isNotBlank() },
                sentAt = null,
            ),
        )
        return id
    }

    override suspend fun create(
        provider: ProviderId,
        excerpt: Excerpt,
        promptId: String?,
        customPrompt: String?,
        docId: DocumentId?,
        docTitle: String?,
        locator: String?,
    ): String {
        val id = uuidV7()
        conversations.insert(
            ConversationEntity(
                id = id,
                title = ConversationTitle.UNTITLED,
                providerId = provider.value,
                promptId = promptId,
                customPrompt = customPrompt?.takeIf { it.isNotBlank() },
                contextText = excerpt.text?.takeIf { it.isNotBlank() },
                contextImagePath = excerpt.imagePath?.takeIf { it.isNotBlank() },
                docId = docId?.value?.takeIf { it.isNotBlank() },
                docTitle = docTitle?.takeIf { it.isNotBlank() },
                docLocator = locator?.takeIf { it.isNotBlank() },
                sentAt = System.currentTimeMillis(),
            ),
        )
        return id
    }

    override suspend fun send(id: String, firstMessage: String) {
        val conversation = (conversations.get(id) ?: return).toDomain()
        if (!conversation.isDraft) return
        val sent = conversation.send(firstMessage, at = System.currentTimeMillis())
        conversations.markSent(id, sent.sentAt!!)
        conversations.rename(id, sent.title)
    }

    override suspend fun append(id: String, message: Message) {
        messages.insert(
            MessageEntity(
                id = message.id.ifBlank { uuidV7() },
                conversationId = id,
                role = if (message.role == Message.Role.Assistant) "assistant" else "user",
                text = message.text,
                imagePath = message.imagePath,
            ),
        )
        conversations.touch(id, System.currentTimeMillis())
    }

    override suspend fun rename(id: String, title: String) {
        ConversationTitle.normalize(title)?.let { conversations.rename(id, it) }
    }

    override suspend fun setProvider(id: String, provider: ProviderId) =
        conversations.setProvider(id, provider.value)

    override suspend fun recordUsage(id: String, promptTokens: Int?, completionTokens: Int?) =
        conversations.recordUsage(id, promptTokens, completionTokens, System.currentTimeMillis())

    override suspend fun compact(id: String, summary: String) = db.withTransaction {
        messages.supersedeActive(id)
        messages.insert(
            MessageEntity(
                id = uuidV7(),
                conversationId = id,
                role = "assistant",
                text = summary,
                isSummary = true,
            ),
        )
        conversations.clearUsage(id)
        conversations.touch(id, System.currentTimeMillis())
    }

    override suspend fun delete(id: String, keepImages: Boolean) {
        if (!keepImages && chatImages != null) {
            val paths = messages.imagePathsFor(id) +
                listOfNotNull(conversations.get(id)?.contextImagePath)
            chatImages.delete(paths)
        }
        messages.deleteFor(id)
        conversations.delete(id)
    }
}
