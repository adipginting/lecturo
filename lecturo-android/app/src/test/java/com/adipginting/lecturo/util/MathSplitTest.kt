package com.adipginting.lecturo.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Reading a model's Markdown into text and maths. A formula is whatever is
 * delimited — `$$…$$`, `\[…\]`, `\(…\)`, `$…$` — never whatever looks like one,
 * so prose stays prose and `$5 and $10` stays money. Inline `$` is the one
 * ambiguous delimiter, so it asks for a backslash or a maths-only character
 * inside before it counts.
 */
class MathSplitTest {

    private fun text(s: String) = MathPart.Text(s)
    private fun math(tex: String, display: Boolean = false) = MathPart.Math(tex, display)

    @Test
    fun `prose without delimiters is one text part`() {
        assertEquals(listOf(text("The quick brown fox.")), splitMath("The quick brown fox."))
    }

    @Test
    fun `display delimiters carry their TeX`() {
        assertEquals(listOf(math("E = mc^2", display = true)), splitMath("\$\$E = mc^2\$\$"))
        assertEquals(listOf(math("a + b", display = true)), splitMath("\\[a + b\\]"))
    }

    @Test
    fun `parenthesised delimiters are inline maths`() {
        assertEquals(listOf(math("x^2")), splitMath("\\(x^2\\)"))
    }

    @Test
    fun `maths interleaves with the text around it`() {
        assertEquals(
            listOf(text("Before "), math("x^2"), text(" after")),
            splitMath("Before \$x^2\$ after"),
        )
    }

    @Test
    fun `money is not maths`() {
        assertEquals(
            listOf(text("It costs \$5 and \$10 today")),
            splitMath("It costs \$5 and \$10 today"),
        )
    }

    @Test
    fun `an inline dollar pair needs a maths-only character`() {
        assertEquals(listOf(math("a=1")), splitMath("\$a=1\$"))
        assertEquals(listOf(text("\$abc\$")), splitMath("\$abc\$"))
    }

    @Test
    fun `a delimiter with a space inside is not maths`() {
        assertEquals(listOf(text("\$ x \$")), splitMath("\$ x \$"))
    }

    @Test
    fun `delimiters inside code are left alone`() {
        assertEquals(listOf(text("Use `\$x^2\$` here")), splitMath("Use `\$x^2\$` here"))
        assertEquals(
            listOf(text("```\n\$\$E=mc^2\$\$\n```")),
            splitMath("```\n\$\$E=mc^2\$\$\n```"),
        )
    }

    @Test
    fun `an unmatched dollar is text`() {
        assertEquals(listOf(text("half a \$ sign")), splitMath("half a \$ sign"))
    }
}
