package com.adipginting.lecturo.domain.reading

/**
 * Where in a document a position sits. A PDF page is a 1-based number — an
 * unreadable stored value falls back to page one rather than refusing to open
 * the book. An EPUB position is kept opaque: Readium's serialized Locator is
 * stored verbatim and never parsed here, so the engine stays swappable.
 */
sealed interface Locator {
    data class PdfPage(val number: Int) : Locator
    data class OpaqueEpub(val raw: String) : Locator

    /** The value persisted in the locator column. */
    val stored: String
        get() = when (this) {
            is PdfPage -> number.toString()
            is OpaqueEpub -> raw
        }

    companion object {
        /** A stored PDF position, forgiving of anything unreadable. */
        fun pdf(stored: String?): PdfPage =
            PdfPage(stored?.toIntOrNull()?.takeIf { it > 0 } ?: 1)

        fun epub(stored: String): OpaqueEpub = OpaqueEpub(stored)

        /** A document's stored position, or null when nothing is stored. */
        fun of(isEpub: Boolean, stored: String?): Locator? =
            if (stored.isNullOrBlank()) null else if (isEpub) epub(stored) else pdf(stored)
    }
}
