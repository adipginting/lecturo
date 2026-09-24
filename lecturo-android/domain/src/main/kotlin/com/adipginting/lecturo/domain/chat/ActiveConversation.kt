package com.adipginting.lecturo.domain.chat

/**
 * The conversation a document treats as its active chat: the one engaged with
 * most recently. Ties — a conversation created and messaged within the same
 * millisecond — are broken by id, so the choice is stable from call to call.
 * Drafts are conversations too, but they are not chats yet: an unsent draft
 * never answers for the document.
 */
fun activeConversation(conversations: List<Conversation>): Conversation? =
    conversations
        .filter { !it.isDraft }
        .maxWithOrNull(compareBy({ it.updatedAt }, { it.id }))

/**
 * The draft a document is waiting on: its newest conversation whose first
 * message has not been sent. A book can hold several drafts; the reader's icon
 * offers the newest, and the rest wait in the drafts list.
 */
fun latestDraft(conversations: List<Conversation>): Conversation? =
    conversations
        .filter { it.isDraft }
        .maxWithOrNull(compareBy({ it.updatedAt }, { it.id }))
