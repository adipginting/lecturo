package com.adipginting.lecturo.chat

import com.adipginting.lecturo.domain.chat.Message
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a conversation's stored messages become in an API payload: superseded
 * messages (a compaction replaced them) are gone, and the summary that stands
 * in for them travels as a user turn so the thread keeps alternating.
 */
class ApiHistoryTest {

    private fun message(
        role: String,
        text: String,
        isSummary: Boolean = false,
        superseded: Boolean = false,
    ) = Message(
        role = if (role == "assistant") Message.Role.Assistant else Message.Role.User,
        text = text,
        isSummary = isSummary,
        superseded = superseded,
    )

    @Test
    fun `superseded messages are left out of the payload`() {
        val messages = listOf(
            message("user", "old question", superseded = true),
            message("assistant", "old answer", superseded = true),
            message("user", "new question"),
        )
        assertEquals(
            listOf(ChatMessage("user", "new question")),
            apiMessages(messages),
        )
    }

    @Test
    fun `a summary goes as a user message with its prefix`() {
        val messages = listOf(
            message("user", "old question", superseded = true),
            message("assistant", "They discussed tokens.", isSummary = true),
            message("user", "and now?"),
        )
        assertEquals(
            listOf(
                ChatMessage("user", "Conversation so far: They discussed tokens."),
                ChatMessage("user", "and now?"),
            ),
            apiMessages(messages),
        )
    }

    @Test
    fun `ordinary messages keep their role and text`() {
        val messages = listOf(
            message("user", "hi"),
            message("assistant", "hello"),
        )
        assertEquals(
            listOf(ChatMessage("user", "hi"), ChatMessage("assistant", "hello")),
            apiMessages(messages),
        )
    }

    @Test
    fun `the context image rides the first user message only`() {
        val image = ChatImage(base64 = "QUJD", mime = "image/jpeg")
        val messages = listOf(
            message("user", "first"),
            message("assistant", "answer"),
            message("user", "second"),
        )
        assertEquals(
            listOf(
                ChatMessage("user", "first", imageBase64 = "QUJD", imageMime = "image/jpeg"),
                ChatMessage("assistant", "answer"),
                ChatMessage("user", "second"),
            ),
            apiMessages(messages, image),
        )
    }

    @Test
    fun `the context image attaches to the summary when it opens the payload`() {
        val image = ChatImage(base64 = "QUJD", mime = "image/jpeg")
        val messages = listOf(
            message("user", "old question", superseded = true),
            message("assistant", "They discussed tokens.", isSummary = true),
            message("user", "and now?"),
        )
        assertEquals(
            listOf(
                ChatMessage(
                    "user",
                    "Conversation so far: They discussed tokens.",
                    imageBase64 = "QUJD",
                    imageMime = "image/jpeg",
                ),
                ChatMessage("user", "and now?"),
            ),
            apiMessages(messages, image),
        )
    }

    @Test
    fun `no user message means the image has nowhere to go`() {
        val image = ChatImage(base64 = "QUJD", mime = "image/jpeg")
        val messages = listOf(message("assistant", "hello"))
        assertEquals(
            listOf(ChatMessage("assistant", "hello")),
            apiMessages(messages, image),
        )
    }
}
