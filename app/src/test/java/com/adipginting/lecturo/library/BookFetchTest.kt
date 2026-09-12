package com.adipginting.lecturo.library

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress

/**
 * Fetching a book is not one request. Standard Ebooks answers the book URL with
 * a "Your Download Has Started!" page whose meta refresh points at the same URL
 * with `?source=download` — that page is what a previous version saved as an
 * EPUB, which is why the reader refused the result. These tests hold the fetch
 * to the bytes it actually received, and to a bounded number of hops when a
 * server keeps handing out pages.
 */
class BookFetchTest {

    private val epub = "PK\u0003\u0004".toByteArray() + "a real book".toByteArray()
    private val pdf = "%PDF-1.7\nbook".toByteArray()

    /** The gateway page as served, refresh pointing at the query-string URL. */
    private val gateway = """
        <?xml version="1.0" encoding="utf-8"?>
        <!DOCTYPE html>
        <html><head>
        <meta http-equiv="refresh" content="0; url=/book.epub?source=download" />
        </head><body>Your Download Has Started!</body></html>
    """.trimIndent().toByteArray()

    private val selfLoop = """
        <html><head>
        <meta http-equiv="refresh" content="0; url=/loop.epub" />
        </head><body>Round and round</body></html>
    """.trimIndent().toByteArray()

    /** Serves `path?query` keys, 404 for anything else. */
    private fun withServer(
        responses: Map<String, Pair<String, ByteArray>>,
        block: (String) -> Unit,
    ) {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            val uri = exchange.requestURI
            val key = uri.path + (uri.query?.let { "?$it" } ?: "")
            val response = responses[key]
            if (response == null) {
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
            } else {
                val (contentType, body) = response
                exchange.responseHeaders.add("Content-Type", contentType)
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
        }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `a download gateway page is followed to the real book`() = withServer(
        mapOf(
            "/book.epub" to ("application/xhtml+xml" to gateway),
            "/book.epub?source=download" to ("application/epub+zip" to epub),
        ),
    ) { base ->
        BookFetch().fetch("$base/book.epub").use { fetched ->
            assertEquals("epub", fetched.format)
            assertArrayEquals(epub, fetched.body.bytes())
        }
    }

    @Test
    fun `the bytes decide the format, not the content type or the name`() = withServer(
        mapOf("/download.txt" to ("application/octet-stream" to epub)),
    ) { base ->
        BookFetch().fetch("$base/download.txt").use { fetched ->
            assertEquals("epub", fetched.format)
        }
    }

    @Test
    fun `a pdf is recognized from its bytes alone`() = withServer(
        mapOf("/doc" to ("application/octet-stream" to pdf)),
    ) { base ->
        BookFetch().fetch("$base/doc").use { fetched ->
            assertEquals("pdf", fetched.format)
        }
    }

    @Test
    fun `a web page with no way forward is rejected as a web page`() = withServer(
        mapOf("/book.epub" to ("text/html" to "<html><body>Subscribe to read</body></html>".toByteArray())),
    ) { base ->
        val failure = assertThrows(IllegalArgumentException::class.java) {
            BookFetch().fetch("$base/book.epub")
        }
        assertTrue(failure.message!!, failure.message!!.contains("web page"))
    }

    @Test
    fun `a gateway pointing at itself stops instead of looping`() = withServer(
        mapOf("/loop.epub" to ("text/html" to selfLoop)),
    ) { base ->
        assertThrows(IllegalArgumentException::class.java) {
            BookFetch().fetch("$base/loop.epub")
        }
    }

    @Test
    fun `a payload that is neither book nor page is rejected`() = withServer(
        mapOf("/x.epub" to ("text/plain" to "nope".toByteArray())),
    ) { base ->
        val failure = assertThrows(IllegalArgumentException::class.java) {
            BookFetch().fetch("$base/x.epub")
        }
        assertTrue(failure.message!!, failure.message!!.contains("not a PDF"))
    }

    @Test
    fun `an http failure is reported with its status`() = withServer(emptyMap()) { base ->
        val failure = assertThrows(IllegalArgumentException::class.java) {
            BookFetch().fetch("$base/missing.epub")
        }
        assertTrue(failure.message!!, failure.message!!.contains("404"))
    }

    @Test
    fun `the refresh url is read out of the page`() {
        assertEquals("/book.epub?source=download", BookFetch.metaRefreshUrl(String(gateway)))
        assertEquals(
            "/b.epub?x=1&y=2",
            BookFetch.metaRefreshUrl("<meta http-equiv=refresh content='0;url=/b.epub?x=1&amp;y=2'>"),
        )
        assertEquals(
            "/b.epub",
            BookFetch.metaRefreshUrl("""<META HTTP-EQUIV="Refresh" CONTENT="5; URL=/b.epub">"""),
        )
        assertNull(BookFetch.metaRefreshUrl("<html><body>no refresh here</body></html>"))
    }
}
