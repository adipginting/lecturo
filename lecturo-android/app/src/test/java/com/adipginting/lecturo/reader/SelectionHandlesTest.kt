package com.adipginting.lecturo.reader

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where the pickers go. Page bounds arrive in page units, the viewer reports each
 * page's frame in view pixels, and the two scales have to meet somewhere — this
 * is the one place that happens. A selection can run past a page boundary, so
 * each end is converted against its own page's frame.
 *
 * The fixture is a real selection on a real page: the numbers were read off the
 * viewer on a device (a word on page 14 of a PDF, drawn at zoom 1.667 in a page
 * frame of 0,-11.667) and the result was checked against the text on screen.
 */
class SelectionHandlesTest {

    private val word = SelectionBounds(
        startPage = 14,
        startX = 106f,
        startY = 206f,
        endPage = 14,
        endX = 143f,
        endY = 206f,
    )
    private val pageFrame = PageFrame(left = 0f, top = -11.667f)
    private val zoom = 1.6666666f

    @Test
    fun `a selection's ends are scaled into the page frame`() {
        assertEquals(176.667f, selectionStart(word, pageFrame, zoom).x, 0.01f)
        assertEquals(331.667f, selectionStart(word, pageFrame, zoom).y, 0.01f)
        assertEquals(238.333f, selectionEnd(word, pageFrame, zoom).x, 0.01f)
        assertEquals(331.667f, selectionEnd(word, pageFrame, zoom).y, 0.01f)
    }

    @Test
    fun `a scrolled page carries its selection with it`() {
        val scrolled = selectionStart(word, PageFrame(left = 0f, top = -311.667f), zoom)
        assertEquals(selectionStart(word, pageFrame, zoom).y - 300f, scrolled.y, 0.01f)
    }

    @Test
    fun `a selection on a page scrolled sideways follows its frame`() {
        val shifted = selectionStart(word, PageFrame(left = 40f, top = 0f), zoom)
        assertEquals(216.667f, shifted.x, 0.01f)
    }

    @Test
    fun `zooming in moves the ends further apart`() {
        val start = selectionStart(word, PageFrame(left = 0f, top = 0f), 3.3333333f)
        val end = selectionEnd(word, PageFrame(left = 0f, top = 0f), 3.3333333f)
        assertEquals(353.333f, start.x, 0.01f)
        assertEquals(476.667f, end.x, 0.01f)
    }

    @Test
    fun `each end of a cross-page selection is placed on its own page`() {
        val acrossPages = SelectionBounds(
            startPage = 3,
            startX = 100f,
            startY = 700f,
            endPage = 4,
            endX = 200f,
            endY = 300f,
        )
        val start = selectionStart(acrossPages, PageFrame(left = 0f, top = 0f), 1f)
        val end = selectionEnd(acrossPages, PageFrame(left = 0f, top = 800f), 1f)
        assertEquals(700f, start.y, 0.01f)
        assertEquals(1100f, end.y, 0.01f)
    }
}
