package com.adipginting.lecturo.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoteroSourceTest {

    private val source = ZoteroSource(userId = "12345", apiKey = "secret")

    private val items = """
        [
          {
            "key": "ABCD1234",
            "version": 9,
            "data": {
              "itemType": "attachment",
              "title": "Attention Is All You Need",
              "filename": "attention.pdf",
              "contentType": "application/pdf"
            }
          },
          {
            "key": "EPUB9999",
            "data": {
              "itemType": "attachment",
              "title": "",
              "filename": "my-book.epub",
              "contentType": "application/epub+zip"
            }
          },
          {
            "key": "NOTE5555",
            "data": {
              "itemType": "note",
              "note": "irrelevant"
            }
          },
          {
            "key": "SNAP7777",
            "data": {
              "itemType": "attachment",
              "title": "Snapshot",
              "contentType": "text/html"
            }
          }
        ]
    """.trimIndent()

    @Test
    fun `keeps only pdf and epub attachments`() {
        val parsed = source.parseItems(items)
        assertEquals(2, parsed.size)

        val pdf = parsed[0]
        assertEquals("ABCD1234", pdf.id)
        assertEquals("Attention Is All You Need", pdf.title)
        assertEquals("pdf", pdf.format)
        assertEquals(
            "https://api.zotero.org/users/12345/items/ABCD1234/file",
            pdf.downloadUrl,
        )
        assertEquals(mapOf("Zotero-API-Key" to "secret"), pdf.headers)

        val epub = parsed[1]
        assertEquals("my-book", epub.title)
        assertEquals("epub", epub.format)
    }

    @Test
    fun `not configured without credentials`() {
        assert(!ZoteroSource("", "key").isConfigured)
        assert(!ZoteroSource("123", "").isConfigured)
        assert(ZoteroSource("123", "key").isConfigured)
    }
}
