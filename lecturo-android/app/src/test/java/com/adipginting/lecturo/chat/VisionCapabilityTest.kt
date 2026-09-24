package com.adipginting.lecturo.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionCapabilityTest {

    private fun supports(providerId: String, modelId: String) =
        defaultSupportsVision(providerId, modelId)

    @Test
    fun `openai vision families are recognized by prefix`() {
        assertTrue(supports("openai", "gpt-4o"))
        assertTrue(supports("openai", "gpt-4o-mini"))
        assertTrue(supports("openai", "gpt-4.1-nano"))
        assertTrue(supports("openai", "gpt-5-turbo"))
        assertFalse(supports("openai", "gpt-3.5-turbo"))
        assertFalse(supports("openai", "text-embedding-3-large"))
    }

    @Test
    fun `anthropic claude 3 and 4 families are vision-capable`() {
        assertTrue(supports("anthropic", "claude-3-haiku-20240307"))
        assertTrue(supports("anthropic", "claude-3-5-sonnet-20241022"))
        assertTrue(supports("anthropic", "claude-sonnet-4-5"))
        assertTrue(supports("anthropic", "claude-opus-4-1"))
        assertFalse(supports("anthropic", "claude-2.1"))
    }

    @Test
    fun `openrouter ids are matched on the embedded origin family`() {
        assertTrue(supports("openrouter", "openai/gpt-4o"))
        assertTrue(supports("openrouter", "anthropic/claude-sonnet-4-5"))
        assertTrue(supports("openrouter", "moonshotai/kimi-k2"))
        assertFalse(supports("openrouter", "deepseek/deepseek-chat"))
        assertFalse(supports("openrouter", "meta-llama/llama-3-8b"))
    }

    @Test
    fun `kimi vision is k2 or the moonshot vision variants`() {
        assertTrue(supports("kimi", "kimi-k2-0711-preview"))
        assertTrue(supports("kimi", "moonshot-v1-8k-vision-preview"))
        assertTrue(supports("kimi", "moonshot-v1-128k-vision"))
        assertFalse(supports("kimi", "moonshot-v1-8k"))
    }

    @Test
    fun `deepseek and unknown providers have no vision models`() {
        assertFalse(supports("deepseek", "deepseek-chat"))
        assertFalse(supports("deepseek", "deepseek-reasoner"))
        assertFalse(supports("copilot", "gpt-4o"))
        assertFalse(supports("openai", ""))
    }

    @Test
    fun `the relay is vision-capable whatever model it runs`() {
        assertTrue(supports("lecturo", "lecturo-cloud"))
        assertTrue(supports("lecturo", ""))
    }

    @Test
    fun `an override wins over the default in both directions`() {
        val overrides = mapOf(
            visionOverrideKey("openai", "gpt-4o") to false,
            visionOverrideKey("deepseek", "deepseek-chat") to true,
        )
        assertFalse(supportsVision("openai", "gpt-4o", overrides))
        assertTrue(supportsVision("deepseek", "deepseek-chat", overrides))
        assertTrue(supportsVision("openai", "gpt-4o-mini", overrides))
        assertFalse(supportsVision("deepseek", "deepseek-reasoner", overrides))
    }

    @Test
    fun `override keys are provider-slash-model`() {
        assertEquals("openai/gpt-4o-mini", visionOverrideKey("openai", "gpt-4o-mini"))
    }
}
