package com.adipginting.lecturo.domain.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A conversation's life: born a draft, sent only once, named from its first
 * message, never left nameless. Persistence and providers adapt to this, not the
 * other way round.
 */
class ConversationTest {

    private fun draft() = Conversation.draft(
        id = "1",
        providerId = "deepseek",
        docId = "doc",
        excerpt = Excerpt.text("the passage"),
    )

    @Test
    fun `a new conversation is a draft`() {
        assertTrue(draft().isDraft)
    }

    @Test
    fun `sending names it from the first message and marks it sent`() {
        val sent = draft().send("Why does the author say this?", at = 100L)
        assertFalse(sent.isDraft)
        assertEquals("Why does the author say this?", sent.title)
    }

    @Test
    fun `a sent conversation cannot be sent again`() {
        val sent = draft().send("first", at = 100L)
        assertEquals(sent, sent.send("second", at = 200L))
    }

    @Test
    fun `a name the user set survives the first send`() {
        val named = draft().rename("Burke on legitimacy")
        assertEquals("Burke on legitimacy", named.send("a long first message", at = 1L).title)
    }

    @Test
    fun `renaming never leaves it nameless`() {
        val conversation = draft().send("first", at = 1L)
        assertEquals("first", conversation.rename("   ").title)
    }
}
