package com.adipginting.lecturo.util

/**
 * The HTML the math view is handed. Prose keeps its shapes — headings, lists,
 * quotes, emphasis, inline code — and is escaped; a formula is handed over as
 * TeX in a data attribute for the renderer to typeset, never as markup.
 */
internal fun markdownToHtml(markdown: String): String {
    val out = StringBuilder()
    val paragraph = StringBuilder()
    var inList = false
    var firstInParagraph = true

    fun closeParagraph() {
        if (paragraph.isNotEmpty()) {
            out.append("<p>").append(paragraph).append("</p>")
            paragraph.clear()
        }
        firstInParagraph = true
    }

    fun closeList() {
        if (inList) {
            out.append("</ul>")
            inList = false
        }
    }

    splitMath(markdown).forEach { part ->
        when (part) {
            is MathPart.Math -> {
                if (part.display) {
                    closeParagraph()
                    closeList()
                    out.append("<div class=\"math\" data-tex=\"")
                        .append(escapeAttribute(part.tex))
                        .append("\" data-display=\"true\"></div>")
                } else {
                    closeList()
                    paragraph.append("<span class=\"math\" data-tex=\"")
                        .append(escapeAttribute(part.tex))
                        .append("\" data-display=\"false\"></span>")
                    firstInParagraph = false
                }
            }

            is MathPart.Text -> part.text.lines().forEach { raw ->
                val line = escapeText(raw)
                when {
                    line.isBlank() -> {
                        closeParagraph()
                        closeList()
                    }

                    Regex("^#{1,6} ").containsMatchIn(line) -> {
                        closeParagraph()
                        closeList()
                        val level = line.takeWhile { it == '#' }.length
                        out.append("<h$level>").append(inline(line.drop(level).trim())).append("</h$level>")
                    }

                    line.startsWith("&gt; ") -> {
                        closeParagraph()
                        closeList()
                        out.append("<blockquote>").append(inline(line.removePrefix("&gt; "))).append("</blockquote>")
                    }

                    line.startsWith("- ") || line.startsWith("* ") -> {
                        closeParagraph()
                        if (!inList) {
                            out.append("<ul>")
                            inList = true
                        }
                        out.append("<li>").append(inline(line.drop(2))).append("</li>")
                    }

                    else -> {
                        closeList()
                        if (!firstInParagraph && paragraph.isNotEmpty() &&
                            !paragraph.last().isWhitespace() && !line.first().isWhitespace()
                        ) {
                            paragraph.append(' ')
                        }
                        paragraph.append(inline(line))
                        firstInParagraph = false
                    }
                }
            }
        }
    }
    closeParagraph()
    closeList()
    return out.toString()
}

private fun escapeText(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

private fun escapeAttribute(text: String): String = escapeText(text).replace("\"", "&quot;")

private fun inline(text: String): String {
    var out = text
    out = Regex("\\*\\*(.+?)\\*\\*").replace(out) { "<strong>${it.groupValues[1]}</strong>" }
    out = Regex("\\*(.+?)\\*").replace(out) { "<em>${it.groupValues[1]}</em>" }
    out = Regex("`(.+?)`").replace(out) { "<code>${it.groupValues[1]}</code>" }
    return out
}
