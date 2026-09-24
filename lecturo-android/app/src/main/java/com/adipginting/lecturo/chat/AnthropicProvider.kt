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
    private val client: OkHttpClient = modelApiClient(),
) : ChatProvider {

    override val id = "anthropic"
    override val displayName = providerDisplayName(id)
    override val isConfigured get() = apiKey.isNotBlank()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val endpoint = apiBase.trimEnd('/') + "/v1/messages"

    override suspend fun chat(system: String, messages: List<ChatMessage>): ChatReply =
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
                messages = messages.map { AnthropicMessage(it.role, it.contentBlocks()) },
            ),
        )

    internal fun parseResponseBody(body: String): ChatReply {
        val parsed = json.decodeFromString<AnthropicResponse>(body)
        val text = parsed.content.filter { it.type == "text" }
            .joinToString("") { it.text ?: "" }
        if (text.isBlank()) throw IllegalArgumentException("No text block in $displayName response")
        return ChatReply(text, parsed.usage?.inputTokens, parsed.usage?.outputTokens)
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
    val content: List<AnthropicBlock>,
)

@Serializable
internal data class AnthropicBlock(
    val type: String,
    val text: String? = null,
    val source: AnthropicImageSource? = null,
)

@Serializable
internal data class AnthropicImageSource(
    val type: String,
    @kotlinx.serialization.SerialName("media_type") val mediaType: String,
    val data: String,
)

/** Image block first, as Anthropic's docs recommend, then the text. */
private fun ChatMessage.contentBlocks(): List<AnthropicBlock> =
    listOfNotNull(
        imageBase64?.let {
            AnthropicBlock(
                type = "image",
                source = AnthropicImageSource(
                    type = "base64",
                    mediaType = imageMime ?: "image/jpeg",
                    data = it,
                ),
            )
        },
        AnthropicBlock(type = "text", text = text),
    )

@Serializable
internal data class AnthropicResponse(
    val content: List<Block> = emptyList(),
    val usage: Usage? = null,
) {
    @Serializable
    data class Block(val type: String? = null, val text: String? = null)

    @Serializable
    data class Usage(
        @kotlinx.serialization.SerialName("input_tokens") val inputTokens: Int? = null,
        @kotlinx.serialization.SerialName("output_tokens") val outputTokens: Int? = null,
    )
}
