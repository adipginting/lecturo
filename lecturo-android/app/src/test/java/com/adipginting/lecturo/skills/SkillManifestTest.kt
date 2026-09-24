package com.adipginting.lecturo.skills

import com.adipginting.lecturo.domain.skills.SCOPE_BOOK
import com.adipginting.lecturo.domain.skills.SCOPE_CONVERSATION
import com.adipginting.lecturo.domain.skills.TRIGGER_MANUAL
import com.adipginting.lecturo.domain.skills.TRIGGER_ON_SAVE
import com.adipginting.lecturo.domain.skills.renderSkill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A skill arrives as a manifest from a catalog and leaves as a row the app can
 * run. Nothing here is trusted: a manifest without the three things a run needs
 * is refused, an unknown scope falls back to the safe one, and a run always
 * carries the book it was attached to.
 */
class SkillManifestTest {

    private fun manifest(
        id: String = "summarise",
        name: String = "Summarise",
        scope: String = "",
        trigger: String = "",
        template: String = "Summarise {{book}}",
    ) = SkillManifest(
        id = id,
        name = name,
        scope = scope,
        trigger = trigger,
        template = template,
    )

    @Test
    fun `a usable manifest becomes an installed skill`() {
        val skill = manifest(scope = "conversation", trigger = "on-save").toSkill("https://x/catalog.json")
        assertEquals("summarise", skill?.id)
        assertEquals("Summarise", skill?.name)
        assertEquals(SCOPE_CONVERSATION, skill?.scope)
        assertEquals(TRIGGER_ON_SAVE, skill?.trigger)
        assertEquals("https://x/catalog.json", skill?.catalogUrl)
    }

    @Test
    fun `an unknown scope and trigger fall back to the safe ones`() {
        val skill = manifest(scope = "whatever", trigger = "whenever").toSkill(null)
        assertEquals(SCOPE_BOOK, skill?.scope)
        assertEquals(TRIGGER_MANUAL, skill?.trigger)
    }

    @Test
    fun `a manifest missing an id, name or template is refused`() {
        assertNull(manifest(id = " ").toSkill(null))
        assertNull(manifest(name = "").toSkill(null))
        assertNull(manifest(template = "  ").toSkill(null))
    }

    @Test
    fun `a run fills the book and the excerpt into the template`() {
        val prompt = renderSkill("About {{excerpt}} in {{book}}", book = "the book", excerpt = "a line")
        assertEquals("About a line in the book", prompt)
    }

    @Test
    fun `a missing excerpt leaves no placeholder behind`() {
        val prompt = renderSkill("About {{excerpt}} in {{book}}", book = "the book", excerpt = null)
        assertFalse(prompt.contains("{{excerpt}}"))
        assertTrue(prompt.contains("the book"))
    }

    @Test
    fun `a template that never names the book still carries it`() {
        val prompt = renderSkill("Do the thing", book = "the book", excerpt = null)
        assertEquals("Do the thing\n\nthe book", prompt)
    }
}
