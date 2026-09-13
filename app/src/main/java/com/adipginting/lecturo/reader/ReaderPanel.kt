package com.adipginting.lecturo.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.adipginting.lecturo.chat.ChatBody
import com.adipginting.lecturo.chat.ChatDialogs
import com.adipginting.lecturo.chat.ChatTitle
import com.adipginting.lecturo.chat.ChatViewModel
import com.adipginting.lecturo.chat.ConversationListBody
import com.adipginting.lecturo.chat.ConversationListViewModel
import com.adipginting.lecturo.chat.DraftChatBody
import com.adipginting.lecturo.chat.DraftChatViewModel
import com.adipginting.lecturo.chat.ProviderButton

/**
 * The chat surface that rides over the page instead of replacing it. It is not a
 * second reader screen: it is the same conversation and the same composer, in a
 * sheet that leaves the book visible and scrollable above it.
 *
 * The sheet is deliberately not modal — a scrim over the page, which is what the
 * Saved list uses, would stop the page being read, which is the whole point of
 * the panel. Collapsing it puts it away; the reader's chat icon brings back the
 * draft or the document's active chat.
 */
@Composable
internal fun ReaderPanel(
    mode: PanelMode,
    onOpenChats: () -> Unit,
    onOpenConversation: (Long) -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // How much of the page to leave showing: a composer and one excerpt need
    // little, a conversation that has been going for a while needs more.
    val height = when (mode) {
        PanelMode.Hidden -> 0f
        PanelMode.Draft -> 0.5f
        PanelMode.Chats -> 0.65f
        is PanelMode.Conversation -> 0.75f
    }
    // The keyboard and the navigation bar sit under the panel's content, not
    // over it: the composer rides above whichever is showing.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(height)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
    ) {
        when (mode) {
            PanelMode.Hidden -> Unit

            PanelMode.Chats -> {
                val vm: ConversationListViewModel = viewModel()
                val conversations by vm.conversations.collectAsState()
                PanelHeader(title = "Chats", onCollapse = onCollapse)
                ConversationListBody(
                    conversations = conversations,
                    onOpenConversation = onOpenConversation,
                    onRename = { id, title -> vm.rename(id, title) },
                    onDelete = { vm.delete(it) },
                )
            }

            PanelMode.Draft -> {
                val vm: DraftChatViewModel = viewModel()
                PanelHeader(
                    title = vm.args?.docTitle ?: "New conversation",
                    onBack = onOpenChats,
                    onCollapse = onCollapse,
                )
                DraftChatBody(vm = vm, onSent = onOpenConversation)
            }

            is PanelMode.Conversation -> {
                val vm: ChatViewModel = viewModel(
                    key = "chat-${mode.id}",
                    factory = viewModelFactory {
                        initializer {
                            val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                            ChatViewModel(app, mode.id)
                        }
                    },
                )
                var showRename by remember { mutableStateOf(false) }
                var showModelPicker by remember { mutableStateOf(false) }
                ChatDialogs(
                    vm = vm,
                    showRename = showRename,
                    showModelPicker = showModelPicker,
                    onDismissRename = { showRename = false },
                    onDismissModelPicker = { showModelPicker = false },
                )
                PanelHeader(
                    title = vm.title,
                    onBack = onOpenChats,
                    onCollapse = onCollapse,
                    onRename = { showRename = true },
                    actions = { ProviderButton(vm = vm, onClick = { showModelPicker = true }) },
                )
                ChatBody(vm = vm)
            }
        }
    }
}

/**
 * What the panel shows above its content: where it came from, what it is, and
 * the way out — down, back to the list, or a rename.
 */
@Composable
private fun PanelHeader(
    title: String,
    onCollapse: () -> Unit,
    onBack: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to chats")
            }
        }
        if (onRename != null) {
            ChatTitle(
                title = title,
                onRename = onRename,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
        } else {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
        }
        actions()
        IconButton(onClick = onCollapse) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Put the panel away")
        }
    }
}
