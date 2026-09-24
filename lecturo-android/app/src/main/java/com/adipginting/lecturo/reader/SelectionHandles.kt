package com.adipginting.lecturo.reader

/**
 * The outer corners of a text selection, in the pages' own units: the bottom
 * left of the first bound and the bottom right of the last. A selection can run
 * past a page boundary, so each end carries the page it belongs to.
 */
internal data class SelectionBounds(
    val startPage: Int,
    val startX: Float,
    val startY: Float,
    val endPage: Int,
    val endX: Float,
    val endY: Float,
)

/** Where a page sits in the viewer, in view pixels. */
internal data class PageFrame(val left: Float, val top: Float)

/** A point in view pixels. */
internal data class ViewPoint(val x: Float, val y: Float)

/**
 * Places a selection's start picker on screen. The page's bounds are in page
 * units and the frame the viewer reports for that page is in view pixels, so
 * the zoom is what converts one into the other.
 */
internal fun selectionStart(selection: SelectionBounds, frame: PageFrame, zoom: Float): ViewPoint =
    ViewPoint(frame.left + selection.startX * zoom, frame.top + selection.startY * zoom)

/**
 * Places a selection's end picker on screen, against its own page's frame: the
 * end of a selection that runs past a page boundary lives on the last page, not
 * the first.
 */
internal fun selectionEnd(selection: SelectionBounds, frame: PageFrame, zoom: Float): ViewPoint =
    ViewPoint(frame.left + selection.endX * zoom, frame.top + selection.endY * zoom)
