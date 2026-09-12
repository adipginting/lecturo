package com.adipginting.lecturo.util

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * Renders a small subset of Markdown into an [AnnotatedString].
 * Supports headings, bold/italic, unordered lists, blockquotes, and plain text.
 */
fun markdownToAnnotatedString(md: String, base: TextStyle): AnnotatedString = buildAnnotatedString {
    val lines = md.lines()
    lines.forEachIndexed { index, raw ->
        if (index > 0) append("\n")
        val line = raw.trimEnd()
        when {
            line.startsWith("# ") -> {
                withStyle(styleFor(base, isHeading = true, level = 1)) {
                    appendInline(line.removePrefix("# ").trim(), base)
                }
            }
            line.startsWith("## ") -> {
                withStyle(styleFor(base, isHeading = true, level = 2)) {
                    appendInline(line.removePrefix("## ").trim(), base)
                }
            }
            line.startsWith("### ") -> {
                withStyle(styleFor(base, isHeading = true, level = 3)) {
                    appendInline(line.removePrefix("### ").trim(), base)
                }
            }
            line.startsWith("#### ") -> {
                withStyle(styleFor(base, isHeading = true, level = 4)) {
                    appendInline(line.removePrefix("#### ").trim(), base)
                }
            }
            line.startsWith("##### ") -> {
                withStyle(styleFor(base, isHeading = true, level = 5)) {
                    appendInline(line.removePrefix("##### ").trim(), base)
                }
            }
            line.startsWith("###### ") -> {
                withStyle(styleFor(base, isHeading = true, level = 6)) {
                    appendInline(line.removePrefix("###### ").trim(), base)
                }
            }
            line.startsWith("> ") -> {
                withStyle(styleFor(base, isQuote = true)) {
                    appendInline(line.removePrefix("> ").trim(), base)
                }
            }
            line.startsWith("- ") || line.startsWith("* ") -> {
                appendInline("• " + line.drop(2), base)
            }
            Regex("^\\d+\\. ").containsMatchIn(line) -> {
                appendInline(line, base)
            }
            line.isBlank() -> {}
            else -> {
                appendInline(line, base)
            }
        }
    }
}

private fun styleFor(
    base: TextStyle,
    isHeading: Boolean = false,
    level: Int = 0,
    isQuote: Boolean = false,
): SpanStyle {
    val size = if (isHeading) {
        when (level) {
            1 -> 22.sp
            2 -> 20.sp
            3 -> 18.sp
            4 -> 17.sp
            5 -> 16.sp
            else -> 15.sp
        }
    } else {
        base.fontSize
    }
    return SpanStyle(
        fontWeight = if (isHeading) FontWeight.Bold else base.fontWeight,
        fontStyle = if (isQuote) FontStyle.Italic else base.fontStyle,
        fontSize = size,
    )
}

private fun AnnotatedString.Builder.appendInline(text: String, base: TextStyle) {
    var i = 0
    while (i < text.length) {
        val bold = text.indexOf("**", i)
        val italic = text.indexOf("*", i)
        val next = when {
            bold >= 0 && (italic < 0 || bold <= italic) -> bold
            italic >= 0 -> italic
            else -> -1
        }
        if (next < 0) {
            append(text.substring(i))
            break
        }
        append(text.substring(i, next))
        val marker = if (text.startsWith("**", next)) "**" else "*"
        val end = text.indexOf(marker, next + marker.length)
        if (end < 0) {
            append(text.substring(next))
            break
        }
        val inner = text.substring(next + marker.length, end)
        val span = if (marker.length == 2) {
            SpanStyle(fontWeight = FontWeight.Bold)
        } else {
            SpanStyle(fontStyle = FontStyle.Italic)
        }
        withStyle(span) { appendInline(inner, base) }
        i = end + marker.length
    }
}

/**
 * Convenience composable that renders markdown with optional line clamping.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = markdownToAnnotatedString(markdown, style),
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
    )
}
