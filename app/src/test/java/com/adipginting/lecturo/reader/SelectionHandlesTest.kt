package com.adipginting.lecturo.reader

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where the pickers go. Page bounds arrive in page units, the viewer reports the
 * page's frame in view pixels, and the two scales have to meet somewhere — this
 * is the one place that happens.
 *
 * The fixture is a real selection on a real page: the numbers were read off the
 * viewer on a device (a word on page 14 of a PDF, drawn at zoom 1.667 in a page
 * frame of 0,-11.667) and the result was checked against the text on screen.
 */
class SelectionHandlesTest {

    private val word = SelectionBounds(
        page = 14,
        startX = 106f,
        startY = 206f,
        endX = 143f,
        endY = 206f,
    )
    private val pageFrame = PageFrame(left = 0f, top = -11.667f)
    private val zoom = 1.6666666f

    @Test
    fun `a selection's ends are scaled into the page frame`() {
        val ends = selectionEnds(word, pageFrame, zoom)
        assertEquals(176.667f, ends.startX, 0.01f)
        assertEquals(331.667f, ends.startY, 0.01f)
        assertEquals(238.333f, ends.endX, 0.01f)
        assertEquals(331.667f, ends.endY, 0.01f)
    }

    @Test
    fun `a scrolled page carries its selection with it`() {
        val scrolled = selectionEnds(word, PageFrame(left = 0f, top = -311.667f), zoom)
        assertEquals(ends(word).startY - 300f, scrolled.startY, 0.01f)
    }

    @Test
    fun `a selection on a page scrolled sideways follows its frame`() {
        val shifted = selectionEnds(word, PageFrame(left = 40f, top = 0f), zoom)
        assertEquals(216.667f, shifted.startX, 0.01f)
    }

    @Test
    fun `zooming in moves the ends further apart`() {
        val zoomed = selectionEnds(word, PageFrame(left = 0f, top = 0f), 3.3333333f)
        assertEquals(353.333f, zoomed.startX, 0.01f)
        assertEquals(476.667f, zoomed.endX, 0.01f)
    }

    private fun ends(selection: SelectionBounds) = selectionEnds(selection, pageFrame, zoom)
}
