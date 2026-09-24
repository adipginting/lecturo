package com.adipginting.lecturo.chat

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.adipginting.lecturo.AppContainer
import com.adipginting.lecturo.domain.chat.Conversation
import com.adipginting.lecturo.domain.chat.Conversations
import com.adipginting.lecturo.domain.chat.ConversationTitle
import com.adipginting.lecturo.domain.chat.Excerpt
import com.adipginting.lecturo.domain.chat.Message
import com.adipginting.lecturo.domain.chat.Prompt
import com.adipginting.lecturo.domain.chat.Prompts
import com.adipginting.lecturo.domain.shared.ProviderId
import com.adipginting.lecturo.reader.ReaderViewModel
import com.adipginting.lecturo.util.MarkdownText
import com.adipginting.lecturo.util.RichText
import com.adipginting.lecturo.domain.chat.contextBudget
import com.adipginting.lecturo.domain.chat.systemPrompt
import com.adipginting.lecturo.util.contextTokens
import com.adipginting.lecturo.util.excerptMetadata
import com.adipginting.lecturo.util.formatTokenCount
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date

class ConversationListViewModel(app: Application) : AndroidViewModel(app) {
    private val store: Conversations = AppContainer.conversations(app)
    private val promptsStore: Prompts = AppContainer.prompts(app)
    private val settings = ChatSettings(app)

    val conversations: StateFlow<List<Conversation>> = store.observeSent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Conversations whose first message has not been sent. */
    val drafts: StateFlow<List<Conversation>> = store.observeDrafts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val prompts: StateFlow<List<Prompt>> = promptsStore.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val selectedProvider = settings.selectedProvider
        .stateIn(viewModelScope, SharingStarted.Eagerly, "openai")

    var createError by mutableStateOf<String?>(null)
        private set

    fun create(promptId: Long?, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            createError = null
            val providerId = selectedProvider.value
            if (providerId == "copilot") {
                createError = "GitHub Copilot chat is not yet supported"
                return@launch
            }
            onCreated(store.create(ProviderId(providerId), Excerpt(), promptId = promptId))
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { store.delete(id) }
    }

    fun rename(id: Long, title: String) {
        viewModelScope.launch { store.rename(id, title) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onBack: () -> Unit,
    onOpenConversation: (Long) -> Unit,
    vm: ConversationListViewModel = viewModel(),
) {
    val conversations by vm.conversations.collectAsState()
    val drafts by vm.drafts.collectAsState()
    val providerId by vm.selectedProvider.collectAsState()
    var showNewDialog by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(0) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Chats") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        text = { Text("Drafts") },
                    )
                    Tab(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        text = { Text("Conversations") },
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "New conversation")
            }
        },
    ) { padding ->
        ConversationListBody(
            conversations = if (tab == 0) drafts else conversations,
            emptyText = if (tab == 0) {
                "No drafts yet — select a passage and ask AI."
            } else {
                "No conversations yet — send a draft to start one."
            },
            onOpenConversation = onOpenConversation,
            onRename = { id, title -> vm.rename(id, title) },
            onDelete = { vm.delete(it) },
            modifier = Modifier.padding(padding),
        )
    }

    if (showNewDialog) {
        val prompts by vm.prompts.collectAsState()
        NewConversationDialog(
            providerName = providerDisplayName(providerId),
            prompts = prompts,
            error = vm.createError,
            onDismiss = { showNewDialog = false },
            onCreate = { promptId ->
                vm.create(promptId) { id ->
                    showNewDialog = false
                    onOpenConversation(id)
                }
            },
        )
    }
}

/**
 * The conversations themselves — rows, and the renaming and deleting they offer —
 * with no chrome: the full screen and the reader's panel each frame it, and each
 * decides what to do with a tap.
 */
@Composable
internal fun ConversationListBody(
    conversations: List<Conversation>,
    emptyText: String,
    onOpenConversation: (Long) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<Conversation?>(null) }
    var pendingRename by remember { mutableStateOf<Conversation?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        if (conversations.isEmpty()) {
            Text(
                text = emptyText,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        onClick = { onOpenConversation(conversation.id) },
                        onRename = { pendingRename = conversation },
                        onDelete = { pendingDelete = conversation },
                    )
                }
            }
        }
    }

    pendingRename?.let { conversation ->
        RenameDialog(
            current = conversation.title,
            onDismiss = { pendingRename = null },
            onRename = { title -> onRename(conversation.id, title) },
        )
    }

    pendingDelete?.let { conversation ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete \"${conversation.title}\"?") },
            text = { Text("This deletes the chat session and its whole prompt history.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(conversation.id)
                        pendingDelete = null
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
private fun ConversationRow(
    conversation: Conversation,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val excerpt = conversation.contextText
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(conversation.title, style = MaterialTheme.typography.titleMedium)
                if (excerpt != null || conversation.contextImagePath != null) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        conversation.contextImagePath?.let { path ->
                            ChatImageView(
                                path = path,
                                maxPx = 128,
                                contentDescription = "Screenshot context",
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        if (excerpt != null) {
                            MarkdownText(
                                markdown = excerpt,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 10,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Text(
                        text = excerptMetadata(
                            title = conversation.docTitle,
                            locator = conversation.docLocator,
                            timestamp = conversation.createdAt,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    text = providerDisplayName(conversation.providerId) + " · " +
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(conversation.updatedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Default.Edit, contentDescription = "Rename chat")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete chat")
            }
        }
    }
}

/**
 * Renaming a conversation, from either the list or the open chat: one field,
 * starting from the name it has now. An empty name cannot be saved, so the
 * choice is to change it or leave it.
 */
@Composable
private fun RenameDialog(
    current: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename chat") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(ConversationTitle.MAX_LENGTH) },
                label = { Text("Title") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onRename(name)
                    onDismiss()
                },
                enabled = ConversationTitle.normalize(name) != null,
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Saved prompt is chosen here and locked for the whole conversation. */
@Composable
private fun NewConversationDialog(
    providerName: String,
    prompts: List<Prompt>,
    error: String?,
    onDismiss: () -> Unit,
    onCreate: (Long?) -> Unit,
) {
    var chosen by remember { mutableStateOf<Long?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New conversation ($providerName)") },
        text = {
            Column {
                Text("Saved prompt for this chat:", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = chosen == null, onClick = { chosen = null })
                    Text("None")
                }
                prompts.forEach { prompt ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { chosen = prompt.id },
                    ) {
                        RadioButton(selected = chosen == prompt.id, onClick = { chosen = prompt.id })
                        Text(prompt.title)
                    }
                }
                if (prompts.isEmpty()) {
                    Text(
                        "No saved prompts yet — add them in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(chosen) }) { Text("Start") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val PROVIDER_IDS =
    listOf("openai", "kimi", "openrouter", "deepseek", "anthropic", ChatSettings.LECTURO)

class ChatViewModel(
    app: Application,
    private val conversationId: Long,
) : AndroidViewModel(app) {
    private val store: Conversations = AppContainer.conversations(app)
    private val promptsStore: Prompts = AppContainer.prompts(app)
    private val chatImages = ChatImages(File(app.filesDir, "chat_images"))
    private val settings = ChatSettings(app)

    val messages: StateFlow<List<Message>> = store.observeMessages(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val conversation: StateFlow<Conversation?> = store.observe(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val compactionThreshold = settings.compactionThreshold
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            ChatSettings.DEFAULT_COMPACTION_THRESHOLD,
        )

    /** The saved prompts, offered as chips above the composer. */
    val prompts: StateFlow<List<Prompt>> = promptsStore.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var title by mutableStateOf("")
        private set
    var providerName by mutableStateOf("")
        private set
    var contextExcerpt by mutableStateOf<String?>(null)
        private set
    var contextMeta by mutableStateOf<String?>(null)
        private set

    /** The frozen screenshot a conversation was fired from, as a file path. */
    var contextImage by mutableStateOf<String?>(null)
        private set
    var providerOptions by mutableStateOf<List<ReaderViewModel.ProviderOption>>(emptyList())
        private set
    var selectedProviderId by mutableStateOf("")
        private set
    var sending by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** A one-off outcome for compaction ("Nothing to compact yet", ...). */
    var notice by mutableStateOf<String?>(null)
        private set
    var compacting by mutableStateOf(false)
        private set

    /** The composer text, kept here so the top bar's counter can count it. */
    var draftInput by mutableStateOf("")

    /** Length of the system prompt, for the counter's fallback estimate. */
    var systemChars by mutableStateOf(0)
        private set

    init {
        viewModelScope.launch {
            val conversation = store.get(conversationId)
            title = conversation?.title ?: "Chat"
            providerName = conversation?.let { providerDisplayName(it.providerId) } ?: ""
            selectedProviderId = conversation?.providerId ?: "openai"
            contextExcerpt = conversation?.contextText
            contextMeta = conversation?.let {
                excerptMetadata(it.docTitle, it.docLocator, it.createdAt)
            }
            contextImage = conversation?.contextImagePath
            systemChars = conversation?.let { systemPrompt(it, promptBody(it)).length } ?: 0
            providerOptions = PROVIDER_IDS.map { id ->
                val providerSettings = settings.settingsFor(id).first()
                val model = providerSettings.model.ifBlank { null }
                ReaderViewModel.ProviderOption(
                    id = id,
                    label = providerDisplayName(id) + (model?.let { " · $it" } ?: ""),
                    enabled = settings.buildProviderFor(id).isConfigured,
                )
            }
        }
    }

    /**
     * Switches this conversation to another provider, mid-thread. The messages
     * and the frozen excerpt stay as they are; the next reply comes from the
     * new model.
     */
    fun changeProvider(providerId: String) {
        if (providerId == selectedProviderId) return
        selectedProviderId = providerId
        providerName = providerDisplayName(providerId)
        viewModelScope.launch { store.setProvider(conversationId, ProviderId(providerId)) }
    }

    /** Renames this conversation, showing the new name before it is stored. */
    fun rename(title: String) {
        val name = ConversationTitle.normalize(title) ?: return
        this.title = name
        viewModelScope.launch { store.rename(conversationId, name) }
    }

    fun send(text: String) {
        if (text.isBlank() || sending) return
        viewModelScope.launch {
            sending = true
            ChatSending.begin()
            error = null
            notice = null
            try {
                // The turn may not fit: compact before it rather than refuse it.
                maybeAutoCompact(text.trim())
                store.append(conversationId, Message(role = Message.Role.User, text = text.trim()))
                val conversation = store.get(conversationId)
                    ?: throw IllegalStateException("Conversation not found")
                title = conversation.title
                val providerSettings = settings.settingsFor(conversation.providerId).first()
                val provider = settings.buildProviderFor(conversation.providerId)
                if (!provider.isConfigured) {
                    throw IllegalStateException(
                        "${provider.displayName} is not configured — add its API key in Settings",
                    )
                }
                val model = providerSettings.model
                    .ifBlank { ChatSettings.defaultModel(conversation.providerId) }
                val contextImage = contextImageForSend(
                    chatImages,
                    conversation,
                    model,
                    settings.visionOverrides.first(),
                )
                val history = apiMessages(store.activeMessages(conversationId), contextImage)
                val reply = provider.chat(systemPrompt(conversation, promptBody(conversation)), history)
                store.append(conversationId, Message(role = Message.Role.Assistant, text = reply.text))
                store.recordUsage(conversationId, reply.promptTokens, reply.completionTokens)
                // Only a reply turns a draft into a conversation: a send that
                // never answered leaves the excerpt waiting in Drafts.
                store.send(conversationId, text.trim())
            } catch (e: Exception) {
                error = e.message ?: "Chat failed"
            } finally {
                sending = false
                ChatSending.end()
            }
        }
    }

    /**
     * One call to the conversation's current provider to summarize the active
     * messages, then an atomic swap: they are superseded and the summary is
     * inserted. A failed call changes nothing.
     */
    fun compact() {
        if (sending || compacting) return
        viewModelScope.launch {
            compacting = true
            error = null
            notice = null
            try {
                val conversation = store.get(conversationId)
                    ?: throw IllegalStateException("Conversation not found")
                if (store.activeMessages(conversationId).isEmpty()) {
                    notice = "Nothing to compact yet"
                    return@launch
                }
                val provider = settings.buildProviderFor(conversation.providerId)
                if (!provider.isConfigured) {
                    throw IllegalStateException(
                        "${provider.displayName} is not configured — add its API key in Settings",
                    )
                }
                if (compactNow()) notice = "Conversation compacted"
            } catch (e: Exception) {
                error = e.message ?: "Compaction failed"
            } finally {
                compacting = false
            }
        }
    }

    /**
     * Compacts when the next turn would cross the model's budget. A failed
     * compaction is not allowed to cost the user their message, so it is
     * swallowed; the send carries on with what context there is.
     */
    private suspend fun maybeAutoCompact(pending: String) {
        val conversation = store.get(conversationId) ?: return
        val providerSettings = settings.settingsFor(conversation.providerId).first()
        val model = providerSettings.model
            .ifBlank { ChatSettings.defaultModel(conversation.providerId) }
        val override = settings.compactionThreshold.first()
            .takeIf { it != ChatSettings.DEFAULT_COMPACTION_THRESHOLD }
        val estimate = contextTokens(
            lastPromptTokens = conversation.lastPromptTokens,
            lastCallAt = conversation.lastCallAt,
            systemChars = systemPrompt(conversation, promptBody(conversation)).length,
            activeMessages = store.activeMessages(conversationId),
            draftChars = pending.length,
        )
        if (estimate <= contextBudget(model, override)) return
        runCatching { compactNow() }
            .onSuccess { if (it) notice = "Conversation compacted" }
    }

    /** The compaction call itself; false when there is nothing to compact. */
    private suspend fun compactNow(): Boolean {
        val conversation = store.get(conversationId) ?: return false
        val active = store.activeMessages(conversationId)
        if (active.isEmpty()) return false
        val provider = settings.buildProviderFor(conversation.providerId)
        if (!provider.isConfigured) return false
        val reply = provider.chat(COMPACTION_SYSTEM_PROMPT, apiMessages(active))
        store.compact(conversationId, reply.text)
        return true
    }

    /** The prompt locked at conversation start, if any. */
    private suspend fun promptBody(conversation: Conversation): String? =
        conversation.promptId?.let { promptsStore.get(it)?.body } ?: conversation.customPrompt
}

/**
 * The frozen screenshot a conversation sends, or a refusal: a conversation with
 * a frozen screenshot can only go to a vision-capable model.
 */
internal suspend fun contextImageForSend(
    chatImages: ChatImages,
    conversation: Conversation,
    model: String,
    overrides: Map<String, Boolean>,
) = conversation.contextImagePath?.let { path ->
    if (!supportsVision(conversation.providerId, model, overrides)) {
        throw IllegalStateException(
            "$model does not support images — switch the conversation to a " +
                "vision-capable model or override it in Settings",
        )
    }
    chatImages.load(path)
        ?: throw IllegalStateException("This conversation's screenshot is missing from storage")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: Long,
    onBack: () -> Unit,
    vm: ChatViewModel = viewModel(
        key = "chat-$conversationId",
        factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                ChatViewModel(app, conversationId)
            }
        },
    ),
) {
    var showModelPicker by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    ChatDialogs(
        vm = vm,
        showRename = showRename,
        showModelPicker = showModelPicker,
        onDismissRename = { showRename = false },
        onDismissModelPicker = { showModelPicker = false },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { ChatTitle(title = vm.title, onRename = { showRename = true }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    ContextCounterLabel(vm)
                    ProviderButton(vm = vm, onClick = { showModelPicker = true })
                    CompactAction(vm)
                },
            )
        },
    ) { padding ->
        ChatBody(vm = vm, modifier = Modifier.padding(padding).imePadding())
    }
}

/** A conversation's name, tappable to change it. */
@Composable
internal fun ChatTitle(title: String, onRename: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clickable(onClick = onRename),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = "Rename chat",
            modifier = Modifier.padding(start = 8.dp).size(16.dp),
        )
    }
}

/** The button naming the model that answers this conversation. */
@Composable
internal fun ProviderButton(vm: ChatViewModel, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) {
        Text(vm.providerName, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * Estimated tokens the next message will carry. Past the compaction threshold
 * it turns red: the only automatic part of compaction is this nudge.
 */
@Composable
internal fun ContextCounterLabel(vm: ChatViewModel, modifier: Modifier = Modifier) {
    val conversation by vm.conversation.collectAsState()
    val messages by vm.messages.collectAsState()
    val threshold by vm.compactionThreshold.collectAsState()
    val tokens = contextTokens(
        lastPromptTokens = conversation?.lastPromptTokens,
        lastCallAt = conversation?.lastCallAt,
        systemChars = vm.systemChars,
        activeMessages = messages.filter { !it.superseded },
        draftChars = vm.draftInput.length,
    )
    Text(
        text = formatTokenCount(tokens),
        style = MaterialTheme.typography.labelSmall,
        color = if (tokens > threshold) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier.padding(start = 8.dp),
    )
}

/** Manual compaction, tucked into an overflow to keep the header narrow. */
@Composable
internal fun CompactAction(vm: ChatViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More actions")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = {
                    Text(if (vm.compacting) "Compacting…" else "Compact context")
                },
                onClick = {
                    open = false
                    vm.compact()
                },
                enabled = !vm.sending && !vm.compacting,
            )
        }
    }
}

/** The rename and model pickers, for whichever chrome is hosting a conversation. */
@Composable
internal fun ChatDialogs(
    vm: ChatViewModel,
    showRename: Boolean,
    showModelPicker: Boolean,
    onDismissRename: () -> Unit,
    onDismissModelPicker: () -> Unit,
) {
    if (showRename) {
        RenameDialog(
            current = vm.title,
            onDismiss = onDismissRename,
            onRename = { vm.rename(it) },
        )
    }

    if (showModelPicker) {
        com.adipginting.lecturo.reader.ModelPickerDialog(
            options = vm.providerOptions,
            selectedId = vm.selectedProviderId,
            onDismiss = onDismissModelPicker,
            onSelect = {
                vm.changeProvider(it)
                onDismissModelPicker()
            },
        )
    }
}

/**
 * A conversation's history and composer, with no chrome of its own: the full
 * screen and the reader's panel each put their own around it.
 */
@Composable
internal fun ChatBody(vm: ChatViewModel, modifier: Modifier = Modifier) {
    val messages by vm.messages.collectAsState()
    val listState = rememberLazyListState()
    val excerpt = vm.contextExcerpt
    val contextImage = vm.contextImage
    val hasContext = excerpt != null || contextImage != null
    val active = messages.filter { !it.superseded }
    val superseded = messages.filter { it.superseded }
    var showSuperseded by remember { mutableStateOf(false) }
    val itemCount = (if (hasContext) 1 else 0) +
        (if (superseded.isNotEmpty()) 1 else 0) +
        (if (showSuperseded) superseded.size else 0) +
        active.size

    LaunchedEffect(itemCount) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            if (hasContext) {
                item(key = "context") {
                    ContextItem(
                        excerpt = excerpt,
                        imagePath = contextImage,
                        meta = vm.contextMeta,
                    )
                }
            }
            if (superseded.isNotEmpty()) {
                item(key = "compacted") {
                    CompactedRow(
                        count = superseded.size,
                        expanded = showSuperseded,
                        onToggle = { showSuperseded = !showSuperseded },
                    )
                }
                if (showSuperseded) {
                    items(superseded, key = { it.id }) { message ->
                        Box(modifier = Modifier.alpha(0.5f)) {
                            MessageBubble(message)
                        }
                    }
                }
            }
            items(active, key = { it.id }) { message ->
                if (message.isSummary) SummaryItem(message.text) else MessageBubble(message)
            }
        }
        vm.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        vm.notice?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        val promptChips by vm.prompts.collectAsState()
        if (promptChips.isNotEmpty()) {
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                promptChips.forEach { prompt ->
                    item(key = prompt.id) {
                        androidx.compose.material3.AssistChip(
                            onClick = {
                                // Same as the draft screen: one tap sends.
                                val message = if (vm.draftInput.isBlank()) {
                                    prompt.body
                                } else {
                                    vm.draftInput + "\n" + prompt.body
                                }
                                vm.draftInput = ""
                                vm.send(message)
                            },
                            label = { Text(prompt.title) },
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = vm.draftInput,
                onValueChange = { vm.draftInput = it },
                label = { Text("Ask about what you saved") },
                modifier = Modifier.weight(1f),
                enabled = !vm.sending,
            )
            if (vm.sending) {
                CircularProgressIndicator(modifier = Modifier.padding(12.dp))
            } else {
                FilledIconButton(
                    onClick = {
                        vm.send(vm.draftInput)
                        vm.draftInput = ""
                    },
                    enabled = vm.draftInput.isNotBlank(),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

/** Where compacted-away messages hide; tap to see them, greyed out. */
@Composable
private fun CompactedRow(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        androidx.compose.material3.HorizontalDivider()
        Text(
            text = if (expanded) {
                "Hide compacted messages ($count)"
            } else {
                "Earlier messages compacted ($count)"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 4.dp),
        )
        androidx.compose.material3.HorizontalDivider()
    }
}

/** A compaction summary in the normal flow: secondary styling, labelled. */
@Composable
private fun SummaryItem(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Summary",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RichText(
                markdown = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * The excerpt a conversation was fired from, shown as the first item in its
 * history. Tapping expands the three-line clamp; a screenshot has no clamp to
 * expand but shares the card.
 */
@Composable
private fun ContextItem(excerpt: String?, imagePath: String?, meta: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = if (excerpt != null) "Original excerpt" else "Screenshot",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            imagePath?.let { path ->
                ChatImageView(
                    path = path,
                    contentDescription = "Screenshot context",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .padding(top = 4.dp),
                )
            }
            if (excerpt != null) {
                // The conversation window shows the excerpt whole: clamping it
                // hides the passage the answer is about.
                RichText(
                    markdown = excerpt,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (meta != null) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message) {
    val isUser = message.role == Message.Role.User
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.85f),
            colors = if (isUser) {
                androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                )
            } else {
                androidx.compose.material3.CardDefaults.cardColors()
            },
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                message.imagePath?.let { path ->
                    ChatImageView(
                        path = path,
                        contentDescription = "Attached image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                    )
                }
                if (message.text.isNotBlank()) {
                    RichText(
                        markdown = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = if (message.imagePath != null) {
                            Modifier.padding(top = 8.dp)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
    }
}
