package com.adipginting.lecturo.reader

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.adipginting.lecturo.saved.SavedRowCard
import com.adipginting.lecturo.saved.SavedViewModel
import com.adipginting.lecturo.chat.DraftChat
import com.adipginting.lecturo.chat.DraftChatArgs
import com.adipginting.lecturo.chat.providerDisplayName
import com.adipginting.lecturo.ui.theme.Chats
import com.adipginting.lecturo.ui.theme.Model
import com.adipginting.lecturo.ui.theme.Saved

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    docId: String,
    onBack: () -> Unit,
    onOpenDraft: () -> Unit = {},
    onOpenChats: () -> Unit = {},
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
    var showToc by remember { mutableStateOf(false) }
    var showSaved by remember { mutableStateOf(false) }
    var epubHandle by remember { mutableStateOf<EpubReaderHandle?>(null) }

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
                    // Saved and chat share one icon, left of the model picker, so
                    // reading never has to be abandoned to reach either.
                    IconButton(onClick = { showSaved = true }) {
                        Icon(Icons.Default.Saved, contentDescription = "Saved and chats")
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
        bottomBar = {
            val handle = epubHandle
            if (state.format == "epub" && handle != null && handle.toc.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.TextButton(
                        onClick = { handle.prev() },
                    ) { Text("Previous") }
                    androidx.compose.material3.TextButton(
                        onClick = { showToc = true },
                        modifier = Modifier.weight(1f),
                    ) { Text("Contents") }
                    androidx.compose.material3.TextButton(
                        onClick = { handle.next() },
                    ) { Text("Next") }
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
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
                    onAddToSaved = { text ->
                        vm.addToSaved(text, null)
                        Toast.makeText(context, "Added to saved", Toast.LENGTH_SHORT).show()
                    },
                    onAskAi = { text -> openDraft(vm, state.title, text, null, onOpenDraft) },
                    modifier = Modifier.fillMaxSize(),
                )

                state.epubFile != null -> EpubReaderView(
                    file = state.epubFile,
                    initialLocatorJson = state.epubInitialLocator,
                    onLocatorChanged = { json -> vm.saveLocator(json) },
                    onAddToSaved = { text ->
                        vm.addToSaved(text, null)
                        Toast.makeText(context, "Added to saved", Toast.LENGTH_SHORT).show()
                    },
                    onAskAi = { text -> openDraft(vm, state.title, text, null, onOpenDraft) },
                    onNavigatorReady = { epubHandle = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (showSaved) {
        val savedVm: SavedViewModel = viewModel()
        val items by savedVm.items.collectAsState()
        ModalBottomSheet(onDismissRequest = { showSaved = false }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Saved",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.TextButton(
                    onClick = {
                        showSaved = false
                        onOpenChats()
                    },
                ) {
                    Icon(
                        Icons.Default.Chats,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text("Chats")
                }
            }
            if (items.isEmpty()) {
                Text(
                    text = "Nothing waiting. Select text while reading and tap \"Add to saved\".",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    items.forEach { row ->
                        item(key = row.item.id) {
                            SavedRowCard(
                                row = row,
                                onClick = {
                                    DraftChat.pending = DraftChatArgs(
                                        text = row.item.text,
                                        docTitle = row.docTitle,
                                        locator = row.item.locator,
                                        savedItemId = row.item.id,
                                    )
                                    showSaved = false
                                    onOpenDraft()
                                },
                                onRemove = { savedVm.remove(row.item.id) },
                            )
                        }
                    }
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
        savedItemId = null,
    )
    onOpenDraft()
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
