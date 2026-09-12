package com.adipginting.lecturo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Names arrive from two directions — derived from a first message, or typed by
 * the user — and both end up in the same column, read back in a list row next to
 * a date. These tests pin the sieve they share: whitespace is layout, not
 * content; a name has to be short enough to read as one; and nothing may be cut
 * through the middle of an emoji.
 */
class ConversationTitleTest {

    @Test
    fun `a name keeps its words and loses its layout`() {
        assertEquals(
            "Burke on legitimacy",
            ConversationTitle.normalize("  Burke\n\non\tlegitimacy  "),
        )
    }

    @Test
    fun `blank input names nothing`() {
        assertNull(ConversationTitle.normalize(""))
        assertNull(ConversationTitle.normalize("   "))
        assertNull(ConversationTitle.normalize("\n\t "))
    }

    @Test
    fun `a typed name is cut to a readable length`() {
        assertEquals("x".repeat(ConversationTitle.MAX_LENGTH), ConversationTitle.normalize("x".repeat(200)))
    }

    @Test
    fun `a cut name does not end in a space`() {
        val normalized = ConversationTitle.normalize("word ".repeat(30))!!
        assertFalse(normalized, normalized.endsWith(" "))
    }

    @Test
    fun `a first message is named more tightly than a typed name`() {
        val message = "y".repeat(200)
        val name = ConversationTitle.fromMessage(message)!!
        assertEquals(40, name.length)
        assertEquals(ConversationTitle.MAX_LENGTH, ConversationTitle.normalize(message)!!.length)
    }

    @Test
    fun `a name is never cut through an emoji`() {
        val name = "a".repeat(59) + "\uD83C\uDF89" + "b".repeat(5)
        val normalized = ConversationTitle.normalize(name)!!
        assertEquals("a".repeat(59), normalized)
    }
}
