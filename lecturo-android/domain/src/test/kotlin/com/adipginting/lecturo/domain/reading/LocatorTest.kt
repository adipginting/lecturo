package com.adipginting.lecturo.domain.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A document's stored position is whatever the last reader session left there.
 * Opening must be forgiving: an unreadable PDF position means page one, never a
 * failure to open. An EPUB position is opaque and comes back exactly as stored.
 */
class LocatorTest {

    @Test
    fun `a stored PDF page opens there`() {
        assertEquals(4, Locator.pdf("4").number)
    }

    @Test
    fun `no stored PDF page opens the first page`() {
        assertEquals(1, Locator.pdf(null).number)
    }

    @Test
    fun `page zero opens the first page`() {
        assertEquals(1, Locator.pdf("0").number)
    }

    @Test
    fun `an unreadable PDF position opens the first page`() {
        assertEquals(1, Locator.pdf("epubcfi(/6/4)").number)
    }

    @Test
    fun `an EPUB position is kept verbatim`() {
        val raw = """{"href":"/ch1","locations":{"progression":0.4}}"""
        assertEquals(raw, Locator.epub(raw).raw)
    }

    @Test
    fun `a document with nothing stored has no position`() {
        assertNull(Locator.of(isEpub = false, stored = null))
        assertNull(Locator.of(isEpub = true, stored = "  "))
    }

    @Test
    fun `a position round-trips through the column`() {
        assertEquals("7", Locator.of(isEpub = false, stored = "7")!!.stored)
        assertEquals("{}", Locator.of(isEpub = true, stored = "{}")!!.stored)
    }
}
