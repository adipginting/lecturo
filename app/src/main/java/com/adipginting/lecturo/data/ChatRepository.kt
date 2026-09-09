package com.adipginting.lecturo.data

import kotlinx.coroutines.flow.Flow

class ChatRepository(private val db: LecturoDatabase) {
    private val conversations = db.conversationDao()
    private val messages = db.messageDao()
    private val prompts = db.promptDao()
    private val basket = db.basketDao()

    fun observeConversations(): Flow<List<ConversationEntity>> = conversations.observeAll()

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
    ): Long =
        conversations.insert(
            ConversationEntity(
                title = "New conversation",
                providerId = providerId,
                promptId = promptId,
                customPrompt = customPrompt?.takeIf { it.isNotBlank() },
                contextText = contextText?.takeIf { it.isNotBlank() },
            ),
        )

    suspend fun addMessage(conversationId: Long, role: String, text: String) {
        messages.insert(MessageEntity(conversationId = conversationId, role = role, text = text))
        conversations.touch(conversationId, System.currentTimeMillis())
    }

    suspend fun renameIfUntitled(conversationId: Long, firstMessage: String) {
        val conversation = conversations.get(conversationId) ?: return
        if (conversation.title == "New conversation") {
            conversations.rename(conversationId, firstMessage.take(40))
        }
    }

    suspend fun savePrompt(prompt: PromptEntity): Long = prompts.upsert(prompt)

    suspend fun deletePrompt(id: Long) = prompts.delete(id)

    suspend fun deleteConversation(id: Long) {
        messages.deleteFor(id)
        conversations.delete(id)
    }

    /**
     * System prompt for a conversation: the prompt locked at start (saved
     * prompt by id, else the one-off custom prompt) plus the frozen excerpt
     * stored on the conversation, if any. The live basket is no longer injected.
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
