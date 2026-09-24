package com.adipginting.lecturo.library

import java.io.File
import java.io.InputStream

/**
 * What a file actually holds, judged by its bytes. The name and the server's
 * content type are claims, and download gateways hand out web pages under book
 * names, so the first few bytes are the only evidence worth acting on.
 */
internal object BookFile {

    /** Enough of a file's start to tell a book from a web page. */
    const val HEADER_BYTES = 512

    private val PDF = "%PDF-".toByteArray()

    /** EPUB is a ZIP container: a local file, an empty archive, a spanned one. */
    private val ZIP_HEADERS = listOf(
        byteArrayOf(0x50, 0x4B, 0x03, 0x04),
        byteArrayOf(0x50, 0x4B, 0x05, 0x06),
        byteArrayOf(0x50, 0x4B, 0x07, 0x08),
    )

    /** `"pdf"`, `"epub"`, or null when [header] is neither. */
    fun sniff(header: ByteArray): String? = when {
        header.beginsWith(PDF) -> "pdf"
        ZIP_HEADERS.any { header.beginsWith(it) } -> "epub"
        else -> null
    }

    /** Up to [HEADER_BYTES] from the start of [input]; short reads are normal. */
    fun readHeader(input: InputStream): ByteArray {
        val header = ByteArray(HEADER_BYTES)
        var filled = 0
        while (filled < header.size) {
            val read = input.read(header, filled, header.size - filled)
            if (read <= 0) break
            filled += read
        }
        return header.copyOf(filled)
    }

    /**
     * Why [file] cannot be read as a book, or null when its bytes look like one.
     * This only judges a file's very start, so a ZIP that is not an EPUB passes
     * here and is left to the reader to reject.
     */
    fun problem(file: File): String? {
        if (!file.exists()) return "The file for this document is missing from the library."
        val header = file.inputStream().use(::readHeader)
        return if (sniff(header) == null) describe(header) else null
    }

    /** True when [header] is markup — a download page far more often than a book. */
    fun isWebPage(header: ByteArray): Boolean {
        val start = String(header, Charsets.UTF_8)
            .trimStart('\uFEFF', ' ', '\n', '\r', '\t')
            .lowercase()
        return start.startsWith("<!doctype html") ||
            start.startsWith("<html") ||
            (start.startsWith("<?xml") && start.contains("<html"))
    }

    /**
     * Sentence to show for a payload that is not a book, naming [name] when
     * there is one worth showing.
     */
    fun describe(header: ByteArray, name: String? = null): String {
        val subject = if (name == null) "This file is" else "\"$name\" is"
        return if (isWebPage(header)) {
            "$subject a web page, not a book. Download the book file itself, then import it."
        } else {
            "$subject not a PDF or an EPUB."
        }
    }

    private fun ByteArray.beginsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        return prefix.indices.all { this[it] == prefix[it] }
    }
}
