package com.adipginting.lecturo.library

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentDownloaderTest {

    @Test
    fun `title from url path`() {
        assertEquals("my-book", DocumentDownloader.titleFor("https://h.org/lib/my-book.epub"))
        assertEquals("my-book", DocumentDownloader.titleFor("https://h.org/my-book.pdf?token=1"))
        assertEquals("download", DocumentDownloader.titleFor("https://h.org/"))
    }
}
