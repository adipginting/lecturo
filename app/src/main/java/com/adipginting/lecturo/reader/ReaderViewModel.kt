package com.adipginting.lecturo.reader

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adipginting.lecturo.chat.ChatSettings
import com.adipginting.lecturo.chat.providerDisplayName
import com.adipginting.lecturo.data.SavedRepository
import com.adipginting.lecturo.data.ChatRepository
import com.adipginting.lecturo.data.LecturoDatabase
import com.adipginting.lecturo.data.DocumentEntity
import com.adipginting.lecturo.data.DocumentRepository
import com.adipginting.lecturo.data.activeConversation
import com.adipginting.lecturo.library.BookFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ReaderViewModel(
    app: Application,
    private val docId: String,
) : AndroidViewModel(app) {

    data class UiState(
        val loading: Boolean = true,
        val title: String = "",
        val format: String = "",
        val pdfFile: File? = null,
        val pdfInitialPage: Int = 0,
        val epubFile: File? = null,
        val epubInitialLocator: String? = null,
        val error: String? = null,
    )

    /** One entry in the model chooser: provider + configured model. */
    data class ProviderOption(
        val id: String,
        val label: String,
        val enabled: Boolean,
    )

    var uiState by mutableStateOf(UiState())
        private set

    private val repo = DocumentRepository(app, LecturoDatabase.get(app))
    private val savedRepo = SavedRepository(LecturoDatabase.get(app))
    private val chatRepo = ChatRepository(LecturoDatabase.get(app))
    private val chatSettings = ChatSettings(app)

    /** The document's active chat: the conversation last engaged with, if any. */
    val activeChat = chatRepo.observeForDoc(docId)
        .map { activeConversation(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** All selectable providers and the current global selection. */
    var providerOptions by mutableStateOf<List<ProviderOption>>(emptyList())
        private set
    var selectedProviderId by mutableStateOf("openai")
        private set

    /** Most recent locator from the active reader; seeds saved items. */
    private var currentLocator by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch {
            chatSettings.selectedProvider.collect { selectedProviderId = it }
        }
        viewModelScope.launch { refreshProviderOptions() }
    }

    private suspend fun refreshProviderOptions() {
        providerOptions = listOf("openai", "kimi", "openrouter", "deepseek", "anthropic").map { id ->
            val settings = chatSettings.settingsFor(id).first()
            val model = settings.model.ifBlank { null }
            ProviderOption(
                id = id,
                label = providerDisplayName(id) + (model?.let { " · $it" } ?: ""),
                enabled = ChatSettings.buildProvider(id, settings).isConfigured,
            )
        }
    }

    /** The model chooser sets the global provider new conversations will use. */
    fun selectProvider(id: String) {
        viewModelScope.launch { chatSettings.saveSelected(id) }
    }

    private suspend fun load() {
        val doc = repo.get(docId)
        if (doc == null) {
            uiState = UiState(loading = false, error = "Document not found")
            return
        }
        currentLocator = doc.lastLocator
        // The library row says what the file should be; the file's own bytes
        // are asked before a reader is handed it, so a page saved under a book
        // name is reported as one instead of surfacing as a failed open.
        val file = File(File(getApplication<Application>().filesDir, "documents"), doc.fileName)
        val problem = withContext(Dispatchers.IO) { BookFile.problem(file) }
        if (problem != null) {
            uiState = UiState(loading = false, title = doc.title, format = doc.format, error = problem)
            return
        }
        try {
            when (doc.format) {
                "pdf" -> {
                    val page = pdfStartPage(doc)
                    uiState = UiState(
                        loading = false,
                        title = doc.title,
                        format = "pdf",
                        pdfFile = file,
                        pdfInitialPage = page - 1,
                    )
                }
                "epub" -> uiState = UiState(
                    loading = false,
                    title = doc.title,
                    format = "epub",
                    epubFile = file,
                    // Raw stored locator: either Readium JSON or a legacy spine
                    // href, which the reader migrates onto the reading order.
                    epubInitialLocator = doc.lastLocator?.trim()?.takeIf { it.isNotBlank() },
                )
                else -> throw IllegalArgumentException("Unknown format ${doc.format}")
            }
        } catch (e: Exception) {
            uiState = UiState(loading = false, error = e.message ?: "Failed to open document")
        }
    }

    fun saveLocator(locator: String) {
        currentLocator = locator
        viewModelScope.launch(Dispatchers.IO) { repo.saveLocator(docId, locator) }
    }

    /**
     * Adds selected text to the saved. [pageHint] is the PDF page read from
     * the selection's DOM ancestor when available; otherwise the current
     * locator (last PDF page / current EPUB spine href) is used.
     */
    fun addToSaved(text: String, pageHint: String?) {
        val locator = when (uiState.format) {
            "pdf" -> pageHint ?: currentLocator
            else -> currentLocator
        }
        viewModelScope.launch(Dispatchers.IO) { savedRepo.add(docId, text, locator) }
    }

    /** Resolves a selection's locator using the current format and last-known position. */
    fun resolveLocator(pageHint: String?): String? = when (uiState.format) {
        "pdf" -> pageHint ?: currentLocator
        else -> currentLocator
    }

    /** 1-based page to open at; also seeds the locator for the first saved item. */
    private fun pdfStartPage(doc: DocumentEntity): Int {
        val page = doc.lastLocator?.toIntOrNull()?.takeIf { it > 0 } ?: 1
        currentLocator = page.toString()
        return page
    }
}
