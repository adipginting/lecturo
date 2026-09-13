package com.adipginting.lecturo.data

import kotlinx.coroutines.flow.Flow

class ChatRepository(private val db: LecturoDatabase) {
    private val conversations = db.conversationDao()
    private val messages = db.messageDao()
    private val prompts = db.promptDao()
    private val saved = db.savedDao()

    fun observeConversations(): Flow<List<ConversationEntity>> = conversations.observeAll()

    /** The conversations fired from one document, for picking its active chat. */
    fun observeForDoc(docId: String): Flow<List<ConversationEntity>> =
        conversations.observeForDoc(docId)

    fun observeMessages(conversationId: Long): Flow<List<MessageEntity>> =
        messages.observeFor(conversationId)

    fun observePrompts(): Flow<List<PromptEntity>> = prompts.observeAll()

    suspend fun getConversation(id: Long) = conversations.get(id)

    suspend fun history(conversationId: Long): List<MessageEntity> =
        messages.getFor(conversationId)

    suspend fun createConversation(
        providerId: String,
        promptId: Long?,
        customPrompt: String? = null,
        contextText: String? = null,
        docId: String? = null,
        docTitle: String? = null,
        docLocator: String? = null,
    ): Long =
        conversations.insert(
            ConversationEntity(
                title = ConversationTitle.UNTITLED,
                providerId = providerId,
                promptId = promptId,
                customPrompt = customPrompt?.takeIf { it.isNotBlank() },
                contextText = contextText?.takeIf { it.isNotBlank() },
                docId = docId?.takeIf { it.isNotBlank() },
                docTitle = docTitle?.takeIf { it.isNotBlank() },
                docLocator = docLocator?.takeIf { it.isNotBlank() },
            ),
        )

    suspend fun addMessage(conversationId: Long, role: String, text: String) {
        messages.insert(MessageEntity(conversationId = conversationId, role = role, text = text))
        conversations.touch(conversationId, System.currentTimeMillis())
    }

    /** Renames a conversation. A name with nothing in it is refused. */
    suspend fun rename(conversationId: Long, title: String) {
        ConversationTitle.normalize(title)?.let { conversations.rename(conversationId, it) }
    }

    /**
     * Names a conversation after its first message, which is the only chance it
     * gets: a name the user has set is left alone.
     */
    suspend fun renameIfUntitled(conversationId: Long, firstMessage: String) {
        val conversation = conversations.get(conversationId) ?: return
        if (conversation.title == ConversationTitle.UNTITLED) {
            ConversationTitle.fromMessage(firstMessage)
                ?.let { conversations.rename(conversationId, it) }
        }
    }

    suspend fun setProvider(conversationId: Long, providerId: String) =
        conversations.setProvider(conversationId, providerId)

    suspend fun savePrompt(prompt: PromptEntity): Long = prompts.upsert(prompt)

    suspend fun deletePrompt(id: Long) = prompts.delete(id)

    suspend fun deleteConversation(id: Long) {
        messages.deleteFor(id)
        conversations.delete(id)
    }

    /**
     * System prompt for a conversation: the prompt locked at start (saved
     * prompt by id, else the one-off custom prompt) plus the frozen excerpt
     * stored on the conversation, if any. The live saved is no longer injected.
     */
    suspend fun buildSystemPrompt(conversation: ConversationEntity): String {
        val promptBody = conversation.promptId?.let { prompts.get(it)?.body }
            ?: conversation.customPrompt
        val context = conversation.contextText
        return buildString {
            promptBody?.let {
                append(it.trim())
                if (context != null) append("\n\n")
            }
            context?.let {
                append("The user is reading documents and has selected the following excerpt for this conversation.\n\n")
                append(it.trim())
            }
        }
    }
}
