package com.adipginting.lecturo.library

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import com.adipginting.lecturo.ui.theme.Cache
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adipginting.lecturo.data.LecturoDatabase
import com.adipginting.lecturo.data.DocumentEntity
import com.adipginting.lecturo.data.DocumentRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = DocumentRepository(app, LecturoDatabase.get(app))
    private val importer = DocumentImporter(app, repo)
    private val downloader = DocumentDownloader(app, repo)

    val docs = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var importing by mutableStateOf(false)
        private set

    var importError by mutableStateOf<String?>(null)
        private set

    fun import(uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            importing = true
            importError = null
            try {
                onDone(importer.import(uri).id)
            } catch (e: Exception) {
                importError = e.message ?: "Import failed"
            } finally {
                importing = false
            }
        }
    }

    fun download(url: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            importing = true
            importError = null
            try {
                onDone(downloader.download(url).id)
            } catch (e: Exception) {
                importError = e.message ?: "Download failed"
            } finally {
                importing = false
            }
        }
    }

    fun remove(docs: List<DocumentEntity>) {
        viewModelScope.launch { repo.remove(docs) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenDocument: (String) -> Unit,
    onOpenBasket: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    sharedUrl: String? = null,
    onSharedUrlConsumed: () -> Unit = {},
    vm: LibraryViewModel = viewModel(),
    syncVm: SyncViewModel = viewModel(),
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var showDownloadDialog by androidx.compose.runtime.remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var pendingDelete by remember { mutableStateOf<List<DocumentEntity>?>(null) }
    val docs by vm.docs.collectAsState()

    // A URL shared from another app goes straight into the download flow.
    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            onSharedUrlConsumed()
            vm.download(sharedUrl, onDone = onOpenDocument)
        }
    }

    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) vm.import(uri, onDone = onOpenDocument)
    }

    Scaffold(
        topBar = {
            if (selected.isNotEmpty()) {
                TopAppBar(
                    title = { Text("${selected.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selected = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { pendingDelete = docs.filter { it.id in selected } },
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("lecturo") },
                    actions = {
                        IconButton(onClick = onOpenBasket) {
                            Icon(Icons.Default.Cache, contentDescription = "Basket")
                        }
                        if (selectedTab == 0) {
                            IconButton(onClick = { showDownloadDialog = true }) {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Download from URL",
                                )
                            }
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = {
                        picker.launch(arrayOf("application/pdf", "application/epub+zip"))
                    },
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Import document")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                listOf("Local", "Calibre", "Zotero").forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            selected = emptySet()
                        },
                        text = { Text(title) },
                    )
                }
            }
            when (selectedTab) {
                0 -> LocalTab(
                    vm = vm,
                    onOpenDocument = onOpenDocument,
                    selected = selected,
                    onToggleSelect = { id ->
                        selected = if (id in selected) selected - id else selected + id
                    },
                    onDelete = { doc -> pendingDelete = listOf(doc) },
                )
                1 -> {
                    val calibreUrl by syncVm.calibreUrl.collectAsState()
                    RemoteTab(
                        sourceName = "Calibre",
                        configured = calibreUrl.isNotBlank(),
                        state = syncVm.calibre,
                        onRefresh = { query ->
                            if (query == null) syncVm.refreshCalibre()
                            else syncVm.searchCalibre(query)
                        },
                        onOpen = { item -> syncVm.openCalibre(item, onOpenDocument) },
                        onUp = { syncVm.calibreUp() },
                        onConfigure = onOpenSettings,
                    )
                }
                else -> {
                    val userId by syncVm.zoteroUserId.collectAsState()
                    val apiKey by syncVm.zoteroApiKey.collectAsState()
                    RemoteTab(
                        sourceName = "Zotero",
                        configured = userId.isNotBlank() && apiKey.isNotBlank(),
                        state = syncVm.zotero,
                        onRefresh = { query -> syncVm.refreshZotero(query) },
                        onOpen = { item -> syncVm.download(item, onOpenDocument) },
                        onUp = null,
                        onConfigure = onOpenSettings,
                    )
                }
            }
        }
    }

    if (showDownloadDialog) {
        DownloadDialog(
            onDismiss = { showDownloadDialog = false },
            onDownload = { url ->
                showDownloadDialog = false
                vm.download(url, onDone = onOpenDocument)
            },
        )
    }

    pendingDelete?.let { targets ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = {
                Text(
                    if (targets.size == 1) "Delete \"${targets.first().title}\"?"
                    else "Delete ${targets.size} documents?",
                )
            },
            text = {
                Text("This removes the local copy from this device. Calibre/Zotero libraries are not affected.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.remove(targets)
                        pendingDelete = null
                        selected = emptySet()
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun LocalTab(
    vm: LibraryViewModel,
    onOpenDocument: (String) -> Unit,
    selected: Set<String>,
    onToggleSelect: (String) -> Unit,
    onDelete: (DocumentEntity) -> Unit,
) {
    val docs by vm.docs.collectAsState()
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            vm.importing -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            docs.isEmpty() -> Text(
                text = vm.importError ?: "No documents yet. Tap + to import a PDF or EPUB.",
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                items(docs, key = { it.id }) { doc ->
                    DocumentRow(
                        doc = doc,
                        isSelected = doc.id in selected,
                        selectionMode = selected.isNotEmpty(),
                        onClick = {
                            if (selected.isEmpty()) onOpenDocument(doc.id) else onToggleSelect(doc.id)
                        },
                        onLongClick = { onToggleSelect(doc.id) },
                        onDelete = { onDelete(doc) },
                    )
                }
            }
        }
        vm.importError?.takeIf { docs.isNotEmpty() }?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DocumentRow(
    doc: DocumentEntity,
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        ),
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(doc.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = buildString {
                        append(doc.format.uppercase())
                        doc.lastLocator?.let { locator ->
                            append(" · ")
                            append(
                                if (doc.format == "pdf") "page $locator" else locator,
                            )
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else if (!selectionMode) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

@Composable
private fun DownloadDialog(onDismiss: () -> Unit, onDownload: (String) -> Unit) {
    var url by androidx.compose.runtime.remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download document") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("PDF or EPUB URL") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onDownload(url.trim()) },
                enabled = url.isNotBlank(),
            ) { Text("Download") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
