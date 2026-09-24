package com.adipginting.lecturo.domain.library

import com.adipginting.lecturo.domain.reading.Locator
import com.adipginting.lecturo.domain.shared.DocumentId

/** The formats the reader can open. */
enum class DocumentFormat(val key: String) {
    Pdf("pdf"),
    Epub("epub"),
    ;

    companion object {
        /** The format a stored `format` column names; anything else reads as PDF. */
        fun of(raw: String): DocumentFormat = if (raw.equals("epub", ignoreCase = true)) Epub else Pdf
    }
}

/** A document's origin in a remote library: its source, and that source's own id. */
data class Provenance(val source: String, val remoteId: String)

/**
 * A document the library owns: a copied file, its format, where it opens, and —
 * when it was downloaded — its provenance.
 */
data class Document(
    val id: DocumentId,
    val title: String,
    /** File name inside filesDir/documents. */
    val fileName: String,
    val format: DocumentFormat,
    /** PDF page / EPUB position, kept as the reader stored it. */
    val lastLocator: Locator? = null,
    val provenance: Provenance? = null,
    val remoteUrl: String? = null,
    val updatedAt: Long = 0,
)
