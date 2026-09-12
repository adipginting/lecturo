package com.adipginting.lecturo.util

/**
 * Converts a small subset of HTML into readable Markdown. Used to freeze
 * WebView selection formatting into the saved/conversation context.
 */
fun htmlToMarkdown(html: String): String {
    val out = StringBuilder()
    var i = 0
    var pendingBlock = false

    fun emitBlockBreak() {
        pendingBlock = true
    }

    fun emit(text: String) {
        if (text.isEmpty()) return
        if (pendingBlock) {
            if (out.isNotEmpty() && !out.endsWith("\n")) out.append("\n")
            out.append("\n")
            pendingBlock = false
        }
        out.append(text)
    }

    fun emitText(text: String) {
        if (text.isBlank()) {
            if (out.isNotEmpty() && !out.endsWith(" ") && !out.endsWith("\n")) {
                out.append(" ")
            }
            return
        }
        val normalized = text.replace(Regex("[ \t]+"), " ")
        emit(normalized)
    }

    while (i < html.length) {
        if (html[i] != '<') {
            val end = html.indexOf('<', i)
            val text = if (end < 0) html.substring(i) else html.substring(i, end)
            emitText(unescapeEntities(text))
            if (end < 0) break
            i = end
            continue
        }
        val close = html.indexOf('>', i)
        if (close < 0) {
            emitText(html.substring(i))
            break
        }
        val rawTag = html.substring(i + 1, close).trim().lowercase()
        i = close + 1
        val closing = rawTag.startsWith("/")
        val name = rawTag.removePrefix("/").takeWhile { it.isLetterOrDigit() }

        when (name) {
            "br" -> if (out.isNotEmpty() && !out.endsWith("\n")) out.append("\n")
            "p", "div", "ul", "ol" -> emitBlockBreak()
            "li" -> {
                emitBlockBreak()
                if (!closing) emit("- ")
            }
            "blockquote" -> {
                emitBlockBreak()
                if (!closing) emit("> ")
            }
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                emitBlockBreak()
                if (!closing) emit("#".repeat(name[1] - '0') + " ")
            }
            "strong", "b" -> emit("**")
            "em", "i" -> emit("*")
            else -> {}
        }
    }

    var result = out.toString().trim()
    result = result.replace(Regex("\n{3,}"), "\n\n")
    return result
}

private fun unescapeEntities(text: String): String {
    return text
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#x27;", "'")
        .replace("&nbsp;", " ")
}
