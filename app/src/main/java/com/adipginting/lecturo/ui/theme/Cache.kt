package com.adipginting.lecturo.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Material Symbols-style "cache" glyph: a stack of elliptical discs.
 * Hand-written so we do not need the material-icons-extended dependency.
 */
val Icons.Filled.Cache: ImageVector
    get() = ImageVector.Builder(
        name = "Cache",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        val fill = SolidColor(Color.Black)
        path(fill = fill) { addEllipse(12f, 7f, 9f, 2.5f) }
        path(fill = fill) { addEllipse(12f, 12f, 9f, 2.5f) }
        path(fill = fill) { addEllipse(12f, 17f, 9f, 2.5f) }
    }.build()

/** Approximates an axis-aligned ellipse with four cubic bezier curves. */
private fun PathBuilder.addEllipse(cx: Float, cy: Float, rx: Float, ry: Float) {
    val k = 0.5522848f
    moveTo(cx + rx, cy)
    curveTo(cx + rx, cy + k * ry, cx + k * rx, cy + ry, cx, cy + ry)
    curveTo(cx - k * rx, cy + ry, cx - rx, cy + k * ry, cx - rx, cy)
    curveTo(cx - rx, cy - k * ry, cx - k * rx, cy - ry, cx, cy - ry)
    curveTo(cx + k * rx, cy - ry, cx + rx, cy - k * ry, cx + rx, cy)
    close()
}
