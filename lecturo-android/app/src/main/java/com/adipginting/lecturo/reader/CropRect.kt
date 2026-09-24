package com.adipginting.lecturo.reader

/**
 * The screenshot crop rectangle, in the capture overlay's pixels. All geometry
 * is pure: the overlay renders and feeds touches in, and these functions say
 * which handle a touch grabbed and where the drag leaves the rect.
 */
internal data class CropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** What a touch on the overlay grabbed: a corner, an edge, or the body. */
internal enum class CropHandle {
    Body, TopLeft, TopRight, BottomLeft, BottomRight, Top, Bottom, Left, Right
}

/** The rect capture mode opens with: centered, [fraction] of the area. */
internal fun initialCropRect(
    areaWidth: Float,
    areaHeight: Float,
    fraction: Float = 0.6f,
): CropRect {
    val width = areaWidth * fraction
    val height = areaHeight * fraction
    return CropRect(
        left = (areaWidth - width) / 2f,
        top = (areaHeight - height) / 2f,
        right = (areaWidth + width) / 2f,
        bottom = (areaHeight + height) / 2f,
    )
}

/**
 * Which handle owns a touch at ([x], [y]), given a [slop] halo around the
 * rect's strokes. Corners win over edges and edges over the body, so a fat
 * finger near a corner resizes instead of moving. Outside the halo: nothing.
 */
internal fun cropHandleAt(rect: CropRect, x: Float, y: Float, slop: Float): CropHandle? {
    val nearLeft = kotlin.math.abs(x - rect.left) <= slop
    val nearRight = kotlin.math.abs(x - rect.right) <= slop
    val nearTop = kotlin.math.abs(y - rect.top) <= slop
    val nearBottom = kotlin.math.abs(y - rect.bottom) <= slop
    if (nearLeft && nearTop) return CropHandle.TopLeft
    if (nearRight && nearTop) return CropHandle.TopRight
    if (nearLeft && nearBottom) return CropHandle.BottomLeft
    if (nearRight && nearBottom) return CropHandle.BottomRight
    val withinX = x in (rect.left - slop)..(rect.right + slop)
    val withinY = y in (rect.top - slop)..(rect.bottom + slop)
    if (nearLeft && withinY) return CropHandle.Left
    if (nearRight && withinY) return CropHandle.Right
    if (nearTop && withinX) return CropHandle.Top
    if (nearBottom && withinX) return CropHandle.Bottom
    if (x in rect.left..rect.right && y in rect.top..rect.bottom) return CropHandle.Body
    return null
}

/**
 * The rect after a drag of ([dx], [dy]) on [handle]: the body translates, a
 * corner or edge pulls its sides. The result never shrinks below [minSize] on
 * either axis and never leaves [bounds]; an edge the drag did not touch stays
 * put, so a resize from a clamped side does not drag the opposite side along.
 */
internal fun dragCropRect(
    rect: CropRect,
    handle: CropHandle,
    dx: Float,
    dy: Float,
    bounds: CropRect,
    minSize: Float,
): CropRect {
    val minWidth = minOf(minSize, bounds.width)
    val minHeight = minOf(minSize, bounds.height)
    if (handle == CropHandle.Body) {
        val moveX = dx.coerceIn(bounds.left - rect.left, bounds.right - rect.right)
        val moveY = dy.coerceIn(bounds.top - rect.top, bounds.bottom - rect.bottom)
        return CropRect(
            left = rect.left + moveX,
            top = rect.top + moveY,
            right = rect.right + moveX,
            bottom = rect.bottom + moveY,
        )
    }
    var left = rect.left
    var top = rect.top
    var right = rect.right
    var bottom = rect.bottom
    when (handle) {
        CropHandle.TopLeft -> { left += dx; top += dy }
        CropHandle.TopRight -> { right += dx; top += dy }
        CropHandle.BottomLeft -> { left += dx; bottom += dy }
        CropHandle.BottomRight -> { right += dx; bottom += dy }
        CropHandle.Left -> left += dx
        CropHandle.Right -> right += dx
        CropHandle.Top -> top += dy
        CropHandle.Bottom -> bottom += dy
        CropHandle.Body -> Unit
    }
    // Each moved side is clamped against the side opposite it as that side
    // stands — never against a side this same drag just moved, which is how a
    // drag on one edge would otherwise drag the other along.
    when (handle) {
        CropHandle.TopLeft, CropHandle.BottomLeft, CropHandle.Left ->
            left = left.coerceIn(bounds.left, right - minWidth)
        CropHandle.TopRight, CropHandle.BottomRight, CropHandle.Right ->
            right = right.coerceIn(left + minWidth, bounds.right)
        else -> Unit
    }
    when (handle) {
        CropHandle.TopLeft, CropHandle.TopRight, CropHandle.Top ->
            top = top.coerceIn(bounds.top, bottom - minHeight)
        CropHandle.BottomLeft, CropHandle.BottomRight, CropHandle.Bottom ->
            bottom = bottom.coerceIn(top + minHeight, bounds.bottom)
        else -> Unit
    }
    return CropRect(left, top, right, bottom)
}
