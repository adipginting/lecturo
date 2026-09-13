package com.adipginting.lecturo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A document's active chat is the one it was last engaged with. The rule is
 * how "last" is decided when two chats claim the same millisecond, and that a
 * document with no chats has none — never a crash, never a guess.
 */
class ActiveConversationTest {

    private fun conversation(id: Long, updatedAt: Long) = ConversationEntity(
        id = id,
        title = "Chat $id",
        providerId = "deepseek",
        updatedAt = updatedAt,
    )

    @Test
    fun `a document with no conversations has no active one`() {
        assertNull(activeConversation(emptyList()))
    }

    @Test
    fun `a single conversation is the active one`() {
        val only = conversation(id = 1, updatedAt = 100)
        assertEquals(only, activeConversation(listOf(only)))
    }

    @Test
    fun `the most recently updated conversation wins, whatever the order`() {
        val old = conversation(id = 1, updatedAt = 100)
        val recent = conversation(id = 2, updatedAt = 300)
        val middle = conversation(id = 3, updatedAt = 200)

        assertEquals(recent, activeConversation(listOf(old, recent, middle)))
        assertEquals(recent, activeConversation(listOf(recent, middle, old)))
    }

    @Test
    fun `same millisecond is broken by id, so the choice is stable`() {
        val first = conversation(id = 7, updatedAt = 500)
        val second = conversation(id = 9, updatedAt = 500)
        assertEquals(second, activeConversation(listOf(first, second)))
        assertEquals(second, activeConversation(listOf(second, first)))
    }
}
