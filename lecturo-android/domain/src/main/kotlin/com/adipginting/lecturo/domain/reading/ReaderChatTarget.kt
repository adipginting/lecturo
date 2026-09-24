package com.adipginting.lecturo.domain.reading

/** What the reader's chat icon opens. */
sealed interface ReaderChatTarget {
    /** The chats surface, when the document has neither draft nor chat. */
    data object Chats : ReaderChatTarget

    /** The document's newest draft: a conversation not yet sent. */
    data class Draft(val id: Long) : ReaderChatTarget

    /** An existing conversation. */
    data class Conversation(val id: Long) : ReaderChatTarget
}

/**
 * Where the icon goes: the document's active conversation wins, so further
 * excerpts append to it (ADR 0003); failing one, its newest draft; failing that,
 * the chats surface. Ids are scoped to the document by the caller, so a draft
 * from another book cannot reach this rule.
 */
fun readerChatTarget(draftId: Long?, activeConversationId: Long?): ReaderChatTarget = when {
    activeConversationId != null -> ReaderChatTarget.Conversation(activeConversationId)
    draftId != null -> ReaderChatTarget.Draft(draftId)
    else -> ReaderChatTarget.Chats
}
