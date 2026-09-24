package com.adipginting.lecturo.library

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The cover rules, tested at the byte level: extraction is pure JVM logic and
 * its failure mode — a missing cover — is indistinguishable from a book that
 * declares none, which is exactly why it is pinned here.
 */
class CoversTest {

    private val image = byteArrayOf(1, 2, 3, 4)

    private fun epubFile(opf: String, entries: Map<String, ByteArray> = emptyMap()): File {
        val file = File.createTempFile("cover-test", ".epub")
        file.deleteOnExit()
        ZipOutputStream(file.outputStream()).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            put(
                "META-INF/container.xml",
                (
                    """<?xml version="1.0"?><container><rootfiles>""" +
                        """<rootfile full-path="OEBPS/content.opf"/></rootfiles></container>"""
                    ).toByteArray(),
            )
            put("OEBPS/content.opf", opf.toByteArray())
            entries.forEach { (name, bytes) -> put("OEBPS/$name", bytes) }
        }
        return file
    }

    @Test
    fun `cover declared through the meta tag is read`() {
        val file = epubFile(
            opf = """
                <package><metadata><meta name="cover" content="cov"/></metadata>
                <manifest><item id="cov" href="cover.jpg" media-type="image/jpeg"/></manifest></package>
            """.trimIndent(),
            entries = mapOf("cover.jpg" to image),
        )
        assertArrayEquals(image, epubCoverBytes(file))
    }

    @Test
    fun `cover declared through the cover-image property is read`() {
        val file = epubFile(
            opf = """
                <package><metadata/><manifest>
                <item id="c" properties="cover-image" href="art.png" media-type="image/png"/>
                </manifest></package>
            """.trimIndent(),
            entries = mapOf("art.png" to image),
        )
        assertArrayEquals(image, epubCoverBytes(file))
    }

    @Test
    fun `a book declaring no cover has none`() {
        val file = epubFile(
            opf = """
                <package><metadata/><manifest>
                <item id="ch1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                </manifest></package>
            """.trimIndent(),
            entries = mapOf("chapter1.xhtml" to "<p>text</p>".toByteArray()),
        )
        assertNull(epubCoverBytes(file))
    }

    @Test
    fun `a file that is not an EPUB has no cover`() {
        val notAZip = File.createTempFile("cover-test", ".epub")
        notAZip.deleteOnExit()
        notAZip.writeText("this is not a zip archive")
        assertNull(epubCoverBytes(notAZip))
        assertNull(epubCoverBytes(File("/definitely/not/here.epub")))
    }

    @Test
    fun `a cover is fetched from its URL, and a failure means no cover`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/cover.png") { exchange ->
            exchange.sendResponseHeaders(200, image.size.toLong())
            exchange.responseBody.use { it.write(image) }
        }
        server.createContext("/missing") { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            assertArrayEquals(image, fetchCoverBytes("$base/cover.png"))
            assertNull(fetchCoverBytes("$base/missing"))
        } finally {
            server.stop(0)
        }
    }
}
