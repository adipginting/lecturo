package com.adipginting.lecturo.skills

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** The shape a marketplace catalog serves: `{"skills": [ … ]}`. */
@Serializable
internal data class SkillCatalogBody(val skills: List<SkillManifest> = emptyList())

/**
 * Reads a skills catalog. The catalog is a URL the user configures, exactly as
 * the remote libraries are: no Lecturo account, no background refresh.
 */
internal object SkillCatalog {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(catalogUrl: String): List<SkillManifest> = withContext(Dispatchers.IO) {
        val response = client.newCall(Request.Builder().url(catalogUrl).build()).execute()
        response.use {
            if (!it.isSuccessful) throw IllegalArgumentException("HTTP ${it.code} from the catalog")
            val body = it.body?.string() ?: throw IllegalArgumentException("Empty catalog")
            json.decodeFromString<SkillCatalogBody>(body).skills
        }
    }
}
