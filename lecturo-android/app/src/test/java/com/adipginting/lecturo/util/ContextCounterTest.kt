package com.adipginting.lecturo.util

import com.adipginting.lecturo.domain.chat.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The counter's two regimes: exact at the base once an API has reported the
 * last payload's size (only newer messages and the draft are estimated), and a
 * whole-context estimate until one has.
 */
class ContextCounterTest {

    private fun message(text: String, createdAt: Long = 0) = Message(
        role = Message.Role.User,
        text = text,
        createdAt = createdAt,
    )

    @Test
    fun `estimation rounds up at four characters per token`() {
        assertEquals(0, estimateTokens(0))
        assertEquals(1, estimateTokens(1))
        assertEquals(1, estimateTokens(4))
        assertEquals(2, estimateTokens(5))
        assertEquals(100, estimateTokens(400))
    }

    @Test
    fun `without reported usage the whole context is estimated`() {
        val messages = listOf(message("abcd"), message("abcdefgh"))
        // system 40 + messages 12 + draft 8 = 60 chars -> 15 tokens
        assertEquals(
            15,
            contextTokens(
                lastPromptTokens = null,
                lastCallAt = null,
                systemChars = 40,
                activeMessages = messages,
                draftChars = 8,
            ),
        )
    }

    @Test
    fun `with reported usage only what arrived after the call is estimated`() {
        val messages = listOf(
            message("abcd", createdAt = 100),
            message("abcdefgh", createdAt = 200),
        )
        // 1000 + estimate(8 new chars + 4 draft chars = 12 -> 3)
        assertEquals(
            1003,
            contextTokens(
                lastPromptTokens = 1000,
                lastCallAt = 150,
                systemChars = 40,
                activeMessages = messages,
                draftChars = 4,
            ),
        )
    }

    @Test
    fun `a fresh reply leaves only the draft to estimate`() {
        val messages = listOf(message("abcd", createdAt = 100))
        assertEquals(
            1001,
            contextTokens(
                lastPromptTokens = 1000,
                lastCallAt = 150,
                systemChars = 40,
                activeMessages = messages,
                draftChars = 4,
            ),
        )
    }

    @Test
    fun `reported usage without its timestamp falls back to estimation`() {
        assertEquals(
            3,
            contextTokens(
                lastPromptTokens = 1000,
                lastCallAt = null,
                systemChars = 4,
                activeMessages = listOf(message("abcd")),
                draftChars = 4,
            ),
        )
    }

    @Test
    fun `counts format compactly`() {
        assertEquals("~800", formatTokenCount(800))
        assertEquals("~4.2k", formatTokenCount(4200))
        assertEquals("~12k", formatTokenCount(12000))
    }

    @Test
    fun `a threshold must be a positive number`() {
        assertEquals(8000, thresholdOrNull("8000"))
        assertEquals(8000, thresholdOrNull(" 8000 "))
        assertNull(thresholdOrNull("0"))
        assertNull(thresholdOrNull("-5"))
        assertNull(thresholdOrNull(""))
        assertNull(thresholdOrNull("many"))
    }
}
