package com.adipginting.lecturo.domain.library

import com.adipginting.lecturo.domain.shared.DocumentId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which remote items a library already holds. The remote list only knows its own
 * ids, so the documents have to answer "do I have this?", per source and per
 * remote item — and a format is a document of its own, so an EPUB never answers
 * for the PDF beside it.
 */
class DownloadsTest {

    private fun doc(
        id: String,
        provenance: Provenance? = null,
        updatedAt: Long = 1,
    ) = Document(
        id = DocumentId(id),
        title = id,
        fileName = "$id.pdf",
        format = DocumentFormat.Pdf,
        provenance = provenance,
        updatedAt = updatedAt,
    )

    @Test
    fun `documents without provenance are not remote downloads`() {
        val map = downloadsByRemoteItem(listOf(doc("local"), doc("url")))
        assertTrue(map.isEmpty())
    }

    @Test
    fun `a remote item maps to the document that came from it`() {
        val map = downloadsByRemoteItem(listOf(doc("z1", Provenance("zotero", "ITEM1"))))
        assertEquals("z1", map[Provenance("zotero", "ITEM1")]?.id?.value)
    }

    @Test
    fun `sources do not answer for each other`() {
        val map = downloadsByRemoteItem(listOf(doc("c1", Provenance("calibre", "7"))))
        assertEquals(null, map[Provenance("zotero", "7")])
    }

    @Test
    fun `formats are separate documents`() {
        val map = downloadsByRemoteItem(
            listOf(
                doc("epub", Provenance("calibre", "7#epub")),
                doc("pdf", Provenance("calibre", "7#pdf")),
            ),
        )
        assertEquals("epub", map[Provenance("calibre", "7#epub")]?.id?.value)
        assertEquals("pdf", map[Provenance("calibre", "7#pdf")]?.id?.value)
    }

    @Test
    fun `the newest copy of a remote item wins`() {
        val map = downloadsByRemoteItem(
            listOf(
                doc("old", Provenance("zotero", "ITEM1"), updatedAt = 100),
                doc("new", Provenance("zotero", "ITEM1"), updatedAt = 200),
            ),
        )
        assertEquals("new", map[Provenance("zotero", "ITEM1")]?.id?.value)
    }

    @Test
    fun `the same millisecond is broken by id, so the choice is stable`() {
        val map = downloadsByRemoteItem(
            listOf(
                doc("a", Provenance("zotero", "ITEM1"), updatedAt = 100),
                doc("b", Provenance("zotero", "ITEM1"), updatedAt = 100),
            ),
        )
        assertEquals("b", map[Provenance("zotero", "ITEM1")]?.id?.value)
    }
}
