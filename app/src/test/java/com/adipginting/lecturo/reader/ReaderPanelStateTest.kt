package com.adipginting.lecturo.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The panel is opened from three places — the icon's draft, the icon's active
 * chat, the chats list — and each displaces whatever was showing. What is worth
 * coming back to is not its business: the reader's chat icon derives that from
 * the draft holder and the document's active conversation.
 */
class ReaderPanelStateTest {

    private val panel = ReaderPanelState()

    @Test
    fun `the panel starts hidden`() {
        assertEquals(PanelMode.Hidden, panel.mode)
        assertFalse(panel.isOpen)
    }

    @Test
    fun `asking about a selection opens the draft from wherever you were`() {
        panel.openChats()
        panel.openDraft()
        assertEquals(PanelMode.Draft, panel.mode)

        panel.openConversation(7)
        panel.openDraft()
        assertEquals(PanelMode.Draft, panel.mode)
    }

    @Test
    fun `a draft's first send becomes the conversation`() {
        panel.openDraft()
        panel.openConversation(42)
        assertEquals(PanelMode.Conversation(42), panel.mode)
    }

    @Test
    fun `opening the list replaces the open conversation`() {
        panel.openConversation(1)
        panel.openChats()
        assertEquals(PanelMode.Chats, panel.mode)
    }

    @Test
    fun `opening a conversation replaces the one before it`() {
        panel.openConversation(1)
        panel.openConversation(2)
        assertEquals(PanelMode.Conversation(2), panel.mode)
    }

    @Test
    fun `collapsing puts the panel away`() {
        panel.openConversation(42)
        panel.collapse()
        assertEquals(PanelMode.Hidden, panel.mode)
        assertFalse(panel.isOpen)
    }

    @Test
    fun `collapsing twice is harmless`() {
        panel.openChats()
        panel.collapse()
        panel.collapse()
        assertEquals(PanelMode.Hidden, panel.mode)
    }
}
