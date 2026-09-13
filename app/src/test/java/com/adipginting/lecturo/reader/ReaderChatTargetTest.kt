package com.adipginting.lecturo.reader

import com.adipginting.lecturo.chat.DraftChatArgs
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the reader's chat icon opens. A draft being composed is the newest
 * context and wins while it belongs to the document being read; otherwise the
 * document's active conversation; and the chats list when there is neither.
 */
class ReaderChatTargetTest {

    private fun draftFor(docId: String) = DraftChatArgs(
        text = "an excerpt",
        docTitle = "A Book",
        locator = "12",
        savedItemId = null,
        docId = docId,
    )

    @Test
    fun `a draft for this document wins over the active conversation`() {
        assertEquals(
            ReaderChatTarget.Draft,
            readerChatTarget(draft = draftFor("doc-1"), activeConversationId = 42, docId = "doc-1"),
        )
    }

    @Test
    fun `a draft for another document is ignored`() {
        assertEquals(
            ReaderChatTarget.Conversation(42),
            readerChatTarget(draft = draftFor("doc-2"), activeConversationId = 42, docId = "doc-1"),
        )
    }

    @Test
    fun `a foreign draft with no active conversation still falls back to the list`() {
        assertEquals(
            ReaderChatTarget.Chats,
            readerChatTarget(draft = draftFor("doc-2"), activeConversationId = null, docId = "doc-1"),
        )
    }

    @Test
    fun `without a draft the active conversation is the target`() {
        assertEquals(
            ReaderChatTarget.Conversation(42),
            readerChatTarget(draft = null, activeConversationId = 42, docId = "doc-1"),
        )
    }

    @Test
    fun `with nothing active the list is the target`() {
        assertEquals(
            ReaderChatTarget.Chats,
            readerChatTarget(draft = null, activeConversationId = null, docId = "doc-1"),
        )
    }
}
