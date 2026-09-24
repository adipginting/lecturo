package com.adipginting.lecturo.domain.skills

/** One skill the app can run: a named prompt, with what it applies to. */
data class Skill(
    val id: String,
    val name: String,
    val description: String = "",
    val author: String? = null,
    val version: String = "",
    val scope: String = "",
    val trigger: String = "",
    val template: String,
    val price: String? = null,
    val catalogUrl: String? = null,
)

const val SCOPE_MESSAGE = "message"
const val SCOPE_CONVERSATION = "conversation"
const val SCOPE_BOOK = "book"
const val TRIGGER_MANUAL = "manual"
const val TRIGGER_ON_SAVE = "on-save"

/**
 * The prompt a run sends: the template with the book and the excerpt filled in.
 * A template that never names the book gets it appended, so a book-level skill
 * cannot run without the book it was attached to.
 */
fun renderSkill(template: String, book: String, excerpt: String?): String {
    var out = template.replace("{{book}}", book).replace("{{excerpt}}", excerpt.orEmpty())
    if (!template.contains("{{book}}")) out = out.trimEnd() + "\n\n" + book
    return out.replace(Regex("\n{3,}"), "\n\n").trim()
}
