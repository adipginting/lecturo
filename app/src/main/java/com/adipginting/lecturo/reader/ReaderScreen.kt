package com.adipginting.lecturo.reader

import android.annotation.SuppressLint
import android.content.Context
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.webkit.WebViewAssetLoader
import com.adipginting.lecturo.chat.DraftChat
import com.adipginting.lecturo.chat.DraftChatArgs
import com.adipginting.lecturo.chat.providerDisplayName
import kotlinx.serialization.json.Json
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    docId: String,
    onBack: () -> Unit,
    onOpenDraft: () -> Unit = {},
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
    val context = LocalContext.current
    var showModelPicker by remember { mutableStateOf(false) }
    var jsSelection by remember { mutableStateOf<Pair<String, String?>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val currentLabel = vm.providerOptions
                        .firstOrNull { it.id == vm.selectedProviderId }
                        ?.label
                        ?: providerDisplayName(vm.selectedProviderId)
                    androidx.compose.material3.TextButton(
                        onClick = { showModelPicker = true },
                    ) { Text(currentLabel, maxLines = 1) }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Text(
                    text = state.error,
                    modifier = Modifier.align(Alignment.Center),
                )
                state.url != null -> ReaderWebView(
                    url = state.url,
                    format = state.format,
                    onLocator = vm::saveLocator,
                    onBasket = { raw ->
                        val selection = parseSelectionResult(raw)
                        if (selection != null) {
                            vm.addToBasket(selection.first, selection.second)
                            Toast.makeText(context, "Added to basket", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onAskAi = { raw ->
                        parseSelectionResult(raw)?.let { (text, pageHint) ->
                            openDraft(vm, state.title, text, pageHint, onOpenDraft)
                        }
                    },
                    onSelection = { raw ->
                        parseSelectionResult(raw)?.let { jsSelection = it }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    // Fallback selection made by the JS long-press hook (for WebViews where
    // native selection never starts).
    jsSelection?.let { (text, pageHint) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { jsSelection = null },
            title = { Text("Selected text") },
            text = {
                Text(
                    "\"" + text.take(200) + (if (text.length > 200) "…" else "") + "\"",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        vm.addToBasket(text, pageHint)
                        Toast.makeText(context, "Added to basket", Toast.LENGTH_SHORT).show()
                        jsSelection = null
                    },
                ) { Text("Add to basket") }
            },
            dismissButton = {
                Row {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            openDraft(vm, state.title, text, pageHint, onOpenDraft)
                            jsSelection = null
                        },
                    ) { Text("Ask AI") }
                    androidx.compose.material3.TextButton(
                        onClick = { jsSelection = null },
                    ) { Text("Cancel") }
                }
            },
        )
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
}

/** Receives locator callbacks from the WebView's JS context. */
private class LocatorBridge(
    private val format: String,
    private val onLocator: (String) -> Unit,
    private val onSelection: (String) -> Unit,
) {
    @JavascriptInterface
    fun onPdfPage(page: Int) {
        if (format == "pdf" && page > 0) onLocator(page.toString())
    }

    /** Fired by [LONG_PRESS_HOOK] after a JS-driven word selection. */
    @JavascriptInterface
    fun onLongPressSelection(json: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post { onSelection(json) }
    }
}

/**
 * Long-press fallback: some WebView versions never start native text
 * selection, so do it in JS — a ~600ms stationary touch selects the word
 * under the finger and reports it (same {text, page} payload as
 * [SELECTION_SNIPPET]) to [LocatorBridge.onLongPressSelection]. If the
 * native selection got there first, the hook stays out of the way.
 */
private const val LONG_PRESS_HOOK = """
(function () {
  if (window.__lecturoLp) return;
  window.__lecturoLp = true;
  var timer = null, sx = 0, sy = 0;
  function cancel() { if (timer) { clearTimeout(timer); timer = null; } }
  document.addEventListener('touchstart', function (e) {
    if (e.touches.length !== 1) return;
    sx = e.touches[0].clientX; sy = e.touches[0].clientY;
    cancel();
    timer = setTimeout(function () { selectWordAt(sx, sy); }, 600);
  }, { passive: true });
  document.addEventListener('touchend', cancel, { passive: true });
  document.addEventListener('touchcancel', cancel, { passive: true });
  document.addEventListener('touchmove', function (e) {
    var t = e.touches[0];
    if (!t || Math.abs(t.clientX - sx) > 12 || Math.abs(t.clientY - sy) > 12) cancel();
  }, { passive: true });
  function selectWordAt(x, y) {
    var sel = window.getSelection();
    if (sel && !sel.isCollapsed && sel.toString()) return;
    if (!document.caretRangeFromPoint) return;
    var range = document.caretRangeFromPoint(x, y);
    if (!range || !range.startContainer || range.startContainer.nodeType !== 3) return;
    var node = range.startContainer, text = node.textContent, off = range.startOffset;
    var isWord = function (c) { return /[\w'’-]/.test(c); };
    var start = off, end = off;
    while (start > 0 && isWord(text[start - 1])) start--;
    while (end < text.length && isWord(text[end])) end++;
    if (start === end) return;
    sel.setBaseAndExtent(node, start, node, end);
    var t = sel.toString();
    if (!t) return;
    var page = null;
    var el = node.parentElement;
    var holder = el && el.closest ? el.closest('[data-page-number]') : null;
    if (holder) page = holder.getAttribute('data-page-number');
    window.Lecturo.onLongPressSelection(JSON.stringify({ text: t, page: page }));
  }
})();
"""

/** Tracks the current pdf.js page and reports it to [LocatorBridge]. */
private const val PDF_PAGE_HOOK = """
(function () {
  function attach() {
    var app = window.PDFViewerApplication;
    if (!app || !app.eventBus) { setTimeout(attach, 300); return; }
    app.eventBus.on('pagechanging', function (e) {
      if (window.Lecturo) window.Lecturo.onPdfPage(e.pageNumber);
    });
    // pdf.js's own initial update() can run before the WebView has propagated
    // real layout dimensions, finding zero visible pages and never painting.
    // Nudge the render queue once pages are loaded and layout has settled —
    // twice, because restoring #page=N scrolls programmatically between them
    // and the first pass can miss pages adjacent to the restored one.
    app.eventBus.on('pagesloaded', function () {
      setTimeout(function () { app.pdfViewer.update(); }, 100);
      setTimeout(function () { app.pdfViewer.update(); }, 1000);
    });
  }
  attach();
})();
"""

/**
 * Reads the current text selection as a JSON string `{text, page}`. `page` is
 * taken from the nearest pdf.js page ancestor (`[data-page-number]`); EPUB
 * pages have no such attribute and report null. Returns '' when nothing is
 * selected.
 */
private const val SELECTION_SNIPPET = """
(function () {
  var s = window.getSelection();
  if (!s || s.rangeCount === 0) return '';
  var text = s.toString();
  if (!text) return '';
  var page = null;
  var node = s.anchorNode;
  var el = node ? (node.nodeType === 1 ? node : node.parentElement) : null;
  var holder = el && el.closest ? el.closest('[data-page-number]') : null;
  if (holder) page = holder.getAttribute('data-page-number');
  return JSON.stringify({ text: text, page: page });
})()
"""

/**
 * Parses the [SELECTION_SNIPPET] result delivered by `evaluateJavascript`
 * (a JSON-encoded JS string wrapping the payload). Returns selected text and
 * an optional PDF page hint, or null when the selection was empty.
 */
internal fun parseSelectionResult(raw: String?): Pair<String, String?>? {
    if (raw == null || raw == "null") return null
    val inner = runCatching { Json.decodeFromString<String>(raw) }.getOrNull()
        ?.takeIf { it.isNotBlank() } ?: return null
    val obj = runCatching { Json.decodeFromString<Map<String, String?>>(inner) }.getOrNull()
        ?: return null
    val text = obj["text"]?.trim().orEmpty()
    if (text.isEmpty()) return null
    return text to obj["page"]
}

private fun openDraft(
    vm: ReaderViewModel,
    docTitle: String,
    text: String,
    pageHint: String?,
    onOpenDraft: () -> Unit,
) {
    DraftChat.pending = DraftChatArgs(
        text = text,
        docTitle = docTitle,
        locator = vm.resolveLocator(pageHint),
        basketItemId = null,
    )
    onOpenDraft()
}

/**
 * WebView whose text-selection action mode carries two extra items:
 * "Add to basket" and "Ask AI". Both evaluate [SELECTION_SNIPPET] and report
 * the raw result through their callback.
 */
@SuppressLint("SetJavaScriptEnabled")
private class BasketWebView(
    context: Context,
    private val onBasketResult: (String?) -> Unit,
    private val onAskAiResult: (String?) -> Unit,
) : WebView(context) {

    override fun startActionMode(callback: ActionMode.Callback): ActionMode =
        super.startActionMode(wrap(callback))

    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode =
        super.startActionMode(wrap(callback), type)

    private fun wrap(callback: ActionMode.Callback) = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            // Add ours first so they land in the visible part of the floating
            // toolbar; items added after the system callback's get overflowed.
            menu.add(Menu.NONE, BASKET_ITEM_ID, 1, "Add to basket")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            menu.add(Menu.NONE, ASK_AI_ITEM_ID, 2, "Ask AI")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            callback.onCreateActionMode(mode, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean =
            callback.onPrepareActionMode(mode, menu)

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val callbackFn = when (item.itemId) {
                BASKET_ITEM_ID -> onBasketResult
                ASK_AI_ITEM_ID -> onAskAiResult
                else -> return callback.onActionItemClicked(mode, item)
            }
            // Capture the selection before finishing the mode: finishing
            // collapses the selection and the JS would read nothing.
            evaluateJavascript(SELECTION_SNIPPET) { result ->
                callbackFn(result)
                mode.finish()
            }
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) = callback.onDestroyActionMode(mode)
    }

    private companion object {
        const val BASKET_ITEM_ID = 0xD0CA1
        const val ASK_AI_ITEM_ID = 0xD0CA2
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ReaderWebView(
    url: String,
    format: String,
    onLocator: (String) -> Unit,
    onBasket: (String?) -> Unit,
    onAskAi: (String?) -> Unit,
    onSelection: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val assetLoader = remember { buildAssetLoader(context) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            BasketWebView(ctx, onBasket, onAskAi).apply {
                // Ensure a real size from the start: loading a page before the
                // view is laid out collapses Chromium's initial containing
                // block to zero height and it never recovers (html height:100%
                // and even 100vh compute to 0), which leaves pdf.js with a
                // zero-height viewerContainer so no page ever renders.
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                // pdf.js viewer reads preferences from localStorage during init;
                // without DOM storage the exception leaves the viewer half-initialized
                // (toolbar works, pages never render).
                settings.domStorageEnabled = true
                addJavascriptInterface(LocatorBridge(format, onLocator, onSelection), "Lecturo")
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

                    override fun onPageFinished(view: WebView, loadedUrl: String) {
                        view.evaluateJavascript(LONG_PRESS_HOOK, null)
                        if (format == "pdf" && loadedUrl.contains("viewer.html")) {
                            view.evaluateJavascript(PDF_PAGE_HOOK, null)
                        }
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        if (format == "epub") {
                            epubHrefFromUrl(request.url.toString())?.let(onLocator)
                        }
                        // Let the WebView load it: same-origin content is served by the
                        // asset loader above.
                        return false
                    }
                }
                // Load only once the view has been laid out with real
                // dimensions — see the layoutParams comment above.
                doOnPreDraw { loadUrl(url) }
            }
        },
    )
}

internal fun buildAssetLoader(context: Context): WebViewAssetLoader =
    WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", androidx.webkit.WebViewAssetLoader.AssetsPathHandler(context))
        .addPathHandler(
            "/doc/",
            androidx.webkit.WebViewAssetLoader.InternalStoragePathHandler(
                context,
                File(context.filesDir, "documents"),
            ),
        )
        .addPathHandler(
            "/epub/",
            androidx.webkit.WebViewAssetLoader.InternalStoragePathHandler(
                context,
                File(context.filesDir, "epub"),
            ),
        )
        .build()

/** Extracts the root-relative href from an /epub/<docId>/<href> URL. */
private fun epubHrefFromUrl(url: String): String? {
    val prefix = "${ReaderViewModel.ASSET_ORIGIN}/epub/"
    if (!url.startsWith(prefix)) return null
    val rest = url.removePrefix(prefix)
    val slash = rest.indexOf('/')
    if (slash < 0 || slash == rest.length - 1) return null
    val rawPath = rest.substring(slash + 1).substringBefore('#').substringBefore('?')
    return android.net.Uri.decode(rawPath).takeIf { it.isNotBlank() }
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
private fun ModelPickerDialog(
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
