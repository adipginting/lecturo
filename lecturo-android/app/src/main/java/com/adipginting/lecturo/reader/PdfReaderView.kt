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
 * Actions the reader chrome needs from the PDF viewer: dropping a selection so
 * the back gesture dismisses it instead of leaving the document.
 */
internal class PdfReaderHandle internal constructor(private val clear: () -> Unit) {
    fun clearSelection() = clear()
}

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
    onAskAi: (String) -> Unit,
    onSelectionActive: (Boolean) -> Unit = {},
    onReady: (PdfReaderHandle) -> Unit = {},
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
    val askAi by rememberUpdatedState(onAskAi)
    val startPage by rememberUpdatedState(initialPage)
    val selectionActive by rememberUpdatedState(onSelectionActive)
    val ready by rememberUpdatedState(onReady)
    val applied = remember { arrayOfNulls<PdfDocument>(1) }
    val readySent = remember { booleanArrayOf(false) }

    val doc = document
    if (doc == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        }
        return
    }

    val selectedStart = selectedBounds?.let { bounds ->
        pageFrames[bounds.startPage]?.let { frame ->
            selectionStart(bounds, PageFrame(frame.left, frame.top), zoom)
        }
    }
    val selectedEnd = selectedBounds?.let { bounds ->
        pageFrames[bounds.endPage]?.let { frame ->
            selectionEnd(bounds, PageFrame(frame.left, frame.top), zoom)
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
                                selectionActive(first != null && last != null)
                                selectedBounds = if (first == null || last == null) {
                                    null
                                } else {
                                    SelectionBounds(
                                        startPage = first.pageNum,
                                        startX = first.left,
                                        startY = first.bottom,
                                        endPage = last.pageNum,
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
                if (!readySent[0]) {
                    readySent[0] = true
                    ready(PdfReaderHandle { view.clearCurrentSelection() })
                }
            },
        )

        // The pickers do not take touches: the viewer underneath keeps the drag.
        // Each end is drawn only while its own page is on screen, so a selection
        // that runs past a page boundary keeps the end that is still visible.
        selectedStart?.let { SelectionHandle(it.x, it.y, HandleSide.Start) }
        selectedEnd?.let { SelectionHandle(it.x, it.y, HandleSide.End) }
    }
}

private fun selectedText(view: PdfView): String? =
    (view.currentSelection as? TextSelection)?.text?.toString()?.takeIf { it.isNotBlank() }

/** Which end of a selection a picker belongs to: it leans away from the text. */
private enum class HandleSide { Start, End }

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
    val pickerColor = MaterialTheme.colorScheme.primary
    val pickerOnColor = MaterialTheme.colorScheme.onPrimary
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
            color = pickerColor,
            start = Offset(stand, y - 8.dp.toPx()),
            end = Offset(stand, y + foot),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(color = pickerColor, radius = radius, center = knob)
        // A hairline of the on-colour keeps it readable on a page that is not white.
        drawCircle(
            color = pickerOnColor,
            radius = radius,
            center = knob,
            style = Stroke(width = 1.5.dp.toPx()),
        )
    }
}
