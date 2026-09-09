package com.adipginting.lecturo.reader

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adipginting.lecturo.chat.ChatSettings
import com.adipginting.lecturo.chat.providerDisplayName
import com.adipginting.lecturo.data.BasketRepository
import com.adipginting.lecturo.data.ChatRepository
import com.adipginting.lecturo.data.LecturoDatabase
import com.adipginting.lecturo.data.DocumentEntity
import com.adipginting.lecturo.data.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder

class ReaderViewModel(
    app: Application,
    private val docId: String,
) : AndroidViewModel(app) {

    data class UiState(
        val loading: Boolean = true,
        val title: String = "",
        val format: String = "",
        val url: String? = null,
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
    private val basketRepo = BasketRepository(LecturoDatabase.get(app))
    private val chatRepo = ChatRepository(LecturoDatabase.get(app))
    private val chatSettings = ChatSettings(app)

    val prompts = chatRepo.observePrompts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** All selectable providers and the current global selection. */
    var providerOptions by mutableStateOf<List<ProviderOption>>(emptyList())
        private set
    var selectedProviderId by mutableStateOf("openai")
        private set

    /** Most recent locator reported by the WebView; seeds basket items. */
    private var currentLocator: String? = null

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch {
            chatSettings.selectedProvider.collect { selectedProviderId = it }
        }
        viewModelScope.launch { refreshProviderOptions() }
    }

    private suspend fun refreshProviderOptions() {
        providerOptions = listOf("openai", "kimi", "openrouter", "anthropic").map { id ->
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
        try {
            val url = when (doc.format) {
                "pdf" -> buildPdfUrl(doc)
                "epub" -> buildEpubUrl(doc)
                else -> throw IllegalArgumentException("Unknown format ${doc.format}")
            }
            uiState = UiState(loading = false, title = doc.title, format = doc.format, url = url)
        } catch (e: Exception) {
            uiState = UiState(loading = false, error = e.message ?: "Failed to open document")
        }
    }

    fun saveLocator(locator: String) {
        currentLocator = locator
        viewModelScope.launch(Dispatchers.IO) { repo.saveLocator(docId, locator) }
    }

    /**
     * Adds selected text to the basket. [pageHint] is the PDF page read from
     * the selection's DOM ancestor when available; otherwise the current
     * locator (last PDF page / current EPUB spine href) is used.
     */
    fun addToBasket(text: String, pageHint: String?) {
        val locator = when (uiState.format) {
            "pdf" -> pageHint ?: currentLocator
            else -> currentLocator
        }
        viewModelScope.launch(Dispatchers.IO) { basketRepo.add(docId, text, locator) }
    }

    /** Resolves a selection's locator using the current format and last-known position. */
    fun resolveLocator(pageHint: String?): String? = when (uiState.format) {
        "pdf" -> pageHint ?: currentLocator
        else -> currentLocator
    }

    private fun buildPdfUrl(doc: DocumentEntity): String {
        val fileUrl = URLEncoder.encode("$ASSET_ORIGIN/doc/${doc.fileName}", "UTF-8")
        val page = doc.lastLocator?.toIntOrNull()?.takeIf { it > 0 }
        // No pagechanging event fires until the user scrolls; seed the
        // locator so basket items added on the first visible page get one.
        currentLocator = (page ?: 1).toString()

        return "$ASSET_ORIGIN/assets/pdfjs/web/viewer.html?file=$fileUrl" +
            (page?.let { "#page=$it" } ?: "")

    }

    private suspend fun buildEpubUrl(doc: DocumentEntity): String = withContext(Dispatchers.IO) {
        val ctx = getApplication<Application>()
        val epubFile = File(File(ctx.filesDir, "documents"), doc.fileName)
        val book = EpubBook.open(epubFile, File(ctx.filesDir, "epub"))
        val startHref = book.startHref(doc.lastLocator)
        // The initial loadUrl does not pass through shouldOverrideUrlLoading,
        // so seed the locator with the href we are about to display.
        currentLocator = startHref
        "$ASSET_ORIGIN/epub/${epubFile.nameWithoutExtension}/${encodePath(startHref)}"
    }

    companion object {
        const val ASSET_ORIGIN = "https://appassets.androidplatform.net"

        fun encodePath(path: String): String =
            path.split('/').joinToString("/") { Uri.encode(it) }
    }
}
