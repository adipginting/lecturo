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
}
