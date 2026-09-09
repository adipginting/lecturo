package com.adipginting.lecturo.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.adipginting.lecturo.settings.KeyStoreCrypto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.syncStore by preferencesDataStore(name = "sync")

/**
 * Server addresses and credentials for remote libraries. The Zotero API key
 * is stored AES-encrypted (AndroidKeyStore via [KeyStoreCrypto]).
 */
class SyncSettings(private val context: Context) {

    val calibreUrl: Flow<String> =
        context.syncStore.data.map { it[KEY_CALIBRE_URL] ?: "" }
    val zoteroUserId: Flow<String> =
        context.syncStore.data.map { it[KEY_ZOTERO_USER] ?: "" }
    val zoteroApiKey: Flow<String> =
        context.syncStore.data.map { prefs ->
            val raw = prefs[KEY_ZOTERO_KEY] ?: ""
            when {
                raw.isEmpty() -> ""
                else -> KeyStoreCrypto.decryptOrNull(raw) ?: raw.also {
                    // Legacy plaintext from before encryption landed:
                    // re-encrypt in place so no key sits in plain text.
                    context.syncStore.edit { it[KEY_ZOTERO_KEY] = KeyStoreCrypto.encrypt(raw) }
                }
            }
        }
    /** Test hook / self-hosted proxy: defaults to the real Web API. */
    val zoteroApiBase: Flow<String> =
        context.syncStore.data.map { it[KEY_ZOTERO_BASE] ?: "" }

    suspend fun saveCalibreUrl(url: String) {
        context.syncStore.edit { it[KEY_CALIBRE_URL] = url.trim() }
    }

    suspend fun saveZotero(userId: String, apiKey: String, apiBase: String) {
        context.syncStore.edit {
            it[KEY_ZOTERO_USER] = userId.trim()
            it[KEY_ZOTERO_KEY] = KeyStoreCrypto.encrypt(apiKey.trim())
            it[KEY_ZOTERO_BASE] = apiBase.trim()
        }
    }

    private companion object {
        val KEY_CALIBRE_URL = stringPreferencesKey("calibre_url")
        val KEY_ZOTERO_USER = stringPreferencesKey("zotero_user_id")
        val KEY_ZOTERO_KEY = stringPreferencesKey("zotero_api_key")
        val KEY_ZOTERO_BASE = stringPreferencesKey("zotero_api_base")
    }
}
