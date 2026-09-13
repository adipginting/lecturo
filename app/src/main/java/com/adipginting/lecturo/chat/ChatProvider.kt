package com.adipginting.lecturo.chat

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** One message in a conversation; [role] is "user" or "assistant". */
data class ChatMessage(val role: String, val text: String)

/**
 * Client for calls to a model API. OkHttp's defaults (10s) are tripled: a
 * completion is generated token by token and regularly outlives an ordinary
 * web request.
 */
internal fun modelApiClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .build()

/**
 * A chat backend. Blocking request/response for v1; when streaming lands it
 * becomes a `Flow<String>` return without touching call sites' message model.
 */
interface ChatProvider {
    /** Stable id persisted on conversations; see [ALL_PROVIDER_IDS]. */
    val id: String
    val displayName: String

    /** False until the required settings (API key, ...) are entered. */
    val isConfigured: Boolean

    /** [system] carries the saved prompt plus serialized saved contents. */
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

val ALL_PROVIDER_IDS =
    listOf("openai", "kimi", "openrouter", "deepseek", "anthropic", "copilot")

fun providerDisplayName(id: String): String = when (id) {
    "openai" -> "OpenAI"
    "kimi" -> "Kimi"
    "openrouter" -> "OpenRouter"
    "deepseek" -> "DeepSeek"
    "anthropic" -> "Anthropic Claude"
    "copilot" -> "GitHub Copilot"
    else -> id
}
