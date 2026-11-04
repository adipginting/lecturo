package com.adipginting.lecturo.domain.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A document's active chat is the one it was last engaged with; its draft is the
 * newest conversation that has not been sent. Neither may answer for the other:
 * a draft is not a chat, and a chat is not a draft. Ties are broken by id so the
 * choice is stable, and a document with neither has none.
 */
class ActiveConversationTest {

    private fun conversation(id: Long, updatedAt: Long, sentAt: Long? = updatedAt) = Conversation(
        id = id.toString(),
        title = "Chat $id",
        providerId = "deepseek",
        excerpt = Excerpt(),
        updatedAt = updatedAt,
        sentAt = sentAt,
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

    @Test
    fun `a draft is never the active chat`() {
        val chat = conversation(id = 1, updatedAt = 100)
        val draft = conversation(id = 2, updatedAt = 900, sentAt = null)
        assertEquals(chat, activeConversation(listOf(chat, draft)))
    }

    @Test
    fun `the newest draft is the document's draft`() {
        val older = conversation(id = 1, updatedAt = 100, sentAt = null)
        val newer = conversation(id = 2, updatedAt = 300, sentAt = null)
        assertEquals(newer, latestDraft(listOf(older, newer)))
        assertEquals(newer, latestDraft(listOf(newer, older)))
    }

    @Test
    fun `a sent conversation is never a draft`() {
        val chat = conversation(id = 1, updatedAt = 900)
        assertNull(latestDraft(listOf(chat)))
    }
}
