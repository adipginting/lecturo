package com.adipginting.lecturo.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatibleProviderTest {

    private val provider = OpenAiCompatibleProvider(
        id = "kimi",
        baseUrl = "https://api.moonshot.cn/v1/",
        apiKey = "secret",
        model = "moonshot-v1-8k",
    )

    @Test
    fun `request body puts system prompt first and serializes history`() {
        val body = provider.buildRequestBody(
            "You are helpful.",
            listOf(ChatMessage("user", "hi"), ChatMessage("assistant", "hello")),
        )
        assertEquals(
            """{"model":"moonshot-v1-8k","messages":[""" +
                """{"role":"system","content":"You are helpful."},""" +
                """{"role":"user","content":"hi"},""" +
                """{"role":"assistant","content":"hello"}]}""",
            body,
        )
    }

    @Test
    fun `a message with an image sends the content-part array form`() {
        val body = provider.buildRequestBody(
            "You are helpful.",
            listOf(
                ChatMessage("user", "what is this?", imageBase64 = "QUJD", imageMime = "image/jpeg"),
                ChatMessage("assistant", "a diagram"),
            ),
        )
        assertEquals(
            """{"model":"moonshot-v1-8k","messages":[""" +
                """{"role":"system","content":"You are helpful."},""" +
                """{"role":"user","content":[""" +
                """{"type":"text","text":"what is this?"},""" +
                """{"type":"image_url","image_url":{"url":"data:image/jpeg;base64,QUJD"}}]},""" +
                """{"role":"assistant","content":"a diagram"}]}""",
            body,
        )
    }

    @Test
    fun `text-only messages keep the plain string content form`() {
        val body = provider.buildRequestBody(
            "You are helpful.",
            listOf(ChatMessage("user", "hi", imageBase64 = null)),
        )
        assertTrue(body.contains(""""content":"hi""""))
        assertTrue(!body.contains("image_url"))
    }

    @Test
    fun `parses first choice content`() {
        val body = """
            {"id":"x","choices":[{"index":0,"message":{"role":"assistant","content":"42"}}]}
        """.trimIndent()
        assertEquals("42", provider.parseResponseBody(body).text)
    }

    @Test
    fun `parses usage when the api reports it`() {
        val body = """
            {"choices":[{"message":{"role":"assistant","content":"ok"}}],
             "usage":{"prompt_tokens":12,"completion_tokens":3,"total_tokens":15}}
        """.trimIndent()
        val reply = provider.parseResponseBody(body)
        assertEquals("ok", reply.text)
        assertEquals(12, reply.promptTokens)
        assertEquals(3, reply.completionTokens)
    }

    @Test
    fun `usage counts are null when the api omits them`() {
        val body = """{"choices":[{"message":{"role":"assistant","content":"ok"}}]}"""
        val reply = provider.parseResponseBody(body)
        assertNull(reply.promptTokens)
        assertNull(reply.completionTokens)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty choices are an error`() {
        provider.parseResponseBody("""{"choices":[]}""")
    }

    @Test
    fun `configured only with an api key`() {
        assertTrue(provider.isConfigured)
        assertTrue(!OpenAiCompatibleProvider("openai", "https://api.openai.com/v1", "", "gpt-4o-mini").isConfigured)
    }
}
