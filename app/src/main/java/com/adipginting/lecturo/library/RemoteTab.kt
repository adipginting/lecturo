package com.adipginting.lecturo.library

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adipginting.lecturo.data.LecturoDatabase
import com.adipginting.lecturo.data.DocumentRepository
import com.adipginting.lecturo.sync.CalibreSource
import com.adipginting.lecturo.sync.RemoteItem
import com.adipginting.lecturo.sync.RemoteLibrarySource
import com.adipginting.lecturo.sync.SyncSettings
import com.adipginting.lecturo.sync.ZoteroSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** UI state of one remote-library tab. */
data class RemoteTabState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val items: List<RemoteItem> = emptyList(),
    val canGoUp: Boolean = false,
)

class SyncViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = SyncSettings(app)
    private val downloader = DocumentDownloader(app, DocumentRepository(app, LecturoDatabase.get(app)))

    val calibreUrl = settings.calibreUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val zoteroUserId = settings.zoteroUserId
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val zoteroApiKey = settings.zoteroApiKey
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val zoteroApiBase = settings.zoteroApiBase
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    var calibre by mutableStateOf(RemoteTabState())
        private set
    var zotero by mutableStateOf(RemoteTabState())
        private set

    /** OPDS browse stack: URLs visited from the root feed. */
    private val calibreStack = ArrayDeque<String>()

    private fun calibreSource() = CalibreSource(calibreUrl.value)

    private fun zoteroSource() = ZoteroSource(
        userId = zoteroUserId.value,
        apiKey = zoteroApiKey.value,
        apiBase = zoteroApiBase.value.ifBlank { "https://api.zotero.org" },
    )

    fun refreshCalibre() = browseCalibre(calibreStack.lastOrNull())

    fun searchCalibre(query: String) = browseCalibre(null, query)

    fun openCalibre(item: RemoteItem, onOpenDocument: (String) -> Unit) {
        val children = item.childrenUrl
        if (children != null) {
            browseCalibre(children)
        } else {
            download(item, onOpenDocument)
        }
    }

    fun calibreUp() {
        if (calibreStack.isNotEmpty()) calibreStack.removeLast()
        browseCalibre(calibreStack.lastOrNull())
    }

    private fun browseCalibre(url: String?, query: String? = null) {
        val source = calibreSource()
        if (!source.isConfigured) return
        viewModelScope.launch {
            calibre = calibre.copy(loading = true, error = null)
            calibre = try {
                val items = if (query != null) source.search(query) else source.browse(url)
                if (query == null) {
                    if (url != null && calibreStack.lastOrNull() != url) calibreStack.addLast(url)
                }
                RemoteTabState(loaded = true, items = items, canGoUp = calibreStack.isNotEmpty())
            } catch (e: Exception) {
                calibre.copy(loading = false, loaded = true, error = e.message ?: "Sync failed")
            }
        }
    }

    fun refreshZotero(query: String? = null) {
        val source = zoteroSource()
        if (!source.isConfigured) return
        viewModelScope.launch {
            zotero = zotero.copy(loading = true, error = null)
            zotero = try {
                val items = if (query != null) source.search(query) else source.browse()
                RemoteTabState(loaded = true, items = items)
            } catch (e: Exception) {
                zotero.copy(loading = false, loaded = true, error = e.message ?: "Sync failed")
            }
        }
    }

    fun download(item: RemoteItem, onOpenDocument: (String) -> Unit) {
        val url = item.downloadUrl ?: return
        viewModelScope.launch {
            calibre = calibre.copy(loading = calibre.loading, error = null)
            try {
                onOpenDocument(
                    downloader.download(url, item.headers, item.title).id,
                )
            } catch (e: Exception) {
                val error = e.message ?: "Download failed"
                calibre = calibre.copy(error = error)
                zotero = zotero.copy(error = error)
            }
        }
    }

    fun saveCalibreUrl(url: String) {
        viewModelScope.launch { settings.saveCalibreUrl(url) }
    }

    fun saveZotero(userId: String, apiKey: String, apiBase: String) {
        viewModelScope.launch { settings.saveZotero(userId, apiKey, apiBase) }
    }
}

/** One remote library tab: config prompt, search + refresh row, item list. */
@Composable
fun RemoteTab(
    sourceName: String,
    configured: Boolean,
    state: RemoteTabState,
    onRefresh: (query: String?) -> Unit,
    onOpen: (RemoteItem) -> Unit,
    onUp: (() -> Unit)?,
    onConfigure: () -> Unit,
) {
    var query by androidx.compose.runtime.remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.canGoUp && onUp != null) {
                IconButton(onClick = onUp) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up")
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search $sourceName") },
                singleLine = true,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            )
            TextButton(onClick = { onRefresh(query.ifBlank { null }) }) {
                Text(if (query.isBlank()) "Sync" else "Search")
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                !configured -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("$sourceName is not configured yet.")
                    TextButton(onClick = onConfigure) { Text("Configure") }
                }
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.items.isEmpty() && state.error == null -> Text(
                    text = "No items. Tap Sync to fetch the catalog.",
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
                state.items.isNotEmpty() -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                ) {
                    items(state.items, key = { it.id + (it.childrenUrl ?: it.downloadUrl ?: "") }) { item ->
                        RemoteItemRow(item, onClick = { onOpen(item) })
                    }
                }
            }
            state.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun RemoteItemRow(item: RemoteItem, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverThumb(
                bitmap = null,
                title = item.title,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = listOfNotNull(
                        item.author,
                        item.format?.uppercase(),
                        if (item.childrenUrl != null) "Catalog" else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (item.childrenUrl != null) {
                    Icons.Default.KeyboardArrowRight
                } else {
                    Icons.Default.KeyboardArrowDown
                },
                contentDescription = if (item.childrenUrl != null) "Open catalog" else "Download",
            )
        }
    }
}
