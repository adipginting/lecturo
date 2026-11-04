package com.adipginting.lecturo.domain.shared

import java.security.SecureRandom

/**
 * An RFC 9562 UUIDv7: a 48-bit Unix-millisecond timestamp, then randomness. The
 * difference from a v4 is order — v7 ids sort by creation time, so the newest is
 * the largest, and id generation needs no database row to hang off.
 *
 * [now] and [random10] are injectable so the layout and the ordering can be
 * asserted without sleeping or stubbing a clock global.
 */
fun uuidV7(
    now: Long = System.currentTimeMillis(),
    random10: ByteArray = ByteArray(10).also { RANDOM.nextBytes(it) },
): String {
    require(random10.size == 10) { "uuid v7 needs 10 random bytes" }
    val bytes = ByteArray(16)
    bytes[0] = (now ushr 40).toByte()
    bytes[1] = (now ushr 32).toByte()
    bytes[2] = (now ushr 24).toByte()
    bytes[3] = (now ushr 16).toByte()
    bytes[4] = (now ushr 8).toByte()
    bytes[5] = now.toByte()
    // Byte 6 carries the version nibble (7); byte 8 the RFC variant (10xx).
    bytes[6] = (0x70 or (random10[0].toInt() and 0x0F)).toByte()
    bytes[7] = random10[1]
    bytes[8] = (0x80 or (random10[2].toInt() and 0x3F)).toByte()
    for (i in 3 until 10) bytes[6 + i] = random10[i]
    return format(bytes)
}

private val RANDOM = SecureRandom()

private const val HEX = "0123456789abcdef"

private fun format(bytes: ByteArray): String = buildString(36) {
    for (i in 0 until 16) {
        val value = bytes[i].toInt() and 0xFF
        append(HEX[value ushr 4]).append(HEX[value and 0x0F])
        if (i == 3 || i == 5 || i == 7 || i == 9) append('-')
    }
}
