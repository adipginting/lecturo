package com.adipginting.lecturo.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            "Saved context.",
            listOf(ChatMessage("user", "summarize")),
        )
        assertEquals(
            """{"model":"claude-sonnet-4-5","max_tokens":1024,""" +
                """"system":"Saved context.",""" +
                """"messages":[{"role":"user","content":[{"type":"text","text":"summarize"}]}]}""",
            body,
        )
    }

    @Test
    fun `a message with an image sends image and text blocks`() {
        val body = provider.buildRequestBody(
            "Saved context.",
            listOf(
                ChatMessage("user", "what is this?", imageBase64 = "QUJD", imageMime = "image/jpeg"),
                ChatMessage("assistant", "a diagram"),
            ),
        )
        assertEquals(
            """{"model":"claude-sonnet-4-5","max_tokens":1024,""" +
                """"system":"Saved context.",""" +
                """"messages":[{"role":"user","content":[""" +
                """{"type":"image","source":{"type":"base64","media_type":"image/jpeg","data":"QUJD"}},""" +
                """{"type":"text","text":"what is this?"}]},""" +
                """{"role":"assistant","content":[{"type":"text","text":"a diagram"}]}]}""",
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
        assertEquals("Part one. Part two.", provider.parseResponseBody(body).text)
    }

    @Test
    fun `parses usage when the api reports it`() {
        val body = """
            {"content":[{"type":"text","text":"Done."}],
             "usage":{"input_tokens":20,"output_tokens":5}}
        """.trimIndent()
        val reply = provider.parseResponseBody(body)
        assertEquals("Done.", reply.text)
        assertEquals(20, reply.promptTokens)
        assertEquals(5, reply.completionTokens)
    }

    @Test
    fun `usage counts are null when the api omits them`() {
        val body = """{"content":[{"type":"text","text":"Done."}]}"""
        val reply = provider.parseResponseBody(body)
        assertNull(reply.promptTokens)
        assertNull(reply.completionTokens)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `response without text blocks is an error`() {
        provider.parseResponseBody("""{"content":[]}""")
    }
}
