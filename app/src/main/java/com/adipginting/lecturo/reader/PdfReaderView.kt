package com.adipginting.lecturo.reader

import android.graphics.RectF
import android.util.SparseArray
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.pdf.PdfDocument
import androidx.pdf.SandboxedPdfLoader
import androidx.pdf.selection.ContextMenuComponent
import androidx.pdf.selection.Selection
import androidx.pdf.selection.SelectionMenuComponent
import androidx.pdf.selection.model.TextSelection
import androidx.pdf.view.PdfView
import java.io.File

/**
 * Renders a PDF through the platform viewer. Text selection uses the system
 * floating menu, with our two items handing the selected text to the saved list.
 * Selections are plain text: there is no HTML here to convert to Markdown.
 *
 * The viewer decides where a selection's drag handles belong and answers touches
 * on them, but does not paint them (see [SelectionHandle]). So the pickers are
 * drawn here, over the page, at the very points the viewer checks for a drag.
 */
@Composable
internal fun PdfReaderView(
    file: File,
    initialPage: Int,
    onPageChanged: (Int) -> Unit,
    onAddToSaved: (String) -> Unit,
    onAskAi: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var document by remember(file) { mutableStateOf<PdfDocument?>(null) }
    var error by remember(file) { mutableStateOf<String?>(null) }

    /** Where each visible page sits in the viewer, and at what zoom. */
    val pageFrames = remember { mutableStateMapOf<Int, RectF>() }
    var zoom by remember { mutableFloatStateOf(1f) }
    var selectedBounds by remember(file) { mutableStateOf<SelectionBounds?>(null) }

    LaunchedEffect(file) {
        error = null
        runCatching { SandboxedPdfLoader(context).openDocument(file.toUri()) }
            .onSuccess { document = it }
            .onFailure { error = "This PDF could not be opened." }
    }
    DisposableEffect(document) {
        val open = document
        onDispose { open?.close() }
    }

    val reportPage by rememberUpdatedState(onPageChanged)
    val addToSaved by rememberUpdatedState(onAddToSaved)
    val askAi by rememberUpdatedState(onAskAi)
    val startPage by rememberUpdatedState(initialPage)
    val applied = remember { arrayOfNulls<PdfDocument>(1) }

    val doc = document
    if (doc == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        }
        return
    }

    val selectedEnds = selectedBounds?.let { bounds ->
        pageFrames[bounds.page]?.let { frame ->
            selectionEnds(bounds, PageFrame(frame.left, frame.top), zoom)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PdfView(ctx).apply {
                    val pdfView = this
                    addOnViewportChangedListener(
                        object : PdfView.OnViewportChangedListener {
                            override fun onViewportChanged(
                                firstVisiblePage: Int,
                                visiblePagesCount: Int,
                                pageLocations: SparseArray<RectF>,
                                zoomLevel: Float,
                            ) {
                                // Persisted locators are 1-based page numbers.
                                reportPage(firstVisiblePage + 1)
                                // The pickers are placed against these frames,
                                // so they follow every scroll, zoom and page.
                                pageFrames.clear()
                                for (i in 0 until pageLocations.size()) {
                                    pageFrames[pageLocations.keyAt(i)] =
                                        RectF(pageLocations.valueAt(i))
                                }
                                zoom = zoomLevel
                            }
                        },
                    )
                    addOnSelectionChangedListener(
                        object : PdfView.OnSelectionChangedListener {
                            override fun onSelectionChanged(newSelection: Selection?) {
                                val bounds = newSelection?.bounds
                                val first = bounds?.firstOrNull()
                                val last = bounds?.lastOrNull()
                                selectedBounds = if (first == null || last == null) {
                                    null
                                } else {
                                    SelectionBounds(
                                        page = first.pageNum,
                                        startX = first.left,
                                        startY = first.bottom,
                                        endX = last.right,
                                        endY = last.bottom,
                                    )
                                }
                            }
                        },
                    )
                    addSelectionMenuItemPreparer(
                        object : PdfView.SelectionMenuItemPreparer {
                            override fun onPrepareSelectionMenuItems(
                                components: MutableList<ContextMenuComponent>,
                            ) {
                                components.add(
                                    SelectionMenuComponent(
                                        key = "save",
                                        label = "Save",
                                        contentDescription = "Save the selected text",
                                    ) {
                                        selectedText(pdfView)?.let(addToSaved)
                                        close()
                                        pdfView.clearCurrentSelection()
                                    },
                                )
                                components.add(
                                    SelectionMenuComponent(
                                        key = "ask_ai",
                                        label = "Ask AI",
                                        contentDescription = "Ask the model about the selected text",
                                    ) {
                                        selectedText(pdfView)?.let(askAi)
                                        close()
                                        pdfView.clearCurrentSelection()
                                    },
                                )
                            }
                        },
                    )
                }
            },
            update = { view ->
                if (applied[0] !== doc) {
                    view.pdfDocument = doc
                    view.scrollToPage(startPage.coerceIn(0, (doc.pageCount - 1).coerceAtLeast(0)))
                    applied[0] = doc
                }
            },
        )

        // The pickers do not take touches: the viewer underneath keeps the drag.
        selectedEnds?.let { SelectionHandle(it.startX, it.startY, HandleSide.Start) }
        selectedEnds?.let { SelectionHandle(it.endX, it.endY, HandleSide.End) }
    }
}

private fun selectedText(view: PdfView): String? =
    (view.currentSelection as? TextSelection)?.text?.toString()?.takeIf { it.isNotBlank() }

/** Which end of a selection a picker belongs to: it leans away from the text. */
private enum class HandleSide { Start, End }

/** A picker has to read against paper, whichever theme the app is in. */
private val PICKER_COLOR = Color(0xFF3700B3)

/**
 * One of the two pickers on a text selection: a bar standing on the selection's
 * edge with a knob at its foot. The knob is the part to pull; the bar is what
 * says which edge you are holding when a selection wraps across lines.
 *
 * The viewer knows where these belong — it answers a drag that starts on either
 * outer corner of a selection — but it does not paint them, and the renderer
 * that would is internal to it. So they are drawn here, with the knob kept
 * inside the box the viewer treats as the handle's touch target. They take no
 * touches: the drag itself stays with the viewer underneath.
 */
@Composable
private fun SelectionHandle(x: Float, y: Float, side: HandleSide) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // The handle stands just outside the text: on the edge itself it would
        // sit on the first or last letter. The knob leans the same way, which
        // keeps it inside the box the viewer has waiting on that side.
        val outward = if (side == HandleSide.End) 1f else -1f
        val radius = 7.5.dp.toPx()
        val foot = radius * 1.6f
        val stand = x + outward * radius
        val knob = Offset(x = stand, y = y + foot)
        drawLine(
            color = PICKER_COLOR,
            start = Offset(stand, y - 8.dp.toPx()),
            end = Offset(stand, y + foot),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(color = PICKER_COLOR, radius = radius, center = knob)
        // A hairline of white keeps it readable on a page that is not white.
        drawCircle(
            color = Color.White,
            radius = radius,
            center = knob,
            style = Stroke(width = 1.5.dp.toPx()),
        )
    }
}
