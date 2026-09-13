package com.adipginting.lecturo.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The holder the reader and the draft composer meet at. It is read from a
 * composition — the panel has to notice a new excerpt arriving — and it is
 * cleared by whoever finishes with a draft, which is why consuming one is not
 * the same as clearing the holder.
 */
class DraftChatTest {

    @Before
    fun empty() {
        DraftChat.pending = null
    }

    private fun draft(text: String) = DraftChatArgs(
        text = text,
        docTitle = "inference-engineering",
        locator = "18",
        savedItemId = null,
        docId = "doc-1",
    )

    @Test
    fun `consuming a draft that is not held changes nothing`() {
        DraftChat.pending = draft("kept")
        DraftChat.consume(draft("stale"))
        assertEquals("kept", DraftChat.pending?.text)
    }

    @Test
    fun `a new draft replaces the one before it`() {
        DraftChat.pending = draft("first")
        DraftChat.pending = draft("second")
        assertEquals("second", DraftChat.pending?.text)
    }

    @Test
    fun `consuming the current draft empties the holder`() {
        val only = draft("only")
        DraftChat.pending = only
        DraftChat.consume(only)
        assertNull(DraftChat.pending)
    }

    @Test
    fun `consuming an old draft leaves a newer one alone`() {
        val first = draft("first")
        DraftChat.pending = first
        DraftChat.pending = draft("second")
        DraftChat.consume(first)
        assertEquals("second", DraftChat.pending?.text)
    }
}
