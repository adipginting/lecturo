package com.adipginting.lecturo.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The HTML the math view is handed. Prose keeps its shapes — headings, bold,
 * italics, lists, quotes, code — and everything is escaped, while formulas are
 * handed over as TeX for KaTeX to render, never as markup.
 */
class MarkdownHtmlTest {

    @Test
    fun `markup in prose is escaped`() {
        assertEquals("<p>a &lt; b &amp; c</p>", markdownToHtml("a < b & c"))
    }

    @Test
    fun `headings, lists and quotes keep their shape`() {
        assertEquals("<h2>Title</h2><p>Text</p>", markdownToHtml("## Title\nText"))
        assertEquals("<ul><li>one</li><li>two</li></ul>", markdownToHtml("- one\n- two"))
        assertEquals("<blockquote>quoted</blockquote>", markdownToHtml("> quoted"))
    }

    @Test
    fun `emphasis and code become tags`() {
        assertEquals("<p><strong>bold</strong> and <em>ital</em></p>", markdownToHtml("**bold** and *ital*"))
        assertEquals("<p><code>x = 1</code></p>", markdownToHtml("`x = 1`"))
    }

    @Test
    fun `a formula is handed over as TeX data`() {
        assertEquals(
            "<p>Before <span class=\"math\" data-tex=\"x^2\" data-display=\"false\"></span> after</p>",
            markdownToHtml("Before \$x^2\$ after"),
        )
    }

    @Test
    fun `display maths is a block`() {
        assertEquals(
            "<div class=\"math\" data-tex=\"E = mc^2\" data-display=\"true\"></div>",
            markdownToHtml("\$\$E = mc^2\$\$"),
        )
    }

    @Test
    fun `a price is still prose`() {
        assertEquals("<p>It costs \$5 and \$10</p>", markdownToHtml("It costs \$5 and \$10"))
    }
}
