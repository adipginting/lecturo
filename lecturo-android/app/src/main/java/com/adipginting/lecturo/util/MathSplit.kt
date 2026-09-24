package com.adipginting.lecturo.util

/** A stretch of a model's reply, either prose or a formula. */
internal sealed interface MathPart {
    data class Text(val text: String) : MathPart
    data class Math(val tex: String, val display: Boolean) : MathPart
}

/**
 * Splits Markdown into prose and maths. Display delimiters (`$$…$$`, `\[…\]`)
 * are unambiguous; `\(…\)` is inline. A `$…$` pair counts only when it sits on
 * one line with no space just inside it and carries a backslash or a maths-only
 * character (`^ _ = + /`), which keeps prices out of the renderer. Backtick code,
 * fenced or inline, is never maths.
 */
internal fun splitMath(markdown: String): List<MathPart> {
    val parts = mutableListOf<MathPart>()
    val text = StringBuilder()
    fun flush() {
        if (text.isNotEmpty()) {
            parts += MathPart.Text(text.toString())
            text.clear()
        }
    }

    var i = 0
    while (i < markdown.length) {
        val c = markdown[i]

        if (c == '`') {
            val run = runLength(markdown, i, '`')
            val close = findRun(markdown, i + run, '`', run)
            if (run >= 3 || close >= 0) {
                val end = if (close < 0) markdown.length else close + run
                text.append(markdown, i, end)
                i = end
            } else {
                text.append(c)
                i++
            }
            continue
        }

        if (c == '\\' && i + 1 < markdown.length) {
            val next = markdown[i + 1]
            if (next == '(' || next == '[') {
                val closeTag = if (next == '(') "\\)" else "\\]"
                val end = markdown.indexOf(closeTag, i + 2)
                if (end >= 0) {
                    flush()
                    parts += MathPart.Math(markdown.substring(i + 2, end).trim(), display = next == '[')
                    i = end + 2
                    continue
                }
            }
            text.append(c)
            i++
            continue
        }

        if (c == '$') {
            if (i + 1 < markdown.length && markdown[i + 1] == '$') {
                val end = markdown.indexOf("$$", i + 2)
                if (end >= 0) {
                    flush()
                    parts += MathPart.Math(markdown.substring(i + 2, end).trim(), display = true)
                    i = end + 2
                    continue
                }
            }
            val lineEnd = markdown.indexOf('\n', i)
            val end = markdown.indexOf('$', i + 1)
            if (end >= 0 && (lineEnd < 0 || end < lineEnd)) {
                val body = markdown.substring(i + 1, end)
                if (isInlineMath(body)) {
                    flush()
                    parts += MathPart.Math(body, display = false)
                    i = end + 1
                    continue
                }
            }
            text.append(c)
            i++
            continue
        }

        text.append(c)
        i++
    }
    flush()
    return parts
}

private fun isInlineMath(body: String): Boolean =
    body.isNotEmpty() &&
        !body.first().isWhitespace() &&
        !body.last().isWhitespace() &&
        body.any { it == '\\' || it in "^_=+/" }

private fun runLength(s: String, from: Int, ch: Char): Int {
    var i = from
    while (i < s.length && s[i] == ch) i++
    return i - from
}

private fun findRun(s: String, from: Int, ch: Char, min: Int): Int {
    var i = from
    while (i < s.length) {
        if (s[i] == ch) {
            val n = runLength(s, i, ch)
            if (n >= min) return i
            i += n
        } else {
            i++
        }
    }
    return -1
}
