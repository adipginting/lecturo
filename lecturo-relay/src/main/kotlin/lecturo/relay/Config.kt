package lecturo.relay

/**
 * Everything the relay is told by its environment; there is no other
 * configuration, and no configuration state changes at runtime.
 */
data class RelayConfig(
    val port: Int,
    val upstreamBaseUrl: String,
    val upstreamApiKey: String,
    val model: String,
    val freeDailyRequests: Int,
    val freeDailyTokens: Int,
    val quotaFile: String,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): RelayConfig {
            fun required(name: String): String = env[name]?.takeIf { it.isNotBlank() }
                ?: error("Missing required environment variable $name")
            return RelayConfig(
                port = env["RELAY_PORT"]?.toIntOrNull() ?: 8787,
                upstreamBaseUrl = required("UPSTREAM_BASE_URL").trimEnd('/'),
                upstreamApiKey = required("UPSTREAM_API_KEY"),
                model = required("UPSTREAM_MODEL"),
                freeDailyRequests = env["FREE_DAILY_REQUESTS"]?.toIntOrNull() ?: 50,
                freeDailyTokens = env["FREE_DAILY_TOKENS"]?.toIntOrNull() ?: 100_000,
                quotaFile = env["QUOTA_FILE"]?.takeIf { it.isNotBlank() } ?: "relay-quota.json",
            )
        }
    }
}
