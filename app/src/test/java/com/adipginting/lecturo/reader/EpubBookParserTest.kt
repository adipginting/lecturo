package com.adipginting.lecturo.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubBookParserTest {

    @Test
    fun `parseContainer extracts rootfile path`() {
        val xml = """
            <?xml version="1.0"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
        """.trimIndent()
        assertEquals("OEBPS/content.opf", EpubBook.parseContainer(xml))
    }

    @Test
    fun `parseOpf maps manifest and orders spine`() {
        val xml = """
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <manifest>
                <item id="ch1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                <item id="ch2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
                <item id="css" href="style.css" media-type="text/css"/>
              </manifest>
              <spine>
                <itemref idref="ch1"/>
                <itemref idref="ch2"/>
              </spine>
            </package>
        """.trimIndent()
        val (manifest, spine) = EpubBook.parseOpf(xml)
        assertEquals("chapter1.xhtml", manifest["ch1"])
        assertEquals("style.css", manifest["css"])
        assertEquals(listOf("ch1", "ch2"), spine)
    }

    @Test
    fun `startHref falls back to first spine item`() {
        val book = EpubBook(java.io.File("/nonexistent"), listOf("OEBPS/a.xhtml", "OEBPS/b.xhtml"))
        assertEquals("OEBPS/a.xhtml", book.startHref(null))
        assertEquals("OEBPS/a.xhtml", book.startHref("OEBPS/gone.xhtml"))
        assertEquals("OEBPS/b.xhtml", book.startHref("OEBPS/b.xhtml"))
    }

    @Test
    fun `parseContainer throws without rootfile`() {
        try {
            EpubBook.parseContainer("<container><rootfiles/></container>")
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("rootfile"))
        }
    }
}
