package com.adipginting.lecturo.reader

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.fragment.compose.AndroidFragment
import com.adipginting.lecturo.util.htmlToMarkdown
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.navigator.SelectableNavigator
import org.readium.r2.navigator.epub.EpubDefaults
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.util.BaseActionModeCallback
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File

/** Navigator actions the reader chrome needs: the TOC and chapter stepping. */
internal class EpubReaderHandle(
    val toc: List<TocEntry>,
    private val navigateTo: (String) -> Unit,
    private val move: (Int) -> Unit,
) {
    fun goTo(href: String) = navigateTo(href)
    fun next() = move(1)
    fun prev() = move(-1)
}

/** One table-of-contents entry: display [label], indented by [depth]. */
internal data class TocEntry(val label: String, val href: String, val depth: Int = 0)

/**
 * Renders an EPUB with the Readium toolkit in continuous scroll mode. Selection
 * still reaches the saved text, and Readium's own JavaScript bridge returns the
 * selected HTML so excerpts keep their formatting.
 *
 * [initialLocatorJson] and [onLocatorChanged] carry Readium's serialized
 * `Locator` JSON, stored opaquely in the document's locator column.
 */
@OptIn(ExperimentalReadiumApi::class)
@Composable
internal fun EpubReaderView(
    file: File,
    initialLocatorJson: String?,
    onLocatorChanged: (String) -> Unit,
    onAddToSaved: (String) -> Unit,
    onAskAi: (String) -> Unit,
    onNavigatorReady: (EpubReaderHandle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()
    var publication by remember(file) { mutableStateOf<Publication?>(null) }
    var error by remember(file) { mutableStateOf<String?>(null) }
    val navigator = remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    val builtHandle = remember { arrayOfNulls<EpubReaderHandle>(1) }

    val addToSaved by rememberUpdatedState(onAddToSaved)
    val askAi by rememberUpdatedState(onAskAi)
    val locatorChanged by rememberUpdatedState(onLocatorChanged)
    val ready by rememberUpdatedState(onNavigatorReady)

    LaunchedEffect(file) {
        val httpClient = DefaultHttpClient()
        val assets = AssetRetriever(context.contentResolver, httpClient)
        val opener = PublicationOpener(
            DefaultPublicationParser(context, httpClient, assets, pdfFactory = null),
        )
        // A Readium failure is an Error value whose toString is an object
        // identity rather than a sentence, so nothing readable is taken from
        // it: this says only that the book would not open.
        val opened = runCatching {
            val asset = assets.retrieve(file).getOrNull() ?: return@runCatching null
            opener.open(asset, allowUserInteraction = true).getOrNull()
        }.getOrNull()
        publication = opened
        error = if (opened == null) "This EPUB could not be opened." else null
    }
    DisposableEffect(publication) {
        val open = publication
        onDispose { open?.close() }
    }

    val pub = publication
    if (pub == null || activity == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                activity == null -> Text("Reader needs a FragmentActivity host")
                else -> CircularProgressIndicator()
            }
        }
        return
    }

    // Readium instantiates the navigator through the Activity's FragmentFactory,
    // so the factory must be installed before AndroidFragment composes.
    remember(pub) {
        val initialLocator = initialLocatorJson?.let { raw ->
            if (raw.trimStart().startsWith("{")) {
                runCatching { Locator.fromJSON(JSONObject(raw)) }.getOrNull()
            } else {
                // Positions written by the old WebView reader are spine hrefs.
                pub.readingOrder
                    .firstOrNull { link -> link.href.toString().endsWith(raw.trimStart('/')) }
                    ?.let { link -> pub.locatorFromLink(link) }
            }
        }
        val callback = object : BaseActionModeCallback() {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.add(Menu.NONE, ADD_TO_BASKET, 1, "Save")
                menu.add(Menu.NONE, ASK_AI, 2, "Ask AI")
                return true
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                val current = navigator.value
                val asking = item.itemId == ASK_AI
                if (current == null || (!asking && item.itemId != ADD_TO_BASKET)) return false
                mode.finish()
                scope.launch {
                    val selection = current.currentSelection() ?: return@launch
                    val text = selection.locator.text.highlight.orEmpty()
                    if (text.isBlank()) return@launch
                    // evaluateJavascript hands back the WebView's JSON-encoded
                    // result, so the string has to be decoded before use.
                    val html = runCatching {
                        current.evaluateJavascript(SELECTION_HTML)
                    }.getOrNull()?.let { raw ->
                        runCatching { org.json.JSONTokener(raw).nextValue() as? String }
                            .getOrNull()
                    }
                    val markdown = html
                        ?.takeIf { it.isNotBlank() }
                        ?.let { htmlToMarkdown(it) }
                        ?.takeIf { it.isNotBlank() }
                    if (asking) askAi(markdown ?: text) else addToSaved(markdown ?: text)
                    current.clearSelection()
                }
                return true
            }
        }
        val navigatorFactory = EpubNavigatorFactory(
            publication = pub,
            configuration = EpubNavigatorFactory.Configuration(
                defaults = EpubDefaults(scroll = true),
            ),
        )
        activity.supportFragmentManager.fragmentFactory = navigatorFactory.createFragmentFactory(
            initialLocator = initialLocator,
            initialPreferences = EpubPreferences(scroll = true),
            configuration = EpubNavigatorFragment.Configuration {
                selectionActionModeCallback = callback
            },
        )
        true
    }

    AndroidFragment(
        clazz = EpubNavigatorFragment::class.java,
        modifier = modifier.fillMaxSize(),
        onUpdate = { fragment ->
            navigator.value = fragment
            if (builtHandle[0] == null) {
                val entries = flattenToc(pub.tableOfContents)
                val handle = EpubReaderHandle(
                    toc = entries.map { it.first },
                    navigateTo = { href ->
                        entries.firstOrNull { it.first.href == href }
                            ?.let { fragment.go(it.second, true) }
                    },
                    move = { direction -> stepResource(fragment, pub, direction) },
                )
                builtHandle[0] = handle
                ready(handle)
            }
        },
    )

    val current = navigator.value
    LaunchedEffect(current) {
        // Factory-time preferences don't always land before the first layout,
        // so the reading mode is applied again once the navigator exists.
        current?.submitPreferences(EpubPreferences(scroll = true))
        current?.currentLocator?.collect { locatorChanged(it.toJSON().toString()) }
    }
}

/** Flattens the publication's TOC, keeping nesting depth for indentation. */
private fun flattenToc(links: List<Link>, depth: Int = 0): List<Pair<TocEntry, Link>> {
    val out = mutableListOf<Pair<TocEntry, Link>>()
    links.forEach { link ->
        out.add(TocEntry(link.title.orEmpty(), link.href.toString(), depth) to link)
        out.addAll(flattenToc(link.children, depth + 1))
    }
    return out
}

/** Moves one step through the reading order, which is what "next chapter" means. */
private fun stepResource(
    fragment: EpubNavigatorFragment,
    publication: Publication,
    direction: Int,
) {
    val order = publication.readingOrder
    val current = fragment.currentLocator.value.href.toString()
    val index = order.indexOfFirst { it.href.toString() == current }
    if (index < 0) return
    order.getOrNull(index + direction)?.let { fragment.go(it, true) }
}

private const val ADD_TO_BASKET = 0x1EC7
private const val ASK_AI = 0x1EC8

/** The selection's HTML, so excerpts keep their formatting. */
private const val SELECTION_HTML = """
(function () {
  var s = window.getSelection();
  if (!s || s.rangeCount === 0) return '';
  var container = document.createElement('div');
  for (var i = 0; i < s.rangeCount; i++) container.appendChild(s.getRangeAt(i).cloneContents());
  return container.innerHTML;
})()
"""
