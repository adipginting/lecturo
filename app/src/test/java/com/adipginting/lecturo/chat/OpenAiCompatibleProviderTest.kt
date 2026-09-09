package com.adipginting.lecturo.chat

import org.junit.Assert.assertEquals
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
    fun `parses first choice content`() {
        val body = """
            {"id":"x","choices":[{"index":0,"message":{"role":"assistant","content":"42"}}]}
        """.trimIndent()
        assertEquals("42", provider.parseResponseBody(body))
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
