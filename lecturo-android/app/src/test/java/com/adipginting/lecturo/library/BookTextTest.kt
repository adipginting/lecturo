package com.adipginting.lecturo.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The text a skill reads. An EPUB's spine decides the order, and only the spine
 * is read — a manifest item not referenced by the spine is not part of the book.
 */
class BookTextTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun epub(entries: Map<String, String>): java.io.File {
        val file = folder.newFile("book.epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }

    private val container =
        """<?xml version="1.0"?><container version="1.0"><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>"""

    @Test
    fun `the spine's chapters are read in order`() {
        val file = epub(
            mapOf(
                "META-INF/container.xml" to container,
                "OEBPS/content.opf" to """
                    <package><manifest>
                        <item id="c1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                        <item id="c2" href="ch2.xhtml" media-type="application/xhtml+xml"/>
                        <item id="note" href="notes.xhtml" media-type="application/xhtml+xml"/>
                    </manifest>
                    <spine><itemref idref="c1"/><itemref idref="c2"/></spine></package>
                """.trimIndent(),
                "OEBPS/ch1.xhtml" to "<html><body><p>First chapter.</p></body></html>",
                "OEBPS/ch2.xhtml" to "<html><body><p>Second chapter.</p></body></html>",
                "OEBPS/notes.xhtml" to "<html><body><p>Not in the spine.</p></body></html>",
            ),
        )
        val text = epubText(file)!!
        assertTrue(text.indexOf("First chapter") < text.indexOf("Second chapter"))
        assertTrue(text.contains("First chapter"))
        assertTrue(text.contains("Second chapter"))
        assertEquals(false, text.contains("Not in the spine"))
    }

    @Test
    fun `a file that is not an EPUB has no text`() {
        val file = folder.newFile("not.epub")
        file.writeText("not a zip")
        assertNull(epubText(file))
    }

    @Test
    fun `a PDF has no text to read yet`() {
        assertNull(bookText(folder.newFile("book.pdf"), "pdf"))
    }
}
