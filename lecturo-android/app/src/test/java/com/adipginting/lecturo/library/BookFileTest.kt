package com.adipginting.lecturo.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

/**
 * A book URL does not always answer with the book, so the bytes in hand — not
 * the name the file arrived under — decide what a file is. These tests pin that
 * verdict for the payloads seen in the wild, and the short-read behaviour of
 * the header reader that content providers make easy to get wrong.
 */
class BookFileTest {

    private val epubHeader = "PK\u0003\u0004".toByteArray() + ByteArray(8)
    private val pdfHeader = "%PDF-1.7\n%\u00e2\u00e3\u00cf\u00d3\n".toByteArray()

    /** Standard Ebooks answers a book URL with this page instead of the book. */
    private val gatewayPage = """
        <?xml version="1.0" encoding="utf-8"?>
        <!DOCTYPE html>
        <html xmlns="http://www.w3.org/1999/xhtml" lang="en-US">
        <head><title>Your Download Has Started!</title></head>
        <body><p>Before you go...</p></body></html>
    """.trimIndent().toByteArray()

    private fun tempFile(bytes: ByteArray): File =
        File.createTempFile("book-file-test", ".bin").apply {
            deleteOnExit()
            writeBytes(bytes)
        }

    @Test
    fun `an epub is a zip container and a pdf carries its own signature`() {
        assertEquals("epub", BookFile.sniff(epubHeader))
        assertEquals("pdf", BookFile.sniff(pdfHeader))
    }

    @Test
    fun `anything else is not a book`() {
        assertNull(BookFile.sniff(ByteArray(0)))
        assertNull(BookFile.sniff("hello world".toByteArray()))
        assertNull(BookFile.sniff(gatewayPage))
    }

    @Test
    fun `markup is recognized as a web page`() {
        assertTrue(BookFile.isWebPage(gatewayPage))
        assertTrue(BookFile.isWebPage("<html>\n<body>hi</body></html>".toByteArray()))
        assertFalse(BookFile.isWebPage(epubHeader))
        assertFalse(BookFile.isWebPage(pdfHeader))
        assertFalse(BookFile.isWebPage("plain text".toByteArray()))
    }

    @Test
    fun `the header reader survives a stream that dribbles out bytes`() {
        val bytes = ByteArray(BookFile.HEADER_BYTES + 100) { 7 }
        val dribble = object : ByteArrayInputStream(bytes) {
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, 1)
        }
        assertEquals(BookFile.HEADER_BYTES, BookFile.readHeader(dribble).size)
    }

    @Test
    fun `the header reader returns whatever a short file has`() {
        assertEquals(3, BookFile.readHeader(ByteArrayInputStream("abc".toByteArray())).size)
    }

    @Test
    fun `a file holding a web page is reported as one`() {
        val problem = BookFile.problem(tempFile(gatewayPage))!!
        assertTrue(problem, problem.contains("web page"))
    }

    @Test
    fun `a missing file says so rather than blaming the book`() {
        val problem = BookFile.problem(File("/definitely/not/here.epub"))!!
        assertTrue(problem, problem.contains("missing"))
    }

    @Test
    fun `a readable book has no problem to report`() {
        assertNull(BookFile.problem(tempFile(epubHeader)))
        assertNull(BookFile.problem(tempFile(pdfHeader)))
    }

    @Test
    fun `an empty file is not a book`() {
        val problem = BookFile.problem(tempFile(ByteArray(0)))!!
        assertTrue(problem, problem.contains("not a PDF"))
    }

    @Test
    fun `a rejection names what arrived when the name is worth showing`() {
        val named = BookFile.describe(gatewayPage, "my-book.epub")
        assertTrue(named, named.contains("my-book.epub"))
        assertTrue(named, named.contains("web page"))
    }
}
