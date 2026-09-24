package com.adipginting.lecturo.reader

import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.input.DragEvent
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.shared.ExperimentalReadiumApi
import kotlin.math.abs

/** Which way a deliberate sideways swipe turns the chapter. */
internal enum class ChapterTurn { Forward, Backward }

/** How far a drag must travel, as a fraction of the view's width. */
private const val SWIPE_THRESHOLD_PERCENT = 0.2f

/** How much more horizontal than vertical a drag must be to count as a swipe. */
private const val SWIPE_DOMINANCE = 1.5f

/**
 * The chapter turn a drag implies, or null when it is too short or too diagonal
 * to be a sideways swipe: a scroll that drifts sideways must not turn the page.
 */
internal fun chapterTurn(dx: Float, dy: Float, width: Float, rtl: Boolean = false): ChapterTurn? {
    if (abs(dx) < width * SWIPE_THRESHOLD_PERCENT) return null
    if (abs(dx) < abs(dy) * SWIPE_DOMINANCE) return null
    val forward = if (rtl) dx > 0f else dx < 0f
    return if (forward) ChapterTurn.Forward else ChapterTurn.Backward
}

/**
 * Turns the chapter on a deliberate sideways swipe, while the continuous scroll
 * keeps every smaller drag. Readium's own swipe-to-turn is switched off, because
 * it cannot tell a swipe from a scroll; [onTurn] steps the resource the way the
 * chapter bar does, since `goForward` is itself gated by that switch. Nothing
 * turns while a selection is up — the reader is choosing text, not a chapter.
 */
@OptIn(ExperimentalReadiumApi::class)
internal class SwipeChapterNavigation(
    private val navigator: OverflowableNavigator,
    private val onTurn: (ChapterTurn) -> Unit,
    private val isSelecting: () -> Boolean = { false },
) : InputListener {

    override fun onDrag(event: DragEvent): Boolean {
        if (event.type != DragEvent.Type.End) return false
        if (isSelecting()) return false
        val width = navigator.publicationView.width.toFloat()
        if (width <= 0f) return false
        val rtl = navigator.overflow.value.readingProgression == ReadingProgression.RTL
        val turn = chapterTurn(event.offset.x, event.offset.y, width, rtl) ?: return false
        onTurn(turn)
        return true
    }
}
