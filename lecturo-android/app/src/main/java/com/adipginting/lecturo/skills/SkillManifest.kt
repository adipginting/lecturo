package com.adipginting.lecturo.skills

import com.adipginting.lecturo.domain.skills.SCOPE_BOOK
import com.adipginting.lecturo.domain.skills.SCOPE_CONVERSATION
import com.adipginting.lecturo.domain.skills.SCOPE_MESSAGE
import com.adipginting.lecturo.domain.skills.Skill
import com.adipginting.lecturo.domain.skills.TRIGGER_MANUAL
import com.adipginting.lecturo.domain.skills.TRIGGER_ON_SAVE
import kotlinx.serialization.Serializable

/** One skill as a catalog serves it. Everything is optional but id, name, template. */
@Serializable
data class SkillManifest(
    val id: String,
    val name: String,
    val description: String = "",
    val author: String? = null,
    val version: String = "",
    val scope: String = "",
    val trigger: String = "",
    val template: String,
    /** Reserved for the marketplace; null while everything is free. */
    val price: String? = null,
)

/**
 * A manifest as the skill the app can run, or null when it lacks what a run needs.
 * An unknown scope becomes "book" — the one the reader applies to a document —
 * and an unknown trigger becomes manual, so nothing installs that can fire on
 * its own unasked.
 */
internal fun SkillManifest.toSkill(catalogUrl: String?): Skill? {
    val id = id.trim().takeIf { it.isNotEmpty() } ?: return null
    val name = name.trim().takeIf { it.isNotEmpty() } ?: return null
    val body = template.takeIf { it.isNotBlank() } ?: return null
    return Skill(
        id = id,
        name = name,
        description = description.trim(),
        author = author?.trim()?.takeIf { it.isNotEmpty() },
        version = version.trim(),
        scope = when (scope.trim().lowercase()) {
            SCOPE_MESSAGE -> SCOPE_MESSAGE
            SCOPE_CONVERSATION -> SCOPE_CONVERSATION
            else -> SCOPE_BOOK
        },
        trigger = if (trigger.trim().equals(TRIGGER_ON_SAVE, ignoreCase = true)) {
            TRIGGER_ON_SAVE
        } else {
            TRIGGER_MANUAL
        },
        template = body.trim(),
        price = price?.trim()?.takeIf { it.isNotEmpty() },
        catalogUrl = catalogUrl,
    )
}
