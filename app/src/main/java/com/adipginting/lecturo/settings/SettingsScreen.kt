package com.adipginting.lecturo.settings

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adipginting.lecturo.chat.ALL_PROVIDER_IDS
import com.adipginting.lecturo.chat.ChatSettings
import com.adipginting.lecturo.chat.ModelCatalog
import com.adipginting.lecturo.chat.ProviderSettings
import com.adipginting.lecturo.chat.providerDisplayName
import com.adipginting.lecturo.data.ChatRepository
import com.adipginting.lecturo.data.LecturoDatabase
import com.adipginting.lecturo.data.PromptEntity
import com.adipginting.lecturo.sync.SyncSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val chatSettings = ChatSettings(app)
    private val syncSettings = SyncSettings(app)
    private val repo = ChatRepository(LecturoDatabase.get(app))

    val selectedProvider = chatSettings.selectedProvider
        .stateIn(viewModelScope, SharingStarted.Eagerly, "openai")

    @OptIn(ExperimentalCoroutinesApi::class)
    val providerSettings = selectedProvider
        .flatMapLatest { chatSettings.settingsFor(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ProviderSettings())

    val calibreUrl = syncSettings.calibreUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val zoteroUserId = syncSettings.zoteroUserId
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val zoteroApiKey = syncSettings.zoteroApiKey
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val zoteroApiBase = syncSettings.zoteroApiBase
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val prompts = repo.observePrompts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var savedNotice by mutableStateOf<String?>(null)
        private set

    /** Model ids fetched from the provider's /models endpoint; null = not fetched. */
    var availableModels by mutableStateOf<List<String>?>(null)
        private set
    var modelsLoading by mutableStateOf(false)
        private set
    var modelsError by mutableStateOf<String?>(null)
        private set

    fun selectProvider(id: String) {
        availableModels = null
        modelsError = null
        viewModelScope.launch { chatSettings.saveSelected(id) }
    }

    /** Uses the values currently typed into the fields, saved or not. */
    fun fetchModels(baseUrl: String, apiKey: String) {
        viewModelScope.launch {
            modelsLoading = true
            modelsError = null
            try {
                availableModels = ModelCatalog.fetch(
                    selectedProvider.value,
                    ProviderSettings(baseUrl = baseUrl.trim(), apiKey = apiKey.trim()),
                )
            } catch (e: Exception) {
                modelsError = e.message ?: "Failed to fetch models"
            } finally {
                modelsLoading = false
            }
        }
    }

    fun saveProvider(baseUrl: String, apiKey: String, model: String) {
        viewModelScope.launch {
            chatSettings.saveProvider(
                selectedProvider.value,
                ProviderSettings(baseUrl = baseUrl, apiKey = apiKey, model = model),
            )
            savedNotice = "Saved ${providerDisplayName(selectedProvider.value)}"
        }
    }

    fun saveServers(calibre: String, zoteroUser: String, zoteroKey: String, zoteroBase: String) {
        viewModelScope.launch {
            syncSettings.saveCalibreUrl(calibre)
            syncSettings.saveZotero(zoteroUser, zoteroKey, zoteroBase)
            savedNotice = "Saved servers"
        }
    }

    val maxSavedPrompts = 12

    fun savePrompt(id: Long?, title: String, body: String) {
        viewModelScope.launch {
            if (id == null && prompts.value.size >= maxSavedPrompts) {
                savedNotice = "Maximum $maxSavedPrompts saved prompts"
                return@launch
            }
            repo.savePrompt(PromptEntity(id = id ?: 0, title = title.trim(), body = body.trim()))
        }
    }

    fun deletePrompt(id: Long) {
        viewModelScope.launch { repo.deletePrompt(id) }
    }

    fun noticeShown() {
        savedNotice = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    val selectedProvider by vm.selectedProvider.collectAsState()
    val prompts by vm.prompts.collectAsState()
    var editingPrompt by remember { mutableStateOf<PromptEntity?>(null) }
    var showPromptDialog by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    vm.savedNotice?.let { notice ->
        LaunchedEffect(notice) {
            android.widget.Toast.makeText(context, notice, android.widget.Toast.LENGTH_SHORT).show()
            vm.noticeShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            item { Text("Chat provider", style = MaterialTheme.typography.titleMedium) }
            items(ALL_PROVIDER_IDS) { id ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { vm.selectProvider(id) },
                ) {
                    RadioButton(selected = selectedProvider == id, onClick = { vm.selectProvider(id) })
                    Text(providerDisplayName(id))
                }
            }
            item {
                if (selectedProvider == "copilot") {
                    Text(
                        "GitHub Copilot has no public chat API — not yet supported.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ProviderFields(vm)
                }
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { Text("Servers", style = MaterialTheme.typography.titleMedium) }
            item { ServerFields(vm) }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Saved prompts",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (prompts.size >= vm.maxSavedPrompts) {
                        Text(
                            "${prompts.size}/${vm.maxSavedPrompts}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    IconButton(onClick = {
                        if (prompts.size >= vm.maxSavedPrompts) {
                            android.widget.Toast.makeText(
                                context,
                                "Maximum ${vm.maxSavedPrompts} saved prompts",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            editingPrompt = null
                            showPromptDialog = true
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add prompt")
                    }
                }
            }
            items(prompts, key = { it.id }) { prompt ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(prompt.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                prompt.body.take(80),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = {
                            editingPrompt = prompt
                            showPromptDialog = true
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit prompt")
                        }
                        IconButton(onClick = { vm.deletePrompt(prompt.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete prompt")
                        }
                    }
                }
            }
            if (prompts.isEmpty()) {
                item {
                    Text(
                        "No saved prompts. A saved prompt becomes the system prompt of a conversation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showPromptDialog) {
        PromptDialog(
            existing = editingPrompt,
            onDismiss = { showPromptDialog = false },
            onSave = { id, title, body ->
                showPromptDialog = false
                vm.savePrompt(id, title, body)
            },
        )
    }
}

@Composable
private fun ProviderFields(vm: SettingsViewModel) {
    val settings by vm.providerSettings.collectAsState()
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var showModelPicker by remember { mutableStateOf(false) }

    LaunchedEffect(settings) {
        baseUrl = settings.baseUrl
        apiKey = settings.apiKey
        model = settings.model
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Base URL (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("Model (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { vm.fetchModels(baseUrl, apiKey) },
                enabled = apiKey.isNotBlank() && !vm.modelsLoading,
            ) { Text(if (vm.modelsLoading) "Fetching models…" else "Fetch model list") }
            vm.availableModels?.let { models ->
                TextButton(onClick = { showModelPicker = true }) {
                    Text("Choose from ${models.size} models")
                }
            }
        }
        vm.modelsError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        TextButton(
            onClick = { vm.saveProvider(baseUrl, apiKey, model) },
            enabled = apiKey.isNotBlank(),
        ) { Text("Save provider") }
    }

    if (showModelPicker) {
        ModelPickerDialog(
            models = vm.availableModels.orEmpty(),
            onDismiss = { showModelPicker = false },
            onSelect = {
                model = it
                showModelPicker = false
            },
        )
    }
}

/** Filterable list of a provider's model catalog; tap one to fill the field. */
@Composable
private fun ModelPickerDialog(
    models: List<String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var filter by remember { mutableStateOf("") }
    val shown = remember(models, filter) {
        if (filter.isBlank()) models
        else models.filter { it.contains(filter.trim(), ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select model") },
        text = {
            Column {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    label = { Text("Filter") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                    items(shown, key = { it }) { id ->
                        Text(
                            text = id,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(id) }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ServerFields(vm: SettingsViewModel) {
    val calibreUrl by vm.calibreUrl.collectAsState()
    val zoteroUserId by vm.zoteroUserId.collectAsState()
    val zoteroApiKey by vm.zoteroApiKey.collectAsState()
    val zoteroApiBase by vm.zoteroApiBase.collectAsState()
    var calibre by remember { mutableStateOf("") }
    var userId by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var apiBase by remember { mutableStateOf("") }

    LaunchedEffect(calibreUrl, zoteroUserId, zoteroApiKey, zoteroApiBase) {
        calibre = calibreUrl
        userId = zoteroUserId
        apiKey = zoteroApiKey
        apiBase = zoteroApiBase
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = calibre,
            onValueChange = { calibre = it },
            label = { Text("Calibre Content Server URL") },
            placeholder = { Text("http://192.168.1.10:8080") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = userId,
            onValueChange = { userId = it },
            label = { Text("Zotero user ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("Zotero API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = apiBase,
            onValueChange = { apiBase = it },
            label = { Text("Zotero API base (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { vm.saveServers(calibre, userId, apiKey, apiBase) }) {
            Text("Save servers")
        }
    }
}

@Composable
private fun PromptDialog(
    existing: PromptEntity?,
    onDismiss: () -> Unit,
    onSave: (Long?, String, String) -> Unit,
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var body by remember { mutableStateOf(existing?.body ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New prompt" else "Edit prompt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Prompt text") },
                    minLines = 4,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(existing?.id, title, body) },
                enabled = title.isNotBlank() && body.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
