package com.adipginting.lecturo.util

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTextTest {
    private val base = TextStyle()

    @Test
    fun `plain text renders unchanged`() {
        val result = markdownToAnnotatedString("just text", base)
        assertEquals("just text", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `bold markers become a bold span`() {
        val result = markdownToAnnotatedString("a **b** c", base)
        assertEquals("a b c", result.text)
        val bold = result.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertEquals(1, bold.size)
        assertEquals("b", result.text.substring(bold.single().start, bold.single().end))
    }

    @Test
    fun `italic markers become an italic span`() {
        val result = markdownToAnnotatedString("*x*", base)
        assertEquals("x", result.text)
        assertTrue(result.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
    }

    @Test
    fun `heading drops the hashes and renders bold`() {
        val result = markdownToAnnotatedString("# Title", base)
        assertEquals("Title", result.text)
        assertTrue(result.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
    }
}
