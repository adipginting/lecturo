package com.adipginting.lecturo.domain.chat

/**
 * One rule for every provider, so mathematics arrives delimited and the reader
 * renders it instead of showing TeX source.
 */
private const val MATH_RULE =
    "Write any mathematics as LaTeX: \\( … \\) for inline maths and " +
        "\\[ … \\] for display maths."

private const val EXCERPT_PREAMBLE =
    "The user is reading documents and has selected the following excerpt for this conversation.\n\n"

/**
 * System prompt for a conversation: the prompt locked at start ([promptBody] —
 * a saved prompt by id, else the one-off custom prompt) plus the frozen excerpt
 * stored on the conversation, if any. The live saved item is never injected.
 */
fun systemPrompt(conversation: Conversation, promptBody: String?): String = buildString {
    promptBody?.takeIf { it.isNotBlank() }?.let {
        append(it.trim())
        append("\n\n")
    }
    conversation.excerpt.text?.takeIf { it.isNotBlank() }?.let {
        append(EXCERPT_PREAMBLE)
        append(it.trim())
        append("\n\n")
    }
    append(MATH_RULE)
}
