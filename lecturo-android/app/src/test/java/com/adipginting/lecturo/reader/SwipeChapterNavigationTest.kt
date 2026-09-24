package com.adipginting.lecturo.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Whether a drag on the scrolling EPUB was a deliberate sideways swipe or just
 * the drift of a vertical scroll. A swipe this short or this diagonal is the
 * reader trying to scroll; only a long, level drag turns the chapter.
 */
class SwipeChapterNavigationTest {

    private val width = 720f

    @Test
    fun `a short sideways drag is not a swipe`() {
        assertNull(chapterTurn(dx = -60f, dy = 0f, width = width))
    }

    @Test
    fun `a long drag to the left goes forward`() {
        assertEquals(ChapterTurn.Forward, chapterTurn(dx = -240f, dy = 20f, width = width))
    }

    @Test
    fun `a long drag to the right goes back`() {
        assertEquals(ChapterTurn.Backward, chapterTurn(dx = 240f, dy = 20f, width = width))
    }

    @Test
    fun `a long but mostly vertical drag is a scroll, not a swipe`() {
        assertNull(chapterTurn(dx = -200f, dy = 400f, width = width))
    }

    @Test
    fun `a drag at the threshold counts`() {
        assertEquals(ChapterTurn.Forward, chapterTurn(dx = -width * 0.2f, dy = 0f, width = width))
    }

    @Test
    fun `a right-to-left book turns the other way`() {
        assertEquals(ChapterTurn.Backward, chapterTurn(dx = -240f, dy = 0f, width = width, rtl = true))
        assertEquals(ChapterTurn.Forward, chapterTurn(dx = 240f, dy = 0f, width = width, rtl = true))
    }
}
