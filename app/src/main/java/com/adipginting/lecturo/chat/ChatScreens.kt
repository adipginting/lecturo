package com.adipginting.lecturo.chat

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.adipginting.lecturo.data.ChatRepository
import com.adipginting.lecturo.data.ConversationEntity
import com.adipginting.lecturo.data.LecturoDatabase
import com.adipginting.lecturo.data.MessageEntity
import com.adipginting.lecturo.data.PromptEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class ConversationListViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ChatRepository(LecturoDatabase.get(app))
    private val settings = ChatSettings(app)

    val conversations = repo.observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val prompts = repo.observePrompts()
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
            onCreated(repo.createConversation(providerId, promptId))
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.deleteConversation(id) }
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
    val providerId by vm.selectedProvider.collectAsState()
    var showNewDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ConversationEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chats") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "New conversation")
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (conversations.isEmpty()) {
                Text(
                    text = "No conversations yet. Tap + to chat about your basket.",
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
                            onDelete = { pendingDelete = conversation },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { conversation ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete \"${conversation.title}\"?") },
            text = { Text("This deletes the chat session and its whole prompt history.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.delete(conversation.id)
                        pendingDelete = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
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

@Composable
private fun ConversationRow(
    conversation: ConversationEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(conversation.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = providerDisplayName(conversation.providerId) + " · " +
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(conversation.updatedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete chat")
            }
        }
    }
}

/** Saved prompt is chosen here and locked for the whole conversation. */
@Composable
private fun NewConversationDialog(
    providerName: String,
    prompts: List<PromptEntity>,
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

class ChatViewModel(
    app: Application,
    private val conversationId: Long,
) : AndroidViewModel(app) {
    private val repo = ChatRepository(LecturoDatabase.get(app))
    private val settings = ChatSettings(app)

    val messages = repo.observeMessages(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var title by mutableStateOf("")
        private set
    var providerName by mutableStateOf("")
        private set
    var sending by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            val conversation = repo.getConversation(conversationId)
            title = conversation?.title ?: "Chat"
            providerName = conversation?.let { providerDisplayName(it.providerId) } ?: ""
        }
    }

    fun send(text: String) {
        if (text.isBlank() || sending) return
        viewModelScope.launch {
            sending = true
            error = null
            try {
                repo.addMessage(conversationId, "user", text.trim())
                repo.renameIfUntitled(conversationId, text.trim())
                val conversation = repo.getConversation(conversationId)
                    ?: throw IllegalStateException("Conversation not found")
                title = conversation.title
                val providerSettings = settings.settingsFor(conversation.providerId).first()
                val provider = ChatSettings.buildProvider(conversation.providerId, providerSettings)
                if (!provider.isConfigured) {
                    throw IllegalStateException(
                        "${provider.displayName} is not configured — add its API key in Settings",
                    )
                }
                val history = repo.history(conversationId)
                    .map { ChatMessage(it.role, it.text) }
                val reply = provider.chat(repo.buildSystemPrompt(conversation), history)
                repo.addMessage(conversationId, "assistant", reply)
            } catch (e: Exception) {
                error = e.message ?: "Chat failed"
            } finally {
                sending = false
            }
        }
    }
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
    val messages by vm.messages.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vm.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { Text(vm.providerName, style = MaterialTheme.typography.labelMedium) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message)
                }
            }
            vm.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Ask about your basket") },
                    modifier = Modifier.weight(1f),
                    enabled = !vm.sending,
                )
                if (vm.sending) {
                    CircularProgressIndicator(modifier = Modifier.padding(12.dp))
                } else {
                    IconButton(
                        onClick = {
                            vm.send(input)
                            input = ""
                        },
                        enabled = input.isNotBlank(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: MessageEntity) {
    val isUser = message.role == "user"
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
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}
