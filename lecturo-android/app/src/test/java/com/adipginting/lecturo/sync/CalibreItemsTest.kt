package com.adipginting.lecturo.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Calibre lists a book once per format. Since a downloaded EPUB must not mark
 * the PDF beside it, each format becomes its own item; mirrors of one format
 * collapse to the first link, and a sub-catalog stays a browsing row.
 */
class CalibreItemsTest {

    private fun entry(
        id: String,
        title: String = "Book",
        links: List<Triple<String, String, String?>>,
    ) = OpdsFeed.Entry(id = id, title = title, author = null, links = links)

    private fun acquisition(href: String, type: String?) =
        Triple("http://opds-spec.org/acquisition", href, type)

    @Test
    fun `two formats are two items`() {
        val items = calibreItems(
            listOf(
                entry(
                    "urn:book",
                    links = listOf(
                        acquisition("/files/book.epub", "application/epub+zip"),
                        acquisition("/files/book.pdf", "application/pdf"),
                    ),
                ),
            ),
            feedUrl = "http://host:8080/opds",
        )
        assertEquals(listOf("urn:book#epub", "urn:book#pdf"), items.map { it.id })
        assertEquals(listOf("epub", "pdf"), items.map { it.format })
        assertEquals(listOf(SOURCE_CALIBRE, SOURCE_CALIBRE), items.map { it.source })
    }

    @Test
    fun `mirrors of one format are one item`() {
        val items = calibreItems(
            listOf(
                entry(
                    "id",
                    links = listOf(
                        acquisition("/a.epub", "application/epub+zip"),
                        acquisition("/b.epub", "application/epub+zip"),
                    ),
                ),
            ),
            feedUrl = "http://host/opds",
        )
        assertEquals(1, items.size)
        assertEquals("http://host/a.epub", items.first().downloadUrl)
    }

    @Test
    fun `a blank entry id falls back to the link`() {
        val items = calibreItems(
            listOf(entry("", links = listOf(acquisition("/book.pdf", null)))),
            feedUrl = "http://host/opds",
        )
        assertEquals("http://host/book.pdf#pdf", items.first().id)
    }

    @Test
    fun `a sub-catalog is one browsing row`() {
        val items = calibreItems(
            listOf(
                entry(
                    "cat",
                    title = "Authors",
                    links = listOf(
                        Triple("subsection", "/authors", "application/atom+xml;profile=opds-catalog"),
                    ),
                ),
            ),
            feedUrl = "http://host/opds",
        )
        assertEquals(1, items.size)
        assertEquals("http://host/authors", items.first().childrenUrl)
        assertNull(items.first().downloadUrl)
    }

    @Test
    fun `an entry that is not a book is dropped`() {
        val items = calibreItems(
            listOf(entry("id", links = listOf(acquisition("/cover.jpg", "image/jpeg")))),
            feedUrl = "http://host/opds",
        )
        assertEquals(emptyList<RemoteItem>(), items)
    }

    @Test
    fun `an offered cover is carried on every format row`() {
        val items = calibreItems(
            listOf(
                entry(
                    "urn:book",
                    links = listOf(
                        acquisition("/files/book.epub", "application/epub+zip"),
                        acquisition("/files/book.pdf", "application/pdf"),
                        Triple("http://opds-spec.org/image/thumbnail", "/covers/book.jpg", "image/jpeg"),
                    ),
                ),
            ),
            feedUrl = "http://host/opds",
        )
        assertEquals(listOf("http://host/covers/book.jpg", "http://host/covers/book.jpg"), items.map { it.coverUrl })
    }

    @Test
    fun `a book with no cover link has no cover`() {
        val items = calibreItems(
            listOf(entry("id", links = listOf(acquisition("/book.pdf", "application/pdf")))),
            feedUrl = "http://host/opds",
        )
        assertNull(items.first().coverUrl)
    }
}
