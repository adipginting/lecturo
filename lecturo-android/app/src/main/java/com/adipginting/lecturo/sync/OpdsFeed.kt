package com.adipginting.lecturo.sync

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URI

/** Minimal OPDS (Atom) feed parser for Calibre's Content Server. */
internal object OpdsFeed {

    class Entry(
        val id: String,
        val title: String,
        val author: String?,
        /** rel -> (href, type) */
        val links: List<Triple<String, String, String?>>,
    )

    fun parse(xml: String): List<Entry> {
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(StringReader(xml))
        }
        val entries = mutableListOf<Entry>()
        var id = ""
        var title = ""
        var author: String? = null
        var links = mutableListOf<Triple<String, String, String?>>()
        var inEntry = false
        var inAuthor = false
        var tag = ""
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name
                    when (tag) {
                        "entry" -> {
                            inEntry = true
                            id = ""; title = ""; author = null
                            links = mutableListOf()
                        }
                        "author" -> inAuthor = true
                        "link" -> if (inEntry) {
                            val rel = parser.getAttributeValue(null, "rel") ?: "alternate"
                            val href = parser.getAttributeValue(null, "href")
                            val type = parser.getAttributeValue(null, "type")
                            if (href != null) links.add(Triple(rel, href, type))
                        }
                    }
                }
                XmlPullParser.TEXT -> if (inEntry) {
                    when {
                        tag == "title" -> title += parser.text
                        tag == "id" -> id += parser.text
                        tag == "name" && inAuthor ->
                            author = (author ?: "") + parser.text
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "entry" -> {
                        inEntry = false
                        entries.add(Entry(id.trim(), title.trim(), author?.trim(), links))
                    }
                    "author" -> inAuthor = false
                    else -> if (parser.name == tag) tag = ""
                }
            }
            event = parser.next()
        }
        return entries
    }
}

/** Resolves [href] against [base], tolerating the oddities of OPDS servers. */
internal fun resolveHref(base: String, href: String): String =
    runCatching { URI(base).resolve(href).toString() }.getOrDefault(href)
