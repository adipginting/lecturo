package com.adipginting.lecturo.chat

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.adipginting.lecturo.settings.KeyStoreCrypto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.chatStore by preferencesDataStore(name = "chat")

/** Per-provider connection settings. */
data class ProviderSettings(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
)

/**
 * Provider selection and per-provider API settings. API keys are stored
 * AES-encrypted (AndroidKeyStore via [KeyStoreCrypto]); the rest is plain
 * DataStore.
 */
class ChatSettings(private val context: Context) {

    val selectedProvider: Flow<String> =
        context.chatStore.data.map { it[KEY_SELECTED] ?: "openai" }

    fun settingsFor(providerId: String): Flow<ProviderSettings> =
        context.chatStore.data.map { prefs ->
            ProviderSettings(
                baseUrl = prefs[stringPreferencesKey("${providerId}_base")] ?: "",
                apiKey = KeyStoreCrypto.decrypt(
                    prefs[stringPreferencesKey("${providerId}_key")] ?: "",
                ),
                model = prefs[stringPreferencesKey("${providerId}_model")] ?: "",
            )
        }

    suspend fun saveSelected(providerId: String) {
        context.chatStore.edit { it[KEY_SELECTED] = providerId }
    }

    suspend fun saveProvider(providerId: String, settings: ProviderSettings) {
        context.chatStore.edit {
            it[stringPreferencesKey("${providerId}_base")] = settings.baseUrl.trim()
            it[stringPreferencesKey("${providerId}_key")] =
                KeyStoreCrypto.encrypt(settings.apiKey.trim())
            it[stringPreferencesKey("${providerId}_model")] = settings.model.trim()
        }
    }

    companion object {
        private val KEY_SELECTED = stringPreferencesKey("selected_provider")

        fun defaultBaseUrl(providerId: String): String = when (providerId) {
            "openai" -> "https://api.openai.com/v1"
            "kimi" -> "https://api.moonshot.cn/v1"
            "openrouter" -> "https://openrouter.ai/api/v1"
            "anthropic" -> "https://api.anthropic.com"
            else -> ""
        }

        fun defaultModel(providerId: String): String = when (providerId) {
            "openai" -> "gpt-4o-mini"
            "kimi" -> "moonshot-v1-8k"
            "openrouter" -> "openai/gpt-4o-mini"
            "anthropic" -> "claude-sonnet-4-5"
            else -> ""
        }

        /** Builds the configured provider instance, applying default endpoints. */
        fun buildProvider(providerId: String, settings: ProviderSettings): ChatProvider =
            when (providerId) {
                "openai", "kimi", "openrouter" -> OpenAiCompatibleProvider(
                    id = providerId,
                    baseUrl = settings.baseUrl.ifBlank { defaultBaseUrl(providerId) },
                    apiKey = settings.apiKey,
                    model = settings.model.ifBlank { defaultModel(providerId) },
                )
                "anthropic" -> AnthropicProvider(
                    apiKey = settings.apiKey,
                    model = settings.model.ifBlank { defaultModel(providerId) },
                    apiBase = settings.baseUrl.ifBlank { defaultBaseUrl(providerId) },
                )
                else -> CopilotProvider()
            }
    }
}
