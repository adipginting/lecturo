package com.adipginting.lecturo.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentDownloaderTest {

    @Test
    fun `format from content type`() {
        assertEquals("pdf", DocumentDownloader.formatFor("application/pdf", "http://x/y"))
        assertEquals("epub", DocumentDownloader.formatFor("application/epub+zip", "http://x/y"))
        assertEquals("pdf", DocumentDownloader.formatFor("application/pdf; charset=binary", "http://x"))
    }

    @Test
    fun `format falls back to url extension`() {
        assertEquals("pdf", DocumentDownloader.formatFor("application/octet-stream", "http://x/a.pdf"))
        assertEquals("epub", DocumentDownloader.formatFor(null, "http://x/a.epub?dl=1"))
        assertNull(DocumentDownloader.formatFor("text/html", "http://x/a.html"))
    }

    @Test
    fun `title from url path`() {
        assertEquals("my-book", DocumentDownloader.titleFor("https://h.org/lib/my-book.epub"))
        assertEquals("my-book", DocumentDownloader.titleFor("https://h.org/my-book.pdf?token=1"))
        assertEquals("download", DocumentDownloader.titleFor("https://h.org/"))
    }
}
