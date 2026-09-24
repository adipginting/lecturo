package com.adipginting.lecturo.domain.chat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How much a conversation may carry before it is compacted. The model's window
 * decides, less room for the reply and a safety margin, capped hard — a million
 * token window is an invitation to a bill — and never so low that a short chat
 * compacts itself. An unknown model keeps the conservative default; a number the
 * user typed wins outright.
 */
class ModelWindowTest {

    @Test
    fun `a large window is capped, not spent`() {
        assertEquals(32_000, contextBudget("gpt-4o"))
        assertEquals(32_000, contextBudget("claude-sonnet-4-5"))
        assertEquals(32_000, contextBudget("deepseek-chat"))
    }

    @Test
    fun `a small window gives a small budget, floor and all`() {
        assertEquals(3_400, contextBudget("moonshot-v1-8k"))
    }

    @Test
    fun `an unknown model keeps the conservative default`() {
        assertEquals(8_000, contextBudget("some-new-model"))
    }

    @Test
    fun `an override wins outright`() {
        assertEquals(12_000, contextBudget("gpt-4o", override = 12_000))
        assertEquals(12_000, contextBudget("unknown", override = 12_000))
    }

    @Test
    fun `nonsense overrides are ignored`() {
        assertEquals(32_000, contextBudget("gpt-4o", override = 0))
        assertEquals(32_000, contextBudget("gpt-4o", override = -5))
    }
}
