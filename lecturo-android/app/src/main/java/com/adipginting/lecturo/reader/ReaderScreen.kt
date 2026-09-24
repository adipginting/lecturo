package com.adipginting.lecturo.reader

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.adipginting.lecturo.chat.ChatSending
import com.adipginting.lecturo.chat.DraftChatArgs
import com.adipginting.lecturo.chat.providerDisplayName
import com.adipginting.lecturo.domain.reading.ReaderChatTarget
import com.adipginting.lecturo.domain.reading.readerChatTarget
import com.adipginting.lecturo.ui.theme.Chats
import com.adipginting.lecturo.ui.theme.Model
import com.adipginting.lecturo.ui.theme.Screenshot
import com.adipginting.lecturo.ui.theme.Skills
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    docId: String,
    onBack: () -> Unit,
    onOpenSkills: () -> Unit = {},
) {
    val vm: ReaderViewModel = viewModel(
        key = "reader-$docId",
        factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                ReaderViewModel(app, docId)
            }
        },
    )
    val state = vm.uiState
    val activeChat by vm.activeChat.collectAsState()
    val draftChat by vm.draftChat.collectAsState()
    val context = LocalContext.current
    var showModelPicker by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
    var epubHandle by remember { mutableStateOf<EpubReaderHandle?>(null) }
    val panel = remember { ReaderPanelState() }
    var capturing by remember { mutableStateOf(false) }
    var captureOrigin by remember { mutableStateOf(IntOffset.Zero) }
    var captureSize by remember { mutableStateOf(IntSize.Zero) }
    var selectionActive by remember { mutableStateOf(false) }
    var clearSelection by remember { mutableStateOf<(() -> Unit)?>(null) }

    // A selected passage is the innermost thing back can dismiss: it goes before
    // the panel, before crop mode, and long before leaving the document.
    BackHandler(enabled = selectionActive) {
        clearSelection?.invoke()
        selectionActive = false
    }
    // The sheet is not modal, so it does not take the back gesture with it.
    BackHandler(enabled = panel.isOpen) { panel.collapse() }
    // Cropping is a mode of its own; back leaves it without a file written.
    BackHandler(enabled = capturing) { capturing = false }

    // The standard sheet state, with Hidden kept as an anchor: the peek height
    // is zero, so both collapsed anchors — Hidden and the zero-height partial —
    // mean "put away". The state's default skips Hidden — a standard sheet
    // normally cannot be dismissed that way — and `hide()` throws on a state
    // that skips it.
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.Hidden,
            skipHiddenState = false,
        ),
    )
    var movingByUs by remember { mutableStateOf(false) }
    LaunchedEffect(panel.isOpen) {
        movingByUs = true
        if (panel.isOpen) {
            scaffoldState.bottomSheetState.expand()
        } else {
            scaffoldState.bottomSheetState.hide()
        }
        movingByUs = false
    }
    // A sheet that settled somewhere other than where we sent it was dragged,
    // and a drag to either zero-height anchor is the panel being put away —
    // without this the panel stays open but invisible, and the chat icon, finding
    // the same target, does nothing until Back is pressed. A sheet dragged up
    // while nothing was showing becomes the chats list rather than an empty one.
    LaunchedEffect(
        scaffoldState.bottomSheetState.currentValue,
        scaffoldState.bottomSheetState.targetValue,
    ) {
        val sheet = scaffoldState.bottomSheetState
        if (movingByUs || sheet.currentValue != sheet.targetValue) return@LaunchedEffect
        when {
            panel.isOpen && sheet.currentValue != SheetValue.Expanded -> panel.collapse()
            !panel.isOpen && sheet.currentValue == SheetValue.Expanded -> panel.openChats()
        }
    }

    /** Makes a draft from an excerpt and shows its composer, still over the page. */
    fun askAbout(text: String) {
        vm.createDraft(text = text) { id -> panel.openDraft(id) }
    }

    /** Opens crop mode, or explains why the selected model cannot take a picture. */
    fun startScreenshotCapture() {
        if (!vm.visionSupported) {
            Toast.makeText(
                context,
                "This model can't read images — choose a vision-capable one in Settings",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        capturing = true
    }

    /**
     * Copies the confirmed rectangle out of the window and opens a draft with
     * it. PixelCopy rather than `View.draw`: the page is the platform's own
     * rendering surface and only the compositor has its pixels.
     */
    fun confirmCapture(rect: CropRect) {
        capturing = false
        val window = (context as? Activity)?.window ?: return
        val left = (captureOrigin.x + rect.left.roundToInt())
            .coerceIn(captureOrigin.x, captureOrigin.x + captureSize.width)
        val top = (captureOrigin.y + rect.top.roundToInt())
            .coerceIn(captureOrigin.y, captureOrigin.y + captureSize.height)
        val right = (captureOrigin.x + rect.right.roundToInt())
            .coerceIn(captureOrigin.x, captureOrigin.x + captureSize.width)
        val bottom = (captureOrigin.y + rect.bottom.roundToInt())
            .coerceIn(captureOrigin.y, captureOrigin.y + captureSize.height)
        if (right - left <= 0 || bottom - top <= 0) return
        val bitmap = Bitmap.createBitmap(right - left, bottom - top, Bitmap.Config.ARGB_8888)
        PixelCopy.request(
            window,
            Rect(left, top, right, bottom),
            bitmap,
            { result ->
                if (result != PixelCopy.SUCCESS) {
                    Toast.makeText(context, "Couldn't capture the page", Toast.LENGTH_SHORT)
                        .show()
                    return@request
                }
                vm.saveScreenshot(bitmap) { path ->
                    if (path == null) {
                        Toast.makeText(context, "Couldn't save the screenshot", Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        // A screenshot is a draft too: its own new chat.
                        vm.createDraft(text = "", imagePath = path) { id -> panel.openDraft(id) }
                    }
                }
            },
            Handler(Looper.getMainLooper()),
        )
    }

    BottomSheetScaffold(
        // The keyboard lifts the whole sheet, so the composer rides above it
        // instead of being squeezed out of a sheet whose content cannot shrink.
        modifier = Modifier.imePadding(),
        scaffoldState = scaffoldState,
        sheetPeekHeight = 0.dp,
        topBar = {
            TopAppBar(
                title = {
                    // Five actions leave the title one line; a long book name
                    // ellipsizes rather than wrapping the bar into four.
                    Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // This document's chat, then the model picker: reading never
                    // has to be abandoned to reach either.
                    val chatTarget = readerChatTarget(
                        draftId = draftChat?.id,
                        activeConversationId = activeChat?.id,
                    )
                    IconButton(
                        onClick = {
                            when (chatTarget) {
                                ReaderChatTarget.Chats -> panel.openChats()
                                is ReaderChatTarget.Draft -> panel.openDraft(chatTarget.id)
                                is ReaderChatTarget.Conversation ->
                                    panel.openConversation(chatTarget.id)
                            }
                        },
                    ) {
                        // A reply that is still on its way says so even with the
                        // panel put away: the icon's slot becomes the progress.
                        if (ChatSending.any && !panel.isOpen) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.Default.Chats,
                                contentDescription = "Chat about this document",
                                // A document with a chat in reach says so; an empty
                                // one offers the chats list instead, untinted.
                                tint = if (chatTarget == ReaderChatTarget.Chats) {
                                    androidx.compose.material3.LocalContentColor.current
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                    }
                    IconButton(onClick = { panel.openChats() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = "All chats",
                        )
                    }
                    IconButton(onClick = onOpenSkills) {
                        Icon(
                            Icons.Default.Skills,
                            contentDescription = "Skills and insights",
                        )
                    }
                    // Screenshotting rides on the selected model being able to
                    // read images, so the icon is dimmed until one is — the tap
                    // then explains the state instead of doing nothing.
                    IconButton(onClick = { startScreenshotCapture() }) {
                        Icon(
                            Icons.Default.Screenshot,
                            contentDescription = "Screenshot a passage",
                            tint = if (vm.visionSupported) {
                                androidx.compose.material3.LocalContentColor.current
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    val active = vm.providerOptions
                        .firstOrNull { it.id == vm.selectedProviderId }
                    IconButton(onClick = { showModelPicker = true }) {
                        Icon(
                            Icons.Default.Model,
                            contentDescription = active?.label
                                ?: providerDisplayName(vm.selectedProviderId),
                            // An unconfigured active provider is the state where a
                            // chat will fail, so it gets a warning tint.
                            tint = if (active?.enabled == false) {
                                MaterialTheme.colorScheme.error
                            } else {
                                androidx.compose.material3.LocalContentColor.current
                            },
                        )
                    }
                },
            )
        },
        sheetContent = {
            ReaderPanel(
                mode = panel.mode,
                onOpenChats = { panel.openChats() },
                onOpenConversation = { id -> panel.openConversation(id) },
                onCollapse = { panel.collapse() },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.error != null -> Text(
                        text = state.error,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    state.pdfFile != null -> PdfReaderView(
                        file = state.pdfFile,
                        initialPage = state.pdfInitialPage,
                        onPageChanged = { page -> vm.saveLocator(page.toString()) },
                        onAskAi = { text -> askAbout(text) },
                        onSelectionActive = { selectionActive = it },
                        onReady = { clearSelection = it::clearSelection },
                        modifier = Modifier.fillMaxSize(),
                    )

                    state.epubFile != null -> EpubReaderView(
                        file = state.epubFile,
                        initialLocatorJson = state.epubInitialLocator,
                        onLocatorChanged = { json -> vm.saveLocator(json) },
                        onAskAi = { text -> askAbout(text) },
                        onNavigatorReady = {
                            epubHandle = it
                            clearSelection = it::clearSelection
                        },
                        onSelectionActive = { selectionActive = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // Crop mode covers the page, not the chrome: what the rectangle
                // frames is what the screenshot gets.
                if (capturing) {
                    CaptureOverlay(
                        onBounds = { origin, size ->
                            captureOrigin = origin
                            captureSize = size
                        },
                        onConfirm = { confirmCapture(it) },
                        onCancel = { capturing = false },
                    )
                }
            }

            // Chapter stepping sits under the page rather than over it, and only
            // while nothing is covering it: the panel takes this strip's place
            // when it is open.
            val handle = epubHandle
            val chapterBar = !panel.isOpen &&
                state.format == "epub" &&
                handle != null &&
                handle.toc.isNotEmpty()
            if (chapterBar) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(
                        8.dp,
                        Alignment.CenterHorizontally,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.FilledTonalButton(
                        onClick = { handle?.prev() },
                    ) { Text("Previous") }
                    androidx.compose.material3.FilledTonalButton(
                        onClick = { showToc = true },
                    ) { Text("Contents") }
                    androidx.compose.material3.FilledTonalButton(
                        onClick = { handle?.next() },
                    ) { Text("Next") }
                }
            }
        }
    }

    if (showModelPicker) {
        ModelPickerDialog(
            options = vm.providerOptions,
            selectedId = vm.selectedProviderId,
            onDismiss = { showModelPicker = false },
            onSelect = {
                vm.selectProvider(it)
                showModelPicker = false
            },
        )
    }

    if (showToc) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showToc = false },
            title = { Text("Contents") },
            text = {
                androidx.compose.foundation.lazy.LazyColumn {
                    epubHandle?.toc.orEmpty().forEach { entry ->
                        item {
                            Text(
                                text = entry.label,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        epubHandle?.goTo(entry.href)
                                        showToc = false
                                    }
                                    .padding(
                                        start = (16 + entry.depth * 12).dp,
                                        top = 10.dp,
                                        bottom = 10.dp,
                                    ),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showToc = false },
                ) { Text("Close") }
            },
        )
    }
}

@Composable
private fun PromptRadioRow(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        androidx.compose.material3.RadioButton(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
        )
        Text(
            label,
            color = if (enabled) {
                androidx.compose.material3.MaterialTheme.colorScheme.onSurface
            } else {
                androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/**
 * Global model chooser shown from the reader's top bar. Selecting a provider
 * here sets [com.adipginting.lecturo.chat.ChatSettings]' global selection, which
 * new conversations (from "Ask AI" or the chat tab) are created with.
 */
@Composable
internal fun ModelPickerDialog(
    options: List<ReaderViewModel.ProviderOption>,
    selectedId: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Model") },
        text = {
            androidx.compose.foundation.layout.Column {
                options.forEach { option ->
                    PromptRadioRow(
                        label = if (option.enabled) {
                            option.label
                        } else {
                            option.label + " (not configured)"
                        },
                        selected = selectedId == option.id,
                        enabled = option.enabled,
                    ) { onSelect(option.id) }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
