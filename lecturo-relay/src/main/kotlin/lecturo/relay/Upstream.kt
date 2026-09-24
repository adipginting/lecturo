package lecturo.relay

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class UpstreamReply(val status: Int, val body: String)

/**
 * A pass-through to the one model behind the relay. The relay authenticates
 * devices, replaces whatever model the app asked for with its own, and leaves
 * everything else — history, images, prompts — exactly as the app wrote it.
 * Nothing but the token counts is ever read out of the exchange.
 */
class Upstream(private val config: RelayConfig) {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    fun chat(body: String): UpstreamReply {
        val request = HttpRequest.newBuilder(URI.create("${config.upstreamBaseUrl}/chat/completions"))
            .timeout(Duration.ofSeconds(120))
            .header("Authorization", "Bearer ${config.upstreamApiKey}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(withModel(body)))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        return UpstreamReply(response.statusCode(), response.body())
    }

    private fun withModel(body: String): String {
        val element = runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { return body }
        val withModel = element.toMutableMap()
        withModel["model"] = JsonPrimitive(config.model)
        return JsonObject(withModel).toString()
    }

    /** The tokens the upstream counted for a reply; zero when it said nothing. */
    fun tokensIn(body: String): Pair<Int, Int> = runCatching {
        val usage = json.parseToJsonElement(body).jsonObject["usage"]?.jsonObject
        val prompt = usage?.get("prompt_tokens")?.jsonPrimitive?.intOrNull ?: 0
        val completion = usage?.get("completion_tokens")?.jsonPrimitive?.intOrNull ?: 0
        prompt to completion
    }.getOrDefault(0 to 0)
}
