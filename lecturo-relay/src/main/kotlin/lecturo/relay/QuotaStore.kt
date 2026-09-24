package lecturo.relay

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class StoredQuota(
    val days: Map<String, Map<String, DeviceUsage>> = emptyMap(),
)

/**
 * The quota counters, kept in a small JSON file: day → device → usage, with
 * only the last two days retained. A rewrite is a temp file and a rename, so
 * a crash mid-save cannot leave half a file behind.
 */
class FileQuotaStore(private val file: File) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private var days: MutableMap<String, MutableMap<String, DeviceUsage>> = load()

    @Synchronized
    fun usage(deviceId: String, day: String): DeviceUsage =
        days[day]?.get(deviceId) ?: DeviceUsage()

    @Synchronized
    fun record(deviceId: String, day: String, requests: Int, tokens: Int): DeviceUsage {
        val perDevice = days.getOrPut(day) { mutableMapOf() }
        val updated = (perDevice[deviceId] ?: DeviceUsage()).plus(requests, tokens)
        perDevice[deviceId] = updated
        prune()
        save()
        return updated
    }

    private fun prune() {
        val keep = days.keys.sorted().takeLast(2)
        days = days.filterKeys { it in keep }.toMutableMap()
    }

    private fun load(): MutableMap<String, MutableMap<String, DeviceUsage>> {
        if (!file.isFile) return mutableMapOf()
        return runCatching {
            json.decodeFromString<StoredQuota>(file.readText())
                .days.mapValues { (_, devices) -> devices.toMutableMap() }
                .toMutableMap()
        }.getOrElse { mutableMapOf() }
    }

    private fun save() {
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json.encodeToString(StoredQuota(days)))
        temp.renameTo(file)
    }
}
