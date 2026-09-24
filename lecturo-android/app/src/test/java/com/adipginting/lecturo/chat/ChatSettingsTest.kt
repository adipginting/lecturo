package com.adipginting.lecturo.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSettingsTest {

    @Test
    fun `deepseek uses its documented endpoints`() {
        assertEquals("https://api.deepseek.com", ChatSettings.defaultBaseUrl("deepseek"))
        assertEquals("deepseek-chat", ChatSettings.defaultModel("deepseek"))
    }

    @Test
    fun `deepseek is selectable and named`() {
        assertTrue("deepseek" in ALL_PROVIDER_IDS)
        assertEquals("DeepSeek", providerDisplayName("deepseek"))
    }

    @Test
    fun `deepseek builds an openai-compatible provider with defaults`() {
        val provider = ChatSettings.buildProvider("deepseek", ProviderSettings(apiKey = "secret"))
        assertEquals("deepseek", provider.id)
        assertEquals("DeepSeek", provider.displayName)
        assertTrue(provider.isConfigured)
    }

    @Test
    fun `vision overrides survive an encode-decode round trip`() {
        val overrides = mapOf(
            "openai/gpt-4o" to false,
            "deepseek/deepseek-chat" to true,
        )
        assertEquals(
            overrides,
            ChatSettings.decodeVisionOverrides(ChatSettings.encodeVisionOverrides(overrides)),
        )
        assertEquals(emptyMap<String, Boolean>(), ChatSettings.decodeVisionOverrides(null))
        assertEquals(emptyMap<String, Boolean>(), ChatSettings.decodeVisionOverrides(""))
    }

    @Test
    fun `malformed override lines are dropped`() {
        assertEquals(
            mapOf("openai/gpt-4o" to true),
            ChatSettings.decodeVisionOverrides("openai/gpt-4o=1\nbogus\n=0\nopenai/gpt-4o=maybe"),
        )
    }

    @Test
    fun `override encoding is stable regardless of map order`() {
        val a = mapOf("b/m2" to false, "a/m1" to true)
        val b = mapOf("a/m1" to true, "b/m2" to false)
        assertEquals(ChatSettings.encodeVisionOverrides(a), ChatSettings.encodeVisionOverrides(b))
    }
}
