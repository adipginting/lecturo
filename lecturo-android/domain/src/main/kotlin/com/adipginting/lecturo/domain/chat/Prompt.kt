package com.adipginting.lecturo.domain.chat

/**
 * A user-authored prompt chip, sent with whatever is already typed when tapped.
 * It carries no invariant beyond having a title and a body, so it is a value,
 * not an aggregate.
 */
data class Prompt(val id: Long, val title: String, val body: String) {
    companion object {
        /** [title] and [body] as a prompt to store, or null when either is blank. */
        fun of(id: Long = 0, title: String, body: String): Prompt? {
            val title = title.trim()
            val body = body.trim()
            return if (title.isEmpty() || body.isEmpty()) null else Prompt(id, title, body)
        }
    }
}
