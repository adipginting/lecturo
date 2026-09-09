package com.adipginting.lecturo.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelectionParseTest {

    @Test
    fun `parses text and pdf page`() {
        // What evaluateJavascript delivers for JSON.stringify({text, page}).
        val raw = "\"{\\\"text\\\":\\\"hello world\\\",\\\"page\\\":\\\"3\\\"}\""
        assertEquals("hello world" to "3", parseSelectionResult(raw))
    }

    @Test
    fun `page is null for epub selections`() {
        val raw = "\"{\\\"text\\\":\\\"chapter text\\\",\\\"page\\\":null}\""
        assertEquals("chapter text" to null, parseSelectionResult(raw))
    }

    @Test
    fun `empty selection yields null`() {
        assertNull(parseSelectionResult("\"\""))
        assertNull(parseSelectionResult("null"))
        assertNull(parseSelectionResult(null))
        assertNull(parseSelectionResult("\"{\\\"text\\\":\\\"   \\\",\\\"page\\\":null}\""))
    }

    @Test
    fun `malformed payload yields null`() {
        assertNull(parseSelectionResult("\"not json"))
    }
}
