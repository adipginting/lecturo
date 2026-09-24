package com.adipginting.lecturo.data

import com.adipginting.lecturo.domain.chat.Conversation
import com.adipginting.lecturo.domain.chat.Excerpt
import com.adipginting.lecturo.domain.chat.Message

/**
 * The persistence record as the domain sees it. Room stays a record; the rules
 * live in `:domain`, so a row is read back as a conversation before anything is
 * decided about it.
 */
fun ConversationEntity.toDomain(): Conversation = Conversation(
    id = id,
    title = title,
    providerId = providerId,
    excerpt = Excerpt(text = contextText, imagePath = contextImagePath),
    docId = docId,
    docTitle = docTitle,
    docLocator = docLocator,
    promptId = promptId,
    customPrompt = customPrompt,
    lastPromptTokens = lastPromptTokens,
    lastCompletionTokens = lastCompletionTokens,
    lastCallAt = lastCallAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    sentAt = sentAt,
)

fun MessageEntity.toDomain(): Message = Message(
    id = id,
    role = if (role == "assistant") Message.Role.Assistant else Message.Role.User,
    text = text,
    imagePath = imagePath,
    isSummary = isSummary,
    superseded = superseded,
    createdAt = createdAt,
)
