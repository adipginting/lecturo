package com.adipginting.lecturo.chat

/** One message in a conversation; [role] is "user" or "assistant". */
data class ChatMessage(val role: String, val text: String)

/**
 * A chat backend. Blocking request/response for v1; when streaming lands it
 * becomes a `Flow<String>` return without touching call sites' message model.
 */
interface ChatProvider {
    /** Stable id persisted on conversations: "openai", "kimi", "anthropic", "copilot". */
    val id: String
    val displayName: String

    /** False until the required settings (API key, ...) are entered. */
    val isConfigured: Boolean

    /** [system] carries the saved prompt plus serialized basket contents. */
    suspend fun chat(system: String, messages: List<ChatMessage>): String
}

/** GitHub Copilot has no public chat API; listed but stubbed. */
class CopilotProvider : ChatProvider {
    override val id = "copilot"
    override val displayName = "GitHub Copilot"
    override val isConfigured = false

    override suspend fun chat(system: String, messages: List<ChatMessage>): String =
        throw UnsupportedOperationException("GitHub Copilot chat is not yet supported")
}

val ALL_PROVIDER_IDS = listOf("openai", "kimi", "openrouter", "anthropic", "copilot")

fun providerDisplayName(id: String): String = when (id) {
    "openai" -> "OpenAI"
    "kimi" -> "Kimi"
    "openrouter" -> "OpenRouter"
    "anthropic" -> "Anthropic Claude"
    "copilot" -> "GitHub Copilot"
    else -> id
}
