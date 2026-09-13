package com.adipginting.lecturo.data

/**
 * The conversation a document treats as its active chat: the one engaged with
 * most recently. Ties — a conversation created and messaged within the same
 * millisecond — are broken by id, so the choice is stable from call to call.
 */
internal fun activeConversation(conversations: List<ConversationEntity>): ConversationEntity? =
    conversations.maxWithOrNull(compareBy({ it.updatedAt }, { it.id }))
