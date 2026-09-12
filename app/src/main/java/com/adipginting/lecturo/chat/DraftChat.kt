package com.adipginting.lecturo.chat

/**
 * In-memory payload for a draft conversation fired from the saved or reader.
 * Excerpts can be long, so this holder is used instead of navigation arguments.
 */
data class DraftChatArgs(
    val text: String,
    val docTitle: String?,
    val locator: String?,
    val savedItemId: Long?,
)

object DraftChat {
    var pending: DraftChatArgs? = null
}
