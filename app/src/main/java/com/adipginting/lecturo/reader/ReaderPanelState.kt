package com.adipginting.lecturo.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What the reader's panel is showing, if anything. */
sealed interface PanelMode {
    /** Put away. The document's chat icon brings back the draft or the active chat. */
    data object Hidden : PanelMode

    /** The list of conversations. */
    data object Chats : PanelMode

    /** Composing the first message about an excerpt. */
    data object Draft : PanelMode

    /** An open conversation. */
    data class Conversation(val id: Long) : PanelMode
}

/**
 * The panel's state of mind: what it shows while it is open. It remembers
 * nothing once put away — the way back is the document's chat icon, which
 * derives its target from the draft holder and the active conversation, the
 * same sources the panel opened from.
 */
class ReaderPanelState {

    var mode by mutableStateOf<PanelMode>(PanelMode.Hidden)
        private set

    val isOpen: Boolean get() = mode != PanelMode.Hidden

    fun openChats() = show(PanelMode.Chats)

    fun openDraft() = show(PanelMode.Draft)

    fun openConversation(id: Long) = show(PanelMode.Conversation(id))

    /** Puts the panel away. What was showing is not kept here. */
    fun collapse() = show(PanelMode.Hidden)

    private fun show(next: PanelMode) {
        mode = next
    }
}
