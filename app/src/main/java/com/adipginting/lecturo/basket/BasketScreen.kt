package com.adipginting.lecturo.basket

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adipginting.lecturo.chat.DraftChat
import com.adipginting.lecturo.chat.DraftChatArgs
import com.adipginting.lecturo.data.BasketRepository
import com.adipginting.lecturo.data.BasketRow
import com.adipginting.lecturo.data.LecturoDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BasketViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BasketRepository(LecturoDatabase.get(app))

    val items = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun remove(id: Long) {
        viewModelScope.launch { repo.remove(id) }
    }

    fun clear() {
        viewModelScope.launch { repo.clear() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasketScreen(
    onBack: () -> Unit,
    onOpenChat: () -> Unit = {},
    onOpenDraft: () -> Unit = {},
    vm: BasketViewModel = viewModel(),
) {
    val items by vm.items.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Basket") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onOpenChat) { Text("Chat") }
                    if (items.isNotEmpty()) {
                        TextButton(onClick = { vm.clear() }) { Text("Clear all") }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (items.isEmpty()) {
                Text(
                    text = "Basket is empty. Select text while reading and tap \"Add to basket\".",
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    items(items, key = { it.item.id }) { row ->
                        BasketRowCard(
                            row = row,
                            onClick = {
                                DraftChat.pending = DraftChatArgs(
                                    text = row.item.text,
                                    docTitle = row.docTitle,
                                    locator = row.item.locator,
                                    basketItemId = row.item.id,
                                )
                                onOpenDraft()
                            },
                            onRemove = { vm.remove(row.item.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BasketRowCard(
    row: BasketRow,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.item.text, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = listOfNotNull(
                        row.docTitle ?: "Unknown document",
                        row.item.locator,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove")
            }
        }
    }
}
