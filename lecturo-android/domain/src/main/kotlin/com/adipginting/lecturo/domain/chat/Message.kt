package com.adipginting.lecturo.domain.chat

/** One turn in a conversation, from the user or the model. */
data class Message(
    val id: Long = 0,
    val role: Role,
    val text: String,
    val imagePath: String? = null,
    /** A compaction summary standing in for the messages marked superseded. */
    val isSummary: Boolean = false,
    /** Replaced by a compaction summary; kept for display, never sent again. */
    val superseded: Boolean = false,
    val createdAt: Long = 0,
) {
    enum class Role { User, Assistant }

    /** A message folded into a summary is history; it no longer goes to the model. */
    val isActive: Boolean get() = !superseded
}
