package lecturo.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class QuotaTest {

    private val plan = Plan("free", dailyRequests = 3, dailyTokens = 1_000)

    @Test
    fun `usage below both limits is allowed`() {
        assertEquals(QuotaVerdict.Allowed, quotaVerdict(DeviceUsage(2, 999), plan))
        assertEquals(QuotaVerdict.Allowed, quotaVerdict(DeviceUsage(), plan))
    }

    @Test
    fun `the request limit denies even with tokens left`() {
        val verdict = quotaVerdict(DeviceUsage(requests = 3, tokens = 0), plan)
        assertTrue(verdict is QuotaVerdict.Denied)
        assertTrue((verdict as QuotaVerdict.Denied).message.contains("requests"))
    }

    @Test
    fun `the token limit denies even with requests left`() {
        val verdict = quotaVerdict(DeviceUsage(requests = 1, tokens = 1_000), plan)
        assertTrue(verdict is QuotaVerdict.Denied)
        assertTrue((verdict as QuotaVerdict.Denied).message.contains("token"))
    }

    @Test
    fun `accrual adds to both counters`() {
        assertEquals(DeviceUsage(2, 300), DeviceUsage(1, 100).plus(1, 200))
    }

    @Test
    fun `the store remembers counters across reloads and separates devices`() {
        val file = Files.createTempFile("quota", ".json").toFile()
        file.delete()
        val store = FileQuotaStore(file)
        store.record("device-a", "2026-09-14", requests = 1, tokens = 100)
        store.record("device-a", "2026-09-14", requests = 2, tokens = 50)
        store.record("device-b", "2026-09-14", requests = 1, tokens = 10)

        val reloaded = FileQuotaStore(file)
        assertEquals(DeviceUsage(3, 150), reloaded.usage("device-a", "2026-09-14"))
        assertEquals(DeviceUsage(1, 10), reloaded.usage("device-b", "2026-09-14"))
        assertEquals(DeviceUsage(), reloaded.usage("device-c", "2026-09-14"))
        file.delete()
    }

    @Test
    fun `only the last two days are kept`() {
        val file = Files.createTempFile("quota", ".json").toFile()
        file.delete()
        val store = FileQuotaStore(file)
        store.record("device", "2026-09-12", 1, 1)
        store.record("device", "2026-09-13", 1, 1)
        store.record("device", "2026-09-14", 1, 1)

        val reloaded = FileQuotaStore(file)
        assertEquals(DeviceUsage(), reloaded.usage("device", "2026-09-12"))
        assertEquals(DeviceUsage(1, 1), reloaded.usage("device", "2026-09-13"))
        file.delete()
    }

    @Test
    fun `a corrupt file starts the day over instead of failing`() {
        val file = Files.createTempFile("quota", ".json").toFile()
        file.writeText("{ not json")
        val store = FileQuotaStore(file)
        assertEquals(DeviceUsage(), store.usage("device", "2026-09-14"))
        file.delete()
    }
}

class ConfigTest {

    @Test
    fun `defaults apply and required variables are read`() {
        val config = RelayConfig.fromEnv(
            mapOf(
                "UPSTREAM_BASE_URL" to "https://api.example.com/v1/",
                "UPSTREAM_API_KEY" to "sk-test",
                "UPSTREAM_MODEL" to "vision-model",
            ),
        )
        assertEquals(8787, config.port)
        assertEquals("https://api.example.com/v1", config.upstreamBaseUrl)
        assertEquals("vision-model", config.model)
        assertEquals(50, config.freeDailyRequests)
        assertEquals(100_000, config.freeDailyTokens)
    }

    @Test(expected = IllegalStateException::class)
    fun `a missing required variable is refused at startup`() {
        RelayConfig.fromEnv(mapOf("UPSTREAM_BASE_URL" to "https://api.example.com/v1"))
    }

    @Test
    fun `the free plan takes its limits from the environment`() {
        val config = RelayConfig.fromEnv(
            mapOf(
                "UPSTREAM_BASE_URL" to "https://api.example.com/v1",
                "UPSTREAM_API_KEY" to "sk-test",
                "UPSTREAM_MODEL" to "vision-model",
                "FREE_DAILY_REQUESTS" to "7",
                "FREE_DAILY_TOKENS" to "42",
            ),
        )
        assertEquals(Plan("free", 7, 42), Plan.free(config))
    }
}
