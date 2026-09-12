package com.adipginting.lecturo.data

/**
 * The rules for naming a conversation. Names arrive from two directions — the
 * first message that names a chat after what it was asked, and the user, who can
 * type whatever they like — so both go through the same sieve: whitespace is
 * layout rather than content, a name has to read as one line in a list, and
 * nothing is cut through the middle of an emoji.
 */
internal object ConversationTitle {

    /** What a conversation is called until someone or something names it. */
    const val UNTITLED = "New conversation"

    /** Long enough for a sentence, short enough to stay one line in a list. */
    const val MAX_LENGTH = 60

    /** A first message is squeezed harder: it sits beside a name and a date. */
    private const val FROM_MESSAGE_LENGTH = 40

    private val WHITESPACE = Regex("\\s+")

    /** [input] as a name to store, or null when there is nothing in it to store. */
    fun normalize(input: String): String? = shorten(input, MAX_LENGTH)

    /** The name a conversation takes from its first message. */
    fun fromMessage(message: String): String? = shorten(message, FROM_MESSAGE_LENGTH)

    private fun shorten(input: String, maxLength: Int): String? = input
        .replace(WHITESPACE, " ")
        .trim()
        .take(maxLength)
        .trimEnd()
        // A cut can land between the halves of an emoji, leaving half a pair.
        .let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it }
        .takeIf { it.isNotEmpty() }
}
