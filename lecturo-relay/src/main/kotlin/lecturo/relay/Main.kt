package lecturo.relay

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.Executors

fun main() {
    val config = RelayConfig.fromEnv()
    val store = FileQuotaStore(File(config.quotaFile))
    val upstream = Upstream(config)
    val plan = Plan.free(config)

    val server = HttpServer.create(InetSocketAddress(config.port), 0)
    server.createContext("/health") { exchange ->
        exchange.respond(200, """{"status":"ok"}""")
    }
    // Both spellings: an OpenAI-compatible client appends its own path to
    // whatever base it was given, so a user may point at `/v1` or at the root.
    val models: (HttpExchange) -> Unit = { exchange ->
        exchange.respond(200, modelsJson(config.model))
    }
    server.createContext("/v1/models", models)
    server.createContext("/models", models)
    val chat: (HttpExchange) -> Unit = { exchange ->
        handleChat(exchange, store, upstream, plan)
    }
    server.createContext("/v1/chat/completions", chat)
    server.createContext("/chat/completions", chat)
    server.executor = Executors.newFixedThreadPool(8)
    server.start()
    println("Lecturo relay on :${config.port} — one model, ${config.model}")
}

/**
 * The whole API: identify the device, check its quota, pass the call through,
 * count what it cost. No body is logged and no message is stored — the only
 * state this server keeps is a counter per device per day.
 */
private fun handleChat(
    exchange: HttpExchange,
    store: FileQuotaStore,
    upstream: Upstream,
    plan: Plan,
) {
    if (exchange.requestMethod != "POST") {
        exchange.respond(405, errorJson("Use POST"))
        return
    }
    val device = exchange.requestHeaders.getFirst("Authorization")
        ?.removePrefix("Bearer ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    if (device == null) {
        exchange.respond(
            401,
            errorJson("Missing device id — the app identifies the install, no sign-in exists"),
        )
        return
    }
    val day = today()
    when (val verdict = quotaVerdict(store.usage(device, day), plan)) {
        is QuotaVerdict.Denied -> {
            exchange.respond(429, errorJson(verdict.message))
            return
        }

        QuotaVerdict.Allowed -> Unit
    }
    val body = exchange.requestBody.readBytes().decodeToString()
    val reply = try {
        upstream.chat(body)
    } catch (e: Exception) {
        exchange.respond(502, errorJson("Upstream failed: ${e.message}"))
        return
    }
    if (reply.status in 200..299) {
        val (prompt, completion) = upstream.tokensIn(reply.body)
        store.record(device, day, requests = 1, tokens = prompt + completion)
    }
    exchange.respond(reply.status, reply.body)
}

private fun modelsJson(model: String): String = buildJsonObject {
    put(
        "data",
        buildJsonArray { add(buildJsonObject { put("id", model) }) },
    )
}.toString()

private fun errorJson(message: String): String = buildJsonObject {
    put("error", buildJsonObject { put("message", message) })
}.toString()

private fun HttpExchange.respond(status: Int, body: String) {
    val bytes = body.toByteArray()
    responseHeaders.set("Content-Type", "application/json")
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}
