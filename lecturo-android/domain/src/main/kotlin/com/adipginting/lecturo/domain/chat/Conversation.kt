package com.adipginting.lecturo.domain.chat

/**
 * The life of a conversation: born a draft, sent only once, named from its first
 * message, never left nameless. Its excerpt is frozen for that life. Persistence
 * and providers adapt to this, not the other way round.
 */
data class Conversation(
    val id: Long,
    val title: String,
    val providerId: String,
    val excerpt: Excerpt,
    val docId: String? = null,
    val docTitle: String? = null,
    val docLocator: String? = null,
    /** Saved prompt chosen at start; locked thereafter. */
    val promptId: Long? = null,
    /** One-off prompt typed at start, when no saved prompt is used. */
    val customPrompt: String? = null,
    val messages: List<Message> = emptyList(),
    val lastPromptTokens: Int? = null,
    val lastCompletionTokens: Int? = null,
    val lastCallAt: Long? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val sentAt: Long? = null,
) {
    /** A conversation whose first message has not been sent. */
    val isDraft: Boolean get() = sentAt == null

    /** The passage the conversation is about, as the UI names it. */
    val contextText: String? get() = excerpt.text

    /** The frozen screenshot the conversation is about, if any. */
    val contextImagePath: String? get() = excerpt.imagePath

    /**
     * The first message sends a draft and names it — unless the user has already
     * named it, in which case the name stands. Sending a sent conversation is a
     * no-op: a conversation is sent once.
     */
    fun send(firstMessage: String, at: Long): Conversation {
        if (!isDraft) return this
        val named = if (title == ConversationTitle.UNTITLED) {
            ConversationTitle.fromMessage(firstMessage) ?: ConversationTitle.UNTITLED
        } else {
            title
        }
        return copy(title = named, sentAt = at)
    }

    /** A name with nothing in it is refused; the old one stands. */
    fun rename(name: String): Conversation =
        ConversationTitle.normalize(name)?.let { copy(title = it) } ?: this

    companion object {
        fun draft(
            id: Long,
            providerId: String,
            excerpt: Excerpt,
            docId: String? = null,
        ): Conversation = Conversation(
            id = id,
            title = ConversationTitle.UNTITLED,
            providerId = providerId,
            excerpt = excerpt,
            docId = docId,
        )
    }
}
