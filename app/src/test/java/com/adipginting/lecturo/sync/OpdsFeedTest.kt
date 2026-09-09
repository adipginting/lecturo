package com.adipginting.lecturo.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpdsFeedTest {

    private val feed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom" xmlns:opds="http://opds-spec.org/2010/catalog">
          <entry>
            <title>By Newest</title>
            <id>calibre-nav:new</id>
            <link type="application/atom+xml;profile=opds-catalog" rel="subsection" href="/opds/new?library_id=Calibre_Library"/>
          </entry>
          <entry>
            <title>A Study in Scarlet</title>
            <id>calibre:book:1</id>
            <author><name>Arthur Conan Doyle</name></author>
            <link type="application/pdf" rel="http://opds-spec.org/acquisition" href="/opds/download/1/PDF"/>
          </entry>
          <entry>
            <title>No links</title>
            <id>calibre:book:2</id>
          </entry>
        </feed>
    """.trimIndent()

    @Test
    fun `parses navigation and acquisition entries`() {
        val entries = OpdsFeed.parse(feed)
        assertEquals(3, entries.size)

        val nav = entries[0]
        assertEquals("By Newest", nav.title)
        assertEquals(1, nav.links.size)
        assertEquals("subsection", nav.links[0].first)
        assertEquals("/opds/new?library_id=Calibre_Library", nav.links[0].second)

        val book = entries[1]
        assertEquals("A Study in Scarlet", book.title)
        assertEquals("Arthur Conan Doyle", book.author)
        assertEquals("http://opds-spec.org/acquisition", book.links[0].first)
        assertEquals("application/pdf", book.links[0].third)

        assertEquals(0, entries[2].links.size)
    }

    @Test
    fun `resolves relative hrefs against the feed url`() {
        assertEquals(
            "http://calibre.local:8080/opds/download/1/PDF",
            resolveHref("http://calibre.local:8080/opds", "/opds/download/1/PDF"),
        )
        // No trailing slash: the last path segment is replaced (RFC 3986).
        assertEquals(
            "http://calibre.local:8080/new?x=1",
            resolveHref("http://calibre.local:8080/opds", "new?x=1"),
        )
        assertEquals(
            "https://cdn.example/x.epub",
            resolveHref("http://calibre.local:8080/opds", "https://cdn.example/x.epub"),
        )
    }
}
