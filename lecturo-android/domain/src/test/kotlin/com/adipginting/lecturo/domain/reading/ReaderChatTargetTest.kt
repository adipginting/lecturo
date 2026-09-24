package com.adipginting.lecturo.domain.reading

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the reader's chat icon opens. The document's active conversation wins, so
 * further excerpts append to it; failing one, its newest draft; failing that, the
 * chats surface. The ids are scoped to the document by the caller, so a draft
 * from another book cannot reach this rule.
 */
class ReaderChatTargetTest {

    @Test
    fun `the active conversation wins over a draft`() {
        assertEquals(ReaderChatTarget.Conversation(42), readerChatTarget(draftId = 7, activeConversationId = 42))
    }

    @Test
    fun `without a conversation the draft is the target`() {
        assertEquals(ReaderChatTarget.Draft(7), readerChatTarget(draftId = 7, activeConversationId = null))
    }

    @Test
    fun `with nothing at all the chats surface is the target`() {
        assertEquals(ReaderChatTarget.Chats, readerChatTarget(draftId = null, activeConversationId = null))
    }
}
