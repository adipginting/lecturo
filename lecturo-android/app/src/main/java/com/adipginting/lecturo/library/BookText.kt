package com.adipginting.lecturo.library

import com.adipginting.lecturo.util.htmlToMarkdown
import java.io.File
import java.util.zip.ZipFile

/**
 * The text a skill runs against. EPUB only for now: the spine's XHTML in reading
 * order, flattened to Markdown. A PDF has no text to read without a renderer of
 * its own, so it returns null rather than pretending.
 */
internal fun bookText(file: File, format: String): String? = when (format) {
    "epub" -> epubText(file)
    else -> null
}

/** Pure JVM: an EPUB is a zip, so its text can be read without a device. */
internal fun epubText(file: File): String? = runCatching {
    ZipFile(file).use { zip ->
        val container = zip.getEntry("META-INF/container.xml") ?: return@use null
        val containerXml = zip.getInputStream(container).bufferedReader().readText()
        val opfPath = Regex("full-path=\"([^\"]+)\"").find(containerXml)?.groupValues?.get(1)
            ?: return@use null
        val opfEntry = zip.getEntry(opfPath) ?: return@use null
        val opf = zip.getInputStream(opfEntry).bufferedReader().readText()
        val dir = opfPath.substringBeforeLast('/', "")

        val hrefById = Regex("<item\\b[^>]*>").findAll(opf).mapNotNull { m ->
            val id = Regex("id=\"([^\"]+)\"").find(m.value)?.groupValues?.get(1)
            val href = Regex("href=\"([^\"]+)\"").find(m.value)?.groupValues?.get(1)
            if (id != null && href != null) id to href else null
        }.toMap()

        val spine = Regex("<itemref\\b[^>]*>").findAll(opf).mapNotNull { m ->
            Regex("idref=\"([^\"]+)\"").find(m.value)?.groupValues?.get(1)
        }.toList()

        spine.mapNotNull { idref ->
            val href = hrefById[idref] ?: return@mapNotNull null
            val path = if (dir.isEmpty()) href else "$dir/$href"
            val entry = zip.getEntry(path) ?: return@mapNotNull null
            val html = zip.getInputStream(entry).bufferedReader().readText()
            htmlToMarkdown(html).trim().takeIf { it.isNotEmpty() }
        }.joinToString("\n\n").takeIf { it.isNotBlank() }
    }
}.getOrNull()
