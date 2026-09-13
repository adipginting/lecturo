package com.adipginting.lecturo.reader

import com.adipginting.lecturo.chat.DraftChatArgs

/** What the reader's chat icon opens. */
internal sealed interface ReaderChatTarget {
    /** The document's chats list, when it has no chat yet. */
    data object Chats : ReaderChatTarget

    /** The draft being composed — an excerpt with nothing sent yet. */
    data object Draft : ReaderChatTarget

    /** An existing conversation. */
    data class Conversation(val id: Long) : ReaderChatTarget
}

/**
 * The chat the reader's icon points at. A draft is the newest context and wins
 * while it belongs to the document being read; a draft for another document —
 * the reader was left with one pending, another book opened — is ignored.
 * Failing a draft, the document's active conversation; failing that, the list.
 */
internal fun readerChatTarget(
    draft: DraftChatArgs?,
    activeConversationId: Long?,
    docId: String,
): ReaderChatTarget = when {
    draft?.docId == docId -> ReaderChatTarget.Draft
    activeConversationId != null -> ReaderChatTarget.Conversation(activeConversationId)
    else -> ReaderChatTarget.Chats
}
