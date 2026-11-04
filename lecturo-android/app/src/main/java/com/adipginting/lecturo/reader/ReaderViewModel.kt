package com.adipginting.lecturo.reader

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adipginting.lecturo.chat.ChatImages
import com.adipginting.lecturo.chat.ChatSettings
import com.adipginting.lecturo.chat.providerDisplayName
import com.adipginting.lecturo.chat.supportsVision
import com.adipginting.lecturo.AppContainer
import com.adipginting.lecturo.data.LecturoDatabase
import com.adipginting.lecturo.domain.library.Document
import com.adipginting.lecturo.domain.library.DocumentFormat
import com.adipginting.lecturo.domain.library.Documents
import com.adipginting.lecturo.domain.chat.Conversations
import com.adipginting.lecturo.domain.chat.Excerpt
import com.adipginting.lecturo.domain.chat.activeConversation
import com.adipginting.lecturo.domain.chat.latestDraft
import com.adipginting.lecturo.domain.reading.Locator
import com.adipginting.lecturo.domain.shared.DocumentId
import com.adipginting.lecturo.domain.shared.ProviderId
import com.adipginting.lecturo.library.BookFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
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

    private val documents: Documents = AppContainer.documents(app)
    private val chatImages = ChatImages(File(app.filesDir, "chat_images"))
    private val store: Conversations = AppContainer.conversations(app)
    private val chatSettings = ChatSettings(app)

    /** This document's conversations, drafts included. */
    private val documentChats = store.observeForDocument(DocumentId(docId))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The document's active chat: the sent conversation last engaged with. */
    val activeChat = documentChats
        .map { activeConversation(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The document's newest draft: a conversation whose first message is unsent. */
    val draftChat = documentChats
        .map { latestDraft(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** All selectable providers and the current global selection. */
    var providerOptions by mutableStateOf<List<ProviderOption>>(emptyList())
        private set
    var selectedProviderId by mutableStateOf("openai")
        private set

    /**
     * Whether the selected provider + model can read images. Gates the
     * screenshot action: the feature is off until the model behind it is one
     * that accepts pictures.
     */
    var visionSupported by mutableStateOf(false)
        private set

    /** Most recent locator from the active reader; seeds saved items. */
    private var currentLocator by mutableStateOf<String?>(null)

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch {
            chatSettings.selectedProvider.collect { selectedProviderId = it }
        }
        viewModelScope.launch {
            combine(chatSettings.selectedProvider, chatSettings.visionOverrides) { provider, overrides ->
                provider to overrides
            }.collect { (provider, overrides) ->
                val model = chatSettings.settingsFor(provider).first().model
                    .ifBlank { ChatSettings.defaultModel(provider) }
                visionSupported = supportsVision(provider, model, overrides)
            }
        }
        viewModelScope.launch { refreshProviderOptions() }
    }

    private suspend fun refreshProviderOptions() {
        val ids = listOf("openai", "kimi", "openrouter", "deepseek", "anthropic", ChatSettings.LECTURO)
        providerOptions = ids.map { id ->
            val settings = chatSettings.settingsFor(id).first()
            val model = settings.model.ifBlank { null }
            ProviderOption(
                id = id,
                label = providerDisplayName(id) + (model?.let { " · $it" } ?: ""),
                enabled = chatSettings.buildProviderFor(id).isConfigured,
            )
        }
    }

    /** The model chooser sets the global provider new conversations will use. */
    fun selectProvider(id: String) {
        viewModelScope.launch { chatSettings.saveSelected(id) }
    }

    private suspend fun load() {
        val doc = documents.get(DocumentId(docId))
        if (doc == null) {
            uiState = UiState(loading = false, error = "Document not found")
            return
        }
        currentLocator = doc.lastLocator?.stored
        // The library row says what the file should be; the file's own bytes
        // are asked before a reader is handed it, so a page saved under a book
        // name is reported as one instead of surfacing as a failed open.
        val file = File(File(getApplication<Application>().filesDir, "documents"), doc.fileName)
        val problem = withContext(Dispatchers.IO) { BookFile.problem(file) }
        if (problem != null) {
            uiState = UiState(loading = false, title = doc.title, format = doc.format.key, error = problem)
            return
        }
        try {
            when (doc.format) {
                DocumentFormat.Pdf -> {
                    val page = pdfStartPage(doc)
                    uiState = UiState(
                        loading = false,
                        title = doc.title,
                        format = "pdf",
                        pdfFile = file,
                        pdfInitialPage = page - 1,
                    )
                }
                DocumentFormat.Epub -> uiState = UiState(
                    loading = false,
                    title = doc.title,
                    format = "epub",
                    epubFile = file,
                    // Raw stored locator: either Readium JSON or a legacy spine
                    // href, which the reader migrates onto the reading order.
                    epubInitialLocator = doc.lastLocator?.stored,
                )
            }
        } catch (e: Exception) {
            uiState = UiState(loading = false, error = e.message ?: "Failed to open document")
        }
    }

    fun saveLocator(locator: String) {
        currentLocator = locator
        viewModelScope.launch(Dispatchers.IO) { documents.saveLocator(DocumentId(docId), locator) }
    }

    /** Resolves a selection's locator using the current format and last-known position. */
    fun resolveLocator(pageHint: String?): String? = when (uiState.format) {
        "pdf" -> pageHint ?: currentLocator
        else -> currentLocator
    }

    /**
     * Makes a draft and reports its id, so the composer can open on it. Defaults
     * to this book and the current position. The draft is persisted — it survives
     * leaving the book, and only a sent first message turns it into a
     * conversation.
     */
    fun createDraft(
        text: String,
        imagePath: String? = null,
        docId: String = this.docId,
        docTitle: String? = uiState.title,
        locator: String? = resolveLocator(null),
        onCreated: (String) -> Unit,
    ) {
        viewModelScope.launch {
            val id = store.createDraft(
                provider = ProviderId(chatSettings.selectedProvider.first()),
                excerpt = Excerpt(text = text, imagePath = imagePath),
                docId = DocumentId(docId),
                docTitle = docTitle,
                locator = locator,
            )
            onCreated(id)
        }
    }

    /** Stores a captured screenshot; calls back with its path, or null on failure. */
    fun saveScreenshot(bitmap: Bitmap, onSaved: (String?) -> Unit) {
        viewModelScope.launch {
            val path = runCatching { chatImages.save(bitmap) }.getOrNull()
            onSaved(path)
        }
    }

    /** 1-based page to open at; also seeds the locator for the first saved item. */
    private fun pdfStartPage(doc: Document): Int {
        val page = (doc.lastLocator as? Locator.PdfPage)?.number ?: 1
        currentLocator = page.toString()
        return page
    }
}
