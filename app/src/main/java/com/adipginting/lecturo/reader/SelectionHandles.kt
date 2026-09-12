package com.adipginting.lecturo.reader

/**
 * The outer corners of a text selection, in the page's own units: the bottom
 * left of the first bound and the bottom right of the last, which are the two
 * points the viewer tests for a handle drag.
 */
internal data class SelectionBounds(
    val page: Int,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
)

/** Where a page sits in the viewer, in view pixels. */
internal data class PageFrame(val left: Float, val top: Float)

/** Where the two pickers are drawn, in view pixels. */
internal data class SelectionEnds(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
)

/**
 * Places a selection's pickers on screen. The page's bounds are in page units
 * and the frame the viewer reports for that page is in view pixels, so the zoom
 * is what converts one into the other.
 */
internal fun selectionEnds(
    selection: SelectionBounds,
    frame: PageFrame,
    zoom: Float,
): SelectionEnds = SelectionEnds(
    startX = frame.left + selection.startX * zoom,
    startY = frame.top + selection.startY * zoom,
    endX = frame.left + selection.endX * zoom,
    endY = frame.top + selection.endY * zoom,
)
