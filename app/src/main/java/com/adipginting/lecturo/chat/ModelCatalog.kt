package com.adipginting.lecturo.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches the model catalog a provider exposes (`GET {base}/models`).
 * OpenAI-compatible APIs and Anthropic both answer with
 * `{"data": [{"id": ...}]}`; only the auth headers differ.
 */
object ModelCatalog {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    /** Providers without a model-list endpoint return null. */
    suspend fun fetch(providerId: String, settings: ProviderSettings): List<String>? =
        withContext(Dispatchers.IO) {
            val base = settings.baseUrl.ifBlank { ChatSettings.defaultBaseUrl(providerId) }
                .trimEnd('/')
            if (base.isBlank() || settings.apiKey.isBlank()) return@withContext null
            val url = if (providerId == "anthropic") "$base/v1/models" else "$base/models"
            val request = Request.Builder().url(url).apply {
                if (providerId == "anthropic") {
                    header("x-api-key", settings.apiKey)
                    header("anthropic-version", "2023-06-01")
                } else {
                    header("Authorization", "Bearer ${settings.apiKey}")
                }
            }.build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                    ?: throw IllegalArgumentException("Empty response")
                if (!response.isSuccessful) {
                    throw IllegalArgumentException("HTTP ${response.code}: ${body.take(200)}")
                }
                json.decodeFromString<ModelsResponse>(body).data.map { it.id }
            }
        }
}

@Serializable
internal data class ModelsResponse(val data: List<ModelEntry> = emptyList())

@Serializable
internal data class ModelEntry(val id: String)
