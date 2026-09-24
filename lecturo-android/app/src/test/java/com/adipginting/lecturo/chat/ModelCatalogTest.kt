package com.adipginting.lecturo.chat

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

class ModelCatalogTest {

    private var server: HttpServer? = null
    private val lastAuth = AtomicReference<String?>()
    private val lastPath = AtomicReference<String?>()

    @After
    fun tearDown() {
        server?.stop(0)
    }

    private fun startServer(): String {
        val s = HttpServer.create(InetSocketAddress(0), 0)
        s.createContext("/") { exchange ->
            lastPath.set(exchange.requestURI.path)
            lastAuth.set(
                exchange.requestHeaders.getFirst("Authorization")
                    ?: exchange.requestHeaders.getFirst("x-api-key"),
            )
            val body = """{"data":[{"id":"model-a"},{"id":"model-b"}]}"""
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        s.start()
        server = s
        return "http://127.0.0.1:${s.address.port}"
    }

    @Test
    fun `openai-compatible providers use Bearer auth on base slash models`() = runBlocking {
        val base = startServer()
        val models = ModelCatalog.fetch(
            "openrouter",
            ProviderSettings(baseUrl = "$base/v1", apiKey = "secret"),
        )
        assertEquals(listOf("model-a", "model-b"), models)
        assertEquals("/v1/models", lastPath.get())
        assertEquals("Bearer secret", lastAuth.get())
    }

    @Test
    fun `anthropic uses x-api-key on v1 slash models`() = runBlocking {
        val base = startServer()
        val models = ModelCatalog.fetch(
            "anthropic",
            ProviderSettings(baseUrl = base, apiKey = "sk-ant"),
        )
        assertEquals(listOf("model-a", "model-b"), models)
        assertEquals("/v1/models", lastPath.get())
        assertEquals("sk-ant", lastAuth.get())
    }

    @Test
    fun `blank key returns null without a request`() = runBlocking {
        startServer()
        assertNull(ModelCatalog.fetch("openai", ProviderSettings(apiKey = "")))
        assertNull(lastPath.get())
    }
}
