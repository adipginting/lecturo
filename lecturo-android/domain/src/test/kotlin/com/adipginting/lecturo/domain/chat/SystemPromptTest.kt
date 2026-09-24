package com.adipginting.lecturo.domain.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the model is told before the conversation: the prompt locked at start,
 * the frozen excerpt, and the maths rule that every provider shares. The live
 * saved item is never injected — only what was frozen.
 */
class SystemPromptTest {

    private fun conversation(excerpt: String? = null) = Conversation(
        id = 1,
        title = "Chat",
        providerId = "deepseek",
        excerpt = Excerpt(text = excerpt),
    )

    @Test
    fun `a conversation with no excerpt still carries the maths rule`() {
        val prompt = systemPrompt(conversation(), promptBody = null)
        assertTrue(prompt.contains("LaTeX"))
    }

    @Test
    fun `the frozen excerpt is quoted back to the model`() {
        val prompt = systemPrompt(conversation("Burke on legitimacy"), promptBody = null)
        assertTrue(prompt.contains("Burke on legitimacy"))
    }

    @Test
    fun `the locked prompt comes first`() {
        val prompt = systemPrompt(conversation("the excerpt"), promptBody = "Answer as a historian.")
        assertEquals(true, prompt.startsWith("Answer as a historian."))
    }

    @Test
    fun `a blank prompt is left out`() {
        val prompt = systemPrompt(conversation(), promptBody = "   ")
        assertTrue(prompt.startsWith("Write any mathematics"))
    }
}
