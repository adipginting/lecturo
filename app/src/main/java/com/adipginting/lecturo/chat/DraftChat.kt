package com.adipginting.lecturo.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * In-memory payload for a draft conversation fired from the saved or reader.
 * Excerpts can be long, so this holder is used instead of navigation arguments.
 */
data class DraftChatArgs(
    val text: String,
    val docTitle: String?,
    val locator: String?,
    val savedItemId: Long?,
    /** The document this excerpt came from; a draft is that document's chat. */
    val docId: String,
)

object DraftChat {

    /**
     * The draft being composed, if any. State rather than a plain field, because
     * the reader's panel reads it from a composition and has to notice a new
     * excerpt arriving while it is already showing one.
     */
    var pending by mutableStateOf<DraftChatArgs?>(null)

    /**
     * Marks [draft] as finished. It only empties the holder while that draft is
     * still the one in it, so a send that lands after the reader has started a
     * new draft does not take the new one with it.
     */
    fun consume(draft: DraftChatArgs) {
        if (pending === draft) pending = null
    }
}
