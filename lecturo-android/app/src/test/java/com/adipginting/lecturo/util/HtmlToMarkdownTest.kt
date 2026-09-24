package com.adipginting.lecturo.util

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlToMarkdownTest {
    @Test
    fun headingsKeepTheirLevel() {
        assertEquals("# Title", htmlToMarkdown("<h1>Title</h1>"))
        assertEquals("### Deep", htmlToMarkdown("<h3>Deep</h3>"))
    }

    @Test
    fun boldAndItalicCloseTheirMarkers() {
        assertEquals("**bold**", htmlToMarkdown("<strong>bold</strong>"))
        assertEquals("*italic*", htmlToMarkdown("<em>italic</em>"))
        assertEquals("**bold *it* bold**", htmlToMarkdown("<b>bold <i>it</i> bold</b>"))
    }

    @Test
    fun listItemsBecomeDashes() {
        assertEquals("- one\n\n- two", htmlToMarkdown("<ul><li>one</li><li>two</li></ul>"))
    }

    @Test
    fun blockquoteIsPrefixed() {
        assertEquals("> quoted", htmlToMarkdown("<blockquote>quoted</blockquote>"))
    }

    @Test
    fun paragraphsAreSeparatedByBlankLines() {
        assertEquals("one\n\ntwo", htmlToMarkdown("<p>one</p><p>two</p>"))
    }

    @Test
    fun entitiesAreUnescaped() {
        assertEquals("a & b < c", htmlToMarkdown("<p>a &amp; b &lt; c</p>"))
    }

    @Test
    fun unknownTagsAreStripped() {
        assertEquals("text", htmlToMarkdown("<p><span class=\"x\"><font>text</font></span></p>"))
    }

    @Test
    fun linksKeepTheirTextOnly() {
        assertEquals("click here", htmlToMarkdown("<p><a href=\"http://x\">click here</a></p>"))
    }

    @Test
    fun plainTextPassesThrough() {
        assertEquals("just text", htmlToMarkdown("just text"))
    }
}
