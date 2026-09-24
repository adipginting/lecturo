package com.adipginting.lecturo.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
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
    private val client: OkHttpClient = modelApiClient(),
) : ChatProvider {

    override val displayName = providerDisplayName(id)

    // The relay's key is auto-filled from the device id, so it is the address
    // that says whether a cloud provider is set up at all.
    override val isConfigured get() = apiKey.isNotBlank() && baseUrl.isNotBlank()

    private val json = Json { ignoreUnknownKeys = true }
    private val endpoint = baseUrl.trimEnd('/') + "/chat/completions"

    override suspend fun chat(system: String, messages: List<ChatMessage>): ChatReply =
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
                messages = listOf(ApiMessage("system", JsonPrimitive(system))) +
                    messages.map { ApiMessage(it.role, it.contentJson()) },
            ),
        )

    internal fun parseResponseBody(body: String): ChatReply {
        val parsed = json.decodeFromString<ChatResponse>(body)
        val text = parsed.choices.firstOrNull()?.message?.content
            ?: throw IllegalArgumentException("No completion in $displayName response")
        return ChatReply(text, parsed.usage?.promptTokens, parsed.usage?.completionTokens)
    }

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
    /** Plain string for text-only turns; a content-part array when an image rides along. */
    val content: JsonElement,
)

/**
 * A message's `content` field on the wire. Text-only stays a bare string so
 * providers that reject the array form are unaffected; an image turn becomes
 * `[{type:"text"…}, {type:"image_url"…}]` with the image inlined as a data URL.
 */
private fun ChatMessage.contentJson(): JsonElement {
    val image = imageBase64 ?: return JsonPrimitive(text)
    return buildJsonArray {
        addJsonObject {
            put("type", "text")
            put("text", text)
        }
        addJsonObject {
            put("type", "image_url")
            putJsonObject("image_url") {
                put("url", "data:${imageMime ?: "image/jpeg"};base64,$image")
            }
        }
    }
}

@Serializable
internal data class ChatResponse(
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null,
) {
    @Serializable
    data class Choice(val message: ResponseMessage? = null)

    @Serializable
    data class ResponseMessage(val content: String? = null)

    @Serializable
    data class Usage(
        @kotlinx.serialization.SerialName("prompt_tokens") val promptTokens: Int? = null,
        @kotlinx.serialization.SerialName("completion_tokens") val completionTokens: Int? = null,
    )
}
