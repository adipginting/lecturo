package com.adipginting.lecturo.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class AnthropicProviderTest {

    private val provider = AnthropicProvider(
        apiKey = "secret",
        model = "claude-sonnet-4-5",
        apiBase = "http://localhost:8777/anthropic",
    )

    @Test
    fun `request body uses top-level system and max_tokens`() {
        val body = provider.buildRequestBody(
            "Basket context.",
            listOf(ChatMessage("user", "summarize")),
        )
        assertEquals(
            """{"model":"claude-sonnet-4-5","max_tokens":1024,""" +
                """"system":"Basket context.",""" +
                """"messages":[{"role":"user","content":"summarize"}]}""",
            body,
        )
    }

    @Test
    fun `joins text blocks and skips others`() {
        val body = """
            {"id":"msg_1","type":"message","role":"assistant",
             "content":[{"type":"text","text":"Part one. "},
                        {"type":"thinking","thinking":"..."},
                        {"type":"text","text":"Part two."}]}
        """.trimIndent()
        assertEquals("Part one. Part two.", provider.parseResponseBody(body))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `response without text blocks is an error`() {
        provider.parseResponseBody("""{"content":[]}""")
    }
}
