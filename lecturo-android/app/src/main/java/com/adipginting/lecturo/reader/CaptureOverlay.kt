package com.adipginting.lecturo.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The screenshot crop overlay: the page dims, a rectangle with grab handles
 * stands over it, and confirming hands the rectangle's area back in overlay
 * pixels. Where those pixels live on screen is the caller's problem — it
 * reports its own window bounds through [onBounds] for exactly that.
 */
@Composable
internal fun CaptureOverlay(
    onBounds: (IntOffset, IntSize) -> Unit,
    onConfirm: (CropRect) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var area by remember { mutableStateOf(IntSize.Zero) }
    var crop by remember(area) {
        mutableStateOf(initialCropRect(area.width.toFloat(), area.height.toFloat()))
    }
    val density = LocalDensity.current
    val slop = with(density) { 24.dp.toPx() }
    val minSize = with(density) { 48.dp.toPx() }
    val borderWidth = with(density) { 2.dp.toPx() }
    val knobRadius = with(density) { 6.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { area = it }
            .onGloballyPositioned {
                val origin = it.positionInWindow()
                onBounds(
                    IntOffset(origin.x.roundToInt(), origin.y.roundToInt()),
                    it.size,
                )
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(area) {
                    var handle: CropHandle? = null
                    detectDragGestures(
                        onDragStart = { pos -> handle = cropHandleAt(crop, pos.x, pos.y, slop) },
                        onDragEnd = { handle = null },
                        onDragCancel = { handle = null },
                    ) { change, drag ->
                        change.consume()
                        val grabbed = handle ?: return@detectDragGestures
                        crop = dragCropRect(
                            rect = crop,
                            handle = grabbed,
                            dx = drag.x,
                            dy = drag.y,
                            bounds = CropRect(0f, 0f, size.width.toFloat(), size.height.toFloat()),
                            minSize = minSize,
                        )
                    }
                },
        ) {
            val scrim = Color.Black.copy(alpha = 0.55f)
            drawRect(scrim, size = Size(size.width, crop.top))
            drawRect(
                scrim,
                topLeft = Offset(0f, crop.bottom),
                size = Size(size.width, size.height - crop.bottom),
            )
            drawRect(
                scrim,
                topLeft = Offset(0f, crop.top),
                size = Size(crop.left, crop.height),
            )
            drawRect(
                scrim,
                topLeft = Offset(crop.right, crop.top),
                size = Size(size.width - crop.right, crop.height),
            )
            drawRect(
                color = Color.White,
                topLeft = Offset(crop.left, crop.top),
                size = Size(crop.width, crop.height),
                style = Stroke(width = borderWidth),
            )
            val midX = (crop.left + crop.right) / 2f
            val midY = (crop.top + crop.bottom) / 2f
            listOf(
                Offset(crop.left, crop.top),
                Offset(crop.right, crop.top),
                Offset(crop.left, crop.bottom),
                Offset(crop.right, crop.bottom),
                Offset(midX, crop.top),
                Offset(midX, crop.bottom),
                Offset(crop.left, midY),
                Offset(crop.right, midY),
            ).forEach { drawCircle(Color.White, radius = knobRadius, center = it) }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = { onConfirm(crop) }) { Text("Capture") }
        }
    }
}
