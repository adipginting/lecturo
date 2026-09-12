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
 * OpenAI-compatible chat-completions client shared by OpenAI, Kimi,
 * OpenRouter, and DeepSeek; only the base URL, key, and model differ.
 */
class OpenAiCompatibleProvider(
    override val id: String,
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val client: OkHttpClient = OkHttpClient(),
) : ChatProvider {

    override val displayName = providerDisplayName(id)
    override val isConfigured get() = apiKey.isNotBlank()

    private val json = Json { ignoreUnknownKeys = true }
    private val endpoint = baseUrl.trimEnd('/') + "/chat/completions"

    override suspend fun chat(system: String, messages: List<ChatMessage>): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(endpoint)
                .header("Authorization", "Bearer $apiKey")
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
            ChatRequest(
                model = model,
                messages = listOf(ApiMessage("system", system)) +
                    messages.map { ApiMessage(it.role, it.text) },
            ),
        )

    internal fun parseResponseBody(body: String): String =
        json.decodeFromString<ChatResponse>(body)
            .choices.firstOrNull()?.message?.content
            ?: throw IllegalArgumentException("No completion in $displayName response")

    private companion object {
        val JSON_MEDIA = "application/json".toMediaType()
    }
}

@Serializable
internal data class ChatRequest(
    val model: String,
    val messages: List<ApiMessage>,
)

@Serializable
internal data class ApiMessage(
    val role: String,
    val content: String,
)

@Serializable
internal data class ChatResponse(
    val choices: List<Choice> = emptyList(),
) {
    @Serializable
    data class Choice(val message: ApiMessage? = null)
}
