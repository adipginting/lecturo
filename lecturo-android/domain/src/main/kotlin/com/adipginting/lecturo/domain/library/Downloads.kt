package com.adipginting.lecturo.domain.library

/**
 * The local document each downloaded remote item opens, newest first. A document
 * with no provenance — imported, or fetched from a plain URL — belongs to no
 * remote item, and a format is its own key, so an EPUB never answers for the PDF
 * beside it. The document is returned whether or not its file still exists; the
 * caller decides to open it or to fetch it again.
 */
fun downloadsByRemoteItem(documents: List<Document>): Map<Provenance, Document> {
    val byKey = mutableMapOf<Provenance, Document>()
    documents.forEach { doc ->
        val provenance = doc.provenance ?: return@forEach
        val current = byKey[provenance]
        if (current == null || compare(doc, current) > 0) {
            byKey[provenance] = doc
        }
    }
    return byKey
}

/** Newest wins; the same millisecond is broken by id, so the choice is stable. */
private fun compare(a: Document, b: Document): Int =
    compareValuesBy(a, b, { it.updatedAt }, { it.id.value })
