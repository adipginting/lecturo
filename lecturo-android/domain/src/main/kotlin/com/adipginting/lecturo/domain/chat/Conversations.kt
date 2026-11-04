package com.adipginting.lecturo.domain.chat

import com.adipginting.lecturo.domain.shared.DocumentId
import com.adipginting.lecturo.domain.shared.ProviderId
import kotlinx.coroutines.flow.Flow

/**
 * The chat context's persistence port. Everything returns domain types; the
 * Room adapter maps at this seam. A draft is a conversation with a null
 * `sentAt`, so drafts and conversations are the same store.
 */
interface Conversations {
    /** Sent conversations, newest first. A draft is a conversation, but not here. */
    fun observeSent(): Flow<List<Conversation>>

    /** The drafts: conversations whose first message has not been sent. */
    fun observeDrafts(): Flow<List<Conversation>>

    /** The conversations fired from one document, for picking its active chat. */
    fun observeForDocument(docId: DocumentId): Flow<List<Conversation>>

    fun observe(id: String): Flow<Conversation?>

    suspend fun get(id: String): Conversation?

    fun observeMessages(id: String): Flow<List<Message>>

    /** The messages that go into the next payload: never superseded ones. */
    suspend fun activeMessages(id: String): List<Message>

    /** Appends a turn and touches the conversation. */
    suspend fun append(id: String, message: Message)

    /** A conversation that has not been sent, its excerpt frozen now. */
    suspend fun createDraft(
        provider: ProviderId,
        excerpt: Excerpt,
        docId: DocumentId? = null,
        docTitle: String? = null,
        locator: String? = null,
    ): String

    /** A conversation sent at creation, for a chat with no draft stage. */
    suspend fun create(
        provider: ProviderId,
        excerpt: Excerpt,
        promptId: String? = null,
        customPrompt: String? = null,
        docId: DocumentId? = null,
        docTitle: String? = null,
        locator: String? = null,
    ): String

    /** Sending the first message is what turns a draft into a conversation. */
    suspend fun send(id: String, firstMessage: String)

    /** A name with nothing in it is refused. */
    suspend fun rename(id: String, title: String)

    suspend fun setProvider(id: String, provider: ProviderId)

    suspend fun recordUsage(id: String, promptTokens: Int?, completionTokens: Int?)

    suspend fun compact(id: String, summary: String)

    suspend fun delete(id: String, keepImages: Boolean = false)
}
