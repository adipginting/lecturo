package com.adipginting.lecturo.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The crop overlay's geometry: which handle a touch grabs and where a drag
 * leaves the rect. Bounds here are a 1000x2000 content area with a 48px
 * minimum, the same numbers the overlay uses in pixels.
 */
class CropRectTest {

    private val bounds = CropRect(0f, 0f, 1000f, 2000f)
    private val rect = CropRect(200f, 300f, 800f, 900f)
    private val slop = 24f
    private val minSize = 48f

    @Test
    fun `the initial rect is centered at the given fraction`() {
        val initial = initialCropRect(1000f, 2000f, 0.6f)
        assertEquals(200f, initial.left, 0.01f)
        assertEquals(400f, initial.top, 0.01f)
        assertEquals(800f, initial.right, 0.01f)
        assertEquals(1600f, initial.bottom, 0.01f)
    }

    @Test
    fun `a touch on a corner grabs the corner, not an edge`() {
        assertEquals(CropHandle.TopLeft, cropHandleAt(rect, rect.left, rect.top, slop))
        assertEquals(CropHandle.BottomRight, cropHandleAt(rect, rect.right + slop, rect.bottom, slop))
    }

    @Test
    fun `a touch near an edge grabs the edge`() {
        assertEquals(CropHandle.Left, cropHandleAt(rect, rect.left - slop, 600f, slop))
        assertEquals(CropHandle.Top, cropHandleAt(rect, 500f, rect.top + slop, slop))
        assertEquals(CropHandle.Bottom, cropHandleAt(rect, 500f, rect.bottom + slop, slop))
    }

    @Test
    fun `a touch inside the rect grabs the body`() {
        assertEquals(CropHandle.Body, cropHandleAt(rect, 500f, 600f, slop))
    }

    @Test
    fun `a touch outside the halo grabs nothing`() {
        assertNull(cropHandleAt(rect, rect.left - slop - 1f, 600f, slop))
        assertNull(cropHandleAt(rect, 50f, 50f, slop))
    }

    @Test
    fun `dragging the body translates the rect`() {
        val moved = dragCropRect(rect, CropHandle.Body, 50f, -100f, bounds, minSize)
        assertEquals(CropRect(250f, 200f, 850f, 800f), moved)
    }

    @Test
    fun `dragging the body clamps at the bounds without resizing`() {
        val moved = dragCropRect(rect, CropHandle.Body, -500f, 5000f, bounds, minSize)
        assertEquals(0f, moved.left, 0.01f)
        assertEquals(2000f, moved.bottom, 0.01f)
        assertEquals(rect.width, moved.width, 0.01f)
        assertEquals(rect.height, moved.height, 0.01f)
    }

    @Test
    fun `a corner drag resizes from that corner only`() {
        val resized = dragCropRect(rect, CropHandle.TopLeft, 40f, 60f, bounds, minSize)
        assertEquals(CropRect(240f, 360f, 800f, 900f), resized)
    }

    @Test
    fun `an edge drag resizes on one axis only`() {
        val resized = dragCropRect(rect, CropHandle.Right, 80f, 500f, bounds, minSize)
        assertEquals(CropRect(200f, 300f, 880f, 900f), resized)
    }

    @Test
    fun `a resize cannot shrink the rect below the minimum`() {
        val shrunk = dragCropRect(rect, CropHandle.Right, -10_000f, 0f, bounds, minSize)
        assertEquals(rect.left + minSize, shrunk.right, 0.01f)
        val shrunkUp = dragCropRect(rect, CropHandle.Top, 0f, 10_000f, bounds, minSize)
        assertEquals(rect.bottom - minSize, shrunkUp.top, 0.01f)
    }

    @Test
    fun `a resize cannot leave the bounds`() {
        val grown = dragCropRect(rect, CropHandle.BottomRight, 10_000f, 10_000f, bounds, minSize)
        assertEquals(1000f, grown.right, 0.01f)
        assertEquals(2000f, grown.bottom, 0.01f)
        val grownLeft = dragCropRect(rect, CropHandle.Left, -10_000f, 0f, bounds, minSize)
        assertEquals(0f, grownLeft.left, 0.01f)
    }

    @Test
    fun `a clamped edge does not drag the opposite edge along`() {
        val atBound = CropRect(0f, 300f, 800f, 900f)
        val dragged = dragCropRect(atBound, CropHandle.Left, -100f, 0f, bounds, minSize)
        assertEquals(0f, dragged.left, 0.01f)
        assertEquals(800f, dragged.right, 0.01f)
    }

    @Test
    fun `the minimum is capped by the bounds themselves`() {
        val tiny = CropRect(0f, 0f, 30f, 30f)
        val resized = dragCropRect(tiny, CropHandle.Right, -100f, 0f, tiny, minSize)
        assertEquals(0f, resized.left, 0.01f)
        assertEquals(30f, resized.right, 0.01f)
    }
}
