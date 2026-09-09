package com.adipginting.lecturo.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Anthropic Claude via the native Messages API. Unlike the OpenAI shape, the
 * system prompt is a top-level field and replies are content blocks.
 * [apiBase] is overridable for tests and proxies.
 */
class AnthropicProvider(
    private val apiKey: String,
    private val model: String,
    private val apiBase: String = "https://api.anthropic.com",
    private val client: OkHttpClient = OkHttpClient(),
) : ChatProvider {

    override val id = "anthropic"
    override val displayName = providerDisplayName(id)
    override val isConfigured get() = apiKey.isNotBlank()

    private val json = Json { ignoreUnknownKeys = true }
    private val endpoint = apiBase.trimEnd('/') + "/v1/messages"

    override suspend fun chat(system: String, messages: List<ChatMessage>): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(endpoint)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .post(buildRequestBody(system, messages).toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                    ?: throw IllegalArgumentException("Empty response from $displayName")
                if (!response.isSuccessful) {
                    throw IllegalArgumentException("HTTP ${response.code} from $displayName: ${body.take(200)}")
                }
                parseResponseBody(body)
            }
        }

    internal fun buildRequestBody(system: String, messages: List<ChatMessage>): String =
        json.encodeToString(
            AnthropicRequest(
                model = model,
                maxTokens = 1024,
                system = system,
                messages = messages.map { AnthropicMessage(it.role, it.text) },
            ),
        )

    internal fun parseResponseBody(body: String): String {
        val text = json.decodeFromString<AnthropicResponse>(body)
            .content.filter { it.type == "text" }
            .joinToString("") { it.text ?: "" }
        if (text.isBlank()) throw IllegalArgumentException("No text block in $displayName response")
        return text
    }

    private companion object {
        val JSON_MEDIA = "application/json".toMediaType()
    }
}

@Serializable
internal data class AnthropicRequest(
    val model: String,
    @kotlinx.serialization.SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<AnthropicMessage>,
)

@Serializable
internal data class AnthropicMessage(
    val role: String,
    val content: String,
)

@Serializable
internal data class AnthropicResponse(
    val content: List<Block> = emptyList(),
) {
    @Serializable
    data class Block(val type: String? = null, val text: String? = null)
}
