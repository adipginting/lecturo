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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adipginting.lecturo.AppContainer
import com.adipginting.lecturo.domain.chat.Conversation
import com.adipginting.lecturo.domain.chat.Conversations
import com.adipginting.lecturo.domain.chat.Message
import com.adipginting.lecturo.domain.chat.Prompt
import com.adipginting.lecturo.domain.chat.Prompts
import com.adipginting.lecturo.domain.chat.systemPrompt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class DraftChatViewModel(app: Application, private val draftId: String) : AndroidViewModel(app) {
    private val store: Conversations = AppContainer.conversations(app)
    private val promptsStore: Prompts = AppContainer.prompts(app)
    private val chatImages = ChatImages(File(app.filesDir, "chat_images"))
    private val settings = ChatSettings(app)

    /** The persisted draft being composed, loaded from the store. */
    var draft by mutableStateOf<DraftChatArgs?>(null)
        private set

    init {
        viewModelScope.launch {
            val conversation: Conversation = store.get(draftId) ?: return@launch
            draft = DraftChatArgs(
                text = conversation.contextText.orEmpty(),
                docTitle = conversation.docTitle,
                locator = conversation.docLocator,
                docId = conversation.docId.orEmpty(),
                imagePath = conversation.contextImagePath,
            )
        }
    }

    val prompts: StateFlow<List<Prompt>> = promptsStore.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var sending by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var sentConversationId by mutableStateOf<String?>(null)
        private set

    fun send(text: String) {
        if (draft == null) return
        if (text.isBlank() || sending) return
        viewModelScope.launch {
            sending = true
            ChatSending.begin()
            error = null
            try {
                val conversationId = draftId
                store.append(conversationId, Message(role = Message.Role.User, text = text.trim()))
                val conversation: Conversation = store.get(conversationId)
                    ?: throw IllegalStateException("Conversation not found")
                val providerSettings = settings.settingsFor(conversation.providerId).first()
                val provider = settings.buildProviderFor(conversation.providerId)
                if (!provider.isConfigured) {
                    throw IllegalStateException(
                        "${provider.displayName} is not configured — add its API key in Settings",
                    )
                }
                val history = apiMessages(
                    store.activeMessages(conversationId),
                    contextImageForSend(
                        chatImages,
                        conversation,
                        providerSettings.model
                            .ifBlank { ChatSettings.defaultModel(conversation.providerId) },
                        settings.visionOverrides.first(),
                    ),
                )
                val reply = provider.chat(systemPrompt(conversation, conversation.customPrompt), history)
                store.append(conversationId, Message(role = Message.Role.Assistant, text = reply.text))
                store.recordUsage(conversationId, reply.promptTokens, reply.completionTokens)
                store.send(conversationId, text.trim())
                sentConversationId = conversationId
            } catch (e: Exception) {
                // A draft that never got a reply is not a conversation: the
                // excerpt stays in the draft until an answer lands, and the
                // screenshot file stays for the retry.
                error = e.message ?: "Chat failed"
            } finally {
                sending = false
                ChatSending.end()
            }
        }
    }
}

/**
 * The composer for a draft, with no chrome of its own: the reader's panel frames
 * it. Sending is what turns a draft into a conversation, so [onSent] is where the
 * panel goes when that happens.
 */
@Composable
internal fun DraftChatBody(
    vm: DraftChatViewModel,
    onSent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Composed before the draft is read, so a send that clears the holder still
    // lets the host hear about the conversation it created.
    LaunchedEffect(vm.sentConversationId) {
        vm.sentConversationId?.let(onSent)
    }

    val draft = vm.draft ?: return
    var expanded by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val prompts by vm.prompts.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        // The banner scrolls with the conversation instead of standing above
        // the composer: a screenshot draft is tall enough that a fixed banner
        // would push the composer past the panel's edge, keyboard or no.
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "context") {
                ContextBanner(
                    draft = draft,
                    expanded = expanded,
                    onToggle = { expanded = !expanded },
                )
            }
            if (prompts.isNotEmpty()) {
                item(key = "prompts") {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
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
                FilledIconButton(
                    onClick = {
                        vm.send(input)
                        input = ""
                    },
                    enabled = input.isNotBlank(),
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

@Composable
private fun ContextBanner(
    draft: DraftChatArgs,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            draft.imagePath?.let { path ->
                ChatImageView(
                    path = path,
                    contentDescription = "Screenshot to ask about",
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .heightIn(max = 160.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            }
            if (draft.text.isNotBlank()) {
                com.adipginting.lecturo.util.RichText(
                    markdown = draft.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
