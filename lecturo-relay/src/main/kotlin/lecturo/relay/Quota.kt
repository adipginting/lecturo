package lecturo.relay

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** What one device has spent in one day. Counters only — never content. */
@Serializable
data class DeviceUsage(
    val requests: Int = 0,
    val tokens: Int = 0,
)

/**
 * What a device is allowed per day. "free" is the only plan that exists; the
 * field is where a paid one would hang, decided here and never in the app.
 */
data class Plan(val id: String, val dailyRequests: Int, val dailyTokens: Int) {
    companion object {
        fun free(config: RelayConfig): Plan =
            Plan("free", config.freeDailyRequests, config.freeDailyTokens)
    }
}

sealed interface QuotaVerdict {
    object Allowed : QuotaVerdict
    data class Denied(val message: String) : QuotaVerdict
}

/**
 * Whether [usage] leaves room for one more call under [plan]. Checked before
 * forwarding, so a denied device never spends upstream tokens.
 */
fun quotaVerdict(usage: DeviceUsage, plan: Plan): QuotaVerdict = when {
    usage.requests >= plan.dailyRequests ->
        QuotaVerdict.Denied(
            "Daily free limit reached (${plan.dailyRequests} requests). It resets at midnight UTC.",
        )

    usage.tokens >= plan.dailyTokens ->
        QuotaVerdict.Denied(
            "Daily free token budget reached (${plan.dailyTokens} tokens). " +
                "It resets at midnight UTC.",
        )

    else -> QuotaVerdict.Allowed
}

/** The usage [requests] calls and [tokens] tokens leave behind. */
fun DeviceUsage.plus(requests: Int, tokens: Int): DeviceUsage =
    DeviceUsage(this.requests + requests, this.tokens + tokens)

fun today(now: LocalDate = LocalDate.now()): String = now.toString()
