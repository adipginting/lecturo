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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adipginting.lecturo.data.SavedRepository
import com.adipginting.lecturo.data.ChatRepository
import com.adipginting.lecturo.data.LecturoDatabase
import com.adipginting.lecturo.data.PromptEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DraftChatViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ChatRepository(LecturoDatabase.get(app))
    private val savedRepo = SavedRepository(LecturoDatabase.get(app))
    private val settings = ChatSettings(app)

    /** Snapshot of the draft payload; cleared from the holder on first access. */
    val args: DraftChatArgs? = DraftChat.pending.also { DraftChat.pending = null }

    val prompts = repo.observePrompts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var sending by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var sentConversationId by mutableStateOf<Long?>(null)
        private set

    fun send(text: String) {
        val draft = args ?: return
        if (text.isBlank() || sending) return
        viewModelScope.launch {
            sending = true
            error = null
            try {
                val providerId = settings.selectedProvider.first()
                if (providerId == "copilot") {
                    throw IllegalStateException("GitHub Copilot chat is not yet supported")
                }
                val conversationId = repo.createConversation(
                    providerId = providerId,
                    promptId = null,
                    customPrompt = null,
                    contextText = draft.text,
                    docTitle = draft.docTitle,
                    docLocator = draft.locator,
                )
                repo.addMessage(conversationId, "user", text.trim())
                repo.renameIfUntitled(conversationId, text.trim())
                val conversation = repo.getConversation(conversationId)
                    ?: throw IllegalStateException("Conversation not found")
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
                draft.savedItemId?.let { savedRepo.remove(it) }
                sentConversationId = conversationId
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
fun DraftChatScreen(
    onBack: () -> Unit,
    onSent: (Long) -> Unit,
    vm: DraftChatViewModel = viewModel(),
) {
    val draft = remember { vm.args }
    var expanded by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val prompts by vm.prompts.collectAsState()

    LaunchedEffect(vm.sentConversationId) {
        vm.sentConversationId?.let { onSent(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New conversation") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (draft == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No draft context. Go back and select an excerpt.",
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {}
                ContextBanner(
                    draft = draft,
                    expanded = expanded,
                    onToggle = { expanded = !expanded },
                )
                if (prompts.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(prompts, key = { it.id }) { prompt ->
                            AssistChip(
                                onClick = {
                                    // One tap: whatever is already typed plus this
                                    // prompt goes out as the message.
                                    val message = appendPrompt(input, prompt.body)
                                    input = ""
                                    vm.send(message)
                                },
                                label = { Text(prompt.title) },
                            )
                        }
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
                        label = { Text("Ask about this excerpt") },
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
}

@Composable
private fun ContextBanner(
    draft: DraftChatArgs,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable(onClick = onToggle),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            com.adipginting.lecturo.util.MarkdownText(
                markdown = draft.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
            val pageLabel = draft.locator?.toIntOrNull()?.takeIf { it > 0 }?.let { "Page $it" }
            val meta = listOfNotNull(draft.docTitle, pageLabel).joinToString(" · ")
            if (meta.isNotEmpty()) {
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

private fun appendPrompt(current: String, body: String): String =
    if (current.isBlank()) body else "$current\n$body"
