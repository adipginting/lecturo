package com.adipginting.lecturo.domain.chat

/**
 * The passage, screenshot or crop a conversation is about. Captured once and
 * frozen: compaction never folds it, and a conversation never loses it.
 */
data class Excerpt(
    val text: String? = null,
    val imagePath: String? = null,
) {
    val isEmpty: Boolean get() = text == null && imagePath == null

    companion object {
        fun text(value: String): Excerpt = Excerpt(text = value)

        fun image(path: String): Excerpt = Excerpt(imagePath = path)
    }
}
