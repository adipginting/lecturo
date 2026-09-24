package com.adipginting.lecturo.chat

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.adipginting.lecturo.settings.KeyStoreCrypto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

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

    /** Token count past which the context counter turns red as a compact nudge. */
    val compactionThreshold: Flow<Int> =
        context.chatStore.data.map { it[KEY_THRESHOLD] ?: DEFAULT_COMPACTION_THRESHOLD }

    /** Per-model vision overrides, keyed by [visionOverrideKey]. */
    val visionOverrides: Flow<Map<String, Boolean>> =
        context.chatStore.data.map { decodeVisionOverrides(it[KEY_VISION_OVERRIDES]) }

    /** Whether formulas are typeset. Off shows the raw TeX a model wrote. */
    val mathRendering: Flow<Boolean> =
        context.chatStore.data.map { it[KEY_MATH] ?: true }

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

    /**
     * This install's anonymous id, created once. For the cloud relay it is the
     * "API key": no sign-up for testers, and the quota belongs to the install
     * rather than to an account — the same id every future plan hangs off.
     */
    suspend fun deviceId(): String = deviceIdMutex.withLock {
        cachedDeviceId ?: run {
            val stored = context.chatStore.data.first()[KEY_DEVICE_ID]
                ?.takeIf { it.isNotBlank() }
            val id = stored ?: UUID.randomUUID().toString().also { generated ->
                context.chatStore.edit { it[KEY_DEVICE_ID] = generated }
            }
            cachedDeviceId = id
            id
        }
    }

    /**
     * The configured provider for [providerId]. The relay has no key of its
     * own to ask for: when the user left one blank it goes out as this
     * install's device id, and the server decides what that is allowed to do.
     */
    suspend fun buildProviderFor(providerId: String): ChatProvider {
        val settings = settingsFor(providerId).first()
        val key = if (providerId == LECTURO) {
            settings.apiKey.ifBlank { deviceId() }
        } else {
            settings.apiKey
        }
        return buildProvider(providerId, settings.copy(apiKey = key))
    }

    suspend fun saveCompactionThreshold(tokens: Int) {
        context.chatStore.edit { it[KEY_THRESHOLD] = tokens }
    }

    suspend fun saveMathRendering(enabled: Boolean) {
        context.chatStore.edit { it[KEY_MATH] = enabled }
    }

    /** Sets — or with null clears — the vision override for one provider's model. */
    suspend fun saveVisionOverride(providerId: String, modelId: String, supported: Boolean?) {
        context.chatStore.edit { prefs ->
            val current = decodeVisionOverrides(prefs[KEY_VISION_OVERRIDES]).toMutableMap()
            val key = visionOverrideKey(providerId, modelId)
            if (supported == null) current.remove(key) else current[key] = supported
            prefs[KEY_VISION_OVERRIDES] = encodeVisionOverrides(current)
        }
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
        const val DEFAULT_COMPACTION_THRESHOLD = 8_000

        /** The relay's provider id; its key is the install's device id. */
        const val LECTURO = "lecturo"

        private val KEY_SELECTED = stringPreferencesKey("selected_provider")
        private val KEY_THRESHOLD = intPreferencesKey("compaction_threshold")
        private val KEY_VISION_OVERRIDES = stringPreferencesKey("vision_overrides")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        private val KEY_MATH = booleanPreferencesKey("math_rendering")

        private val deviceIdMutex = Mutex()

        @Volatile
        private var cachedDeviceId: String? = null

        /**
         * Overrides as one line per entry, `provider/model=1|0`; the lines are
         * sorted so equal maps encode to equal strings.
         */
        internal fun encodeVisionOverrides(overrides: Map<String, Boolean>): String =
            overrides.entries.sortedBy { it.key }
                .joinToString("\n") { (key, value) -> "$key=${if (value) "1" else "0"}" }

        internal fun decodeVisionOverrides(raw: String?): Map<String, Boolean> =
            raw.orEmpty().lines().mapNotNull { line ->
                val key = line.substringBeforeLast('=').trim()
                if (key.isEmpty()) return@mapNotNull null
                when (line.substringAfterLast('=', "").trim()) {
                    "1" -> key to true
                    "0" -> key to false
                    else -> null
                }
            }.toMap()

        fun defaultBaseUrl(providerId: String): String = when (providerId) {
            "openai" -> "https://api.openai.com/v1"
            "kimi" -> "https://api.moonshot.cn/v1"
            "openrouter" -> "https://openrouter.ai/api/v1"
            "deepseek" -> "https://api.deepseek.com"
            "anthropic" -> "https://api.anthropic.com"
            // The relay's address is wherever it is deployed; the user points
            // the app at it, and the app has no default for someone else's.
            else -> ""
        }

        fun defaultModel(providerId: String): String = when (providerId) {
            "openai" -> "gpt-4o-mini"
            "kimi" -> "moonshot-v1-8k"
            "openrouter" -> "openai/gpt-4o-mini"
            "deepseek" -> "deepseek-chat"
            "anthropic" -> "claude-sonnet-4-5"
            // The relay runs one model and decides it itself; this name is
            // what the picker shows, not something the app chooses with.
            LECTURO -> "lecturo-cloud"
            else -> ""
        }

        /** Builds the configured provider instance, applying default endpoints. */
        fun buildProvider(providerId: String, settings: ProviderSettings): ChatProvider =
            when (providerId) {
                "openai", "kimi", "openrouter", "deepseek", LECTURO -> OpenAiCompatibleProvider(
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
