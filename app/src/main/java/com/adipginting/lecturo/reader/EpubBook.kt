package com.adipginting.lecturo.reader

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader
import java.util.zip.ZipFile

/**
 * An unpacked EPUB: knows its spine so the reader can open the right chapter.
 * Spine hrefs are stored root-relative (e.g. "OEBPS/chapter1.xhtml").
 */
class EpubBook(
    val rootDir: File,
    val spineHrefs: List<String>,
) {
    init {
        require(spineHrefs.isNotEmpty()) { "EPUB spine is empty" }
    }

    fun startHref(locator: String?): String =
        if (locator != null && locator in spineHrefs) locator else spineHrefs.first()

    companion object {
        /** Unpacks [epubFile] into [unpackRoot]/<fileName> (cached) and parses its OPF. */
        fun open(epubFile: File, unpackRoot: File): EpubBook {
            val docDir = File(unpackRoot, epubFile.nameWithoutExtension)
            if (!File(docDir, "META-INF/container.xml").exists()) {
                docDir.deleteRecursively()
                docDir.mkdirs()
                unzip(epubFile, docDir)
            }
            val containerXml = File(docDir, "META-INF/container.xml").readText()
            val opfPath = parseContainer(containerXml)
            val opfDir = opfPath.substringBeforeLast('/', "")
            val (manifest, spine) = parseOpf(File(docDir, opfPath).readText())
            val hrefs = spine.mapNotNull { manifest[it] }.map { joinPath(opfDir, it) }
            return EpubBook(docDir, hrefs)
        }

        private fun unzip(zip: File, destDir: File) {
            ZipFile(zip).use { zipFile ->
                for (entry in zipFile.entries()) {
                    if (entry.isDirectory) continue
                    val out = File(destDir, entry.name)
                    // Guard against zip-slip.
                    if (!out.canonicalPath.startsWith(destDir.canonicalPath)) continue
                    out.parentFile?.mkdirs()
                    zipFile.getInputStream(entry).use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }

        private fun joinPath(dir: String, href: String): String =
            if (dir.isEmpty()) href else "$dir/$href"

        internal fun parseContainer(xml: String): String {
            val parser = newParser(xml)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "rootfile") {
                    return parser.getAttributeValue(null, "full-path")
                        ?: throw IllegalArgumentException("rootfile without full-path")
                }
                event = parser.next()
            }
            throw IllegalArgumentException("No rootfile in container.xml")
        }

        /** Returns manifest (id -> href) and spine (ordered idrefs). */
        internal fun parseOpf(xml: String): Pair<Map<String, String>, List<String>> {
            val parser = newParser(xml)
            val manifest = mutableMapOf<String, String>()
            val spine = mutableListOf<String>()
            var inSpine = false
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "spine" -> inSpine = true
                        "item" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val href = parser.getAttributeValue(null, "href")
                            if (id != null && href != null) manifest[id] = href
                        }
                        "itemref" -> if (inSpine) {
                            parser.getAttributeValue(null, "idref")?.let { spine.add(it) }
                        }
                    }
                } else if (event == XmlPullParser.END_TAG && parser.name == "spine") {
                    inSpine = false
                }
                event = parser.next()
            }
            return manifest to spine
        }

        private fun newParser(xml: String): XmlPullParser =
            XmlPullParserFactory.newInstance().newPullParser().apply {
                setInput(StringReader(xml))
            }
    }
}
