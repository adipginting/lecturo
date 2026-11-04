package com.adipginting.lecturo.domain.skills

/** The result of one skill run, attached to the document it was run against. */
data class Insight(
    val id: String,
    val docId: String,
    val skillId: String,
    val status: Status,
    val result: String? = null,
    val error: String? = null,
    val startedAt: Long = 0,
    val finishedAt: Long? = null,
) {
    enum class Status(val key: String) {
        Running("running"),
        Done("done"),
        Failed("failed"),
        ;

        companion object {
            fun of(raw: String): Status = entries.firstOrNull { it.key == raw } ?: Running
        }
    }
}
