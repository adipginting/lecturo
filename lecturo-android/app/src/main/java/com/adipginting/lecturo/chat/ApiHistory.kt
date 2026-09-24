package com.adipginting.lecturo.chat

import com.adipginting.lecturo.domain.chat.Message

/** System prompt for the one call that compacts a conversation. */
internal const val COMPACTION_SYSTEM_PROMPT =
    "Summarize this conversation faithfully and compactly: keep the facts, " +
        "decisions, and open questions needed to continue it. " +
        "Reply with the summary only."

/** Prefix marking a summary when it goes back into an API payload. */
internal const val SUMMARY_PREFIX = "Conversation so far: "

/**
 * Stored messages as an API payload: superseded ones (replaced by a
 * compaction) are left out, and a summary goes as a user message — some
 * providers refuse a conversation that opens with an assistant turn.
 *
 * [contextImage] is the conversation's frozen screenshot; like the excerpt on
 * the system prompt it rides every payload, attached to the first user turn.
 */
internal fun apiMessages(
    messages: List<Message>,
    contextImage: ChatImage? = null,
): List<ChatMessage> {
    val base = messages.filter { !it.superseded }.map {
        if (it.isSummary) ChatMessage("user", SUMMARY_PREFIX + it.text)
        else ChatMessage(if (it.role == Message.Role.Assistant) "assistant" else "user", it.text)
    }
    if (contextImage == null) return base
    val firstUser = base.indexOfFirst { it.role == "user" }
    if (firstUser < 0) return base
    return base.mapIndexed { index, message ->
        if (index == firstUser) {
            message.copy(imageBase64 = contextImage.base64, imageMime = contextImage.mime)
        } else {
            message
        }
    }
}
