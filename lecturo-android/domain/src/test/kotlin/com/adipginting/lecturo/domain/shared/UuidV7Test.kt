package com.adipginting.lecturo.domain.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pieces of RFC 9562 that matter to a caller: it parses as a UUID, it says
 * v7, and it sorts by the moment it was minted.
 */
class UuidV7Test {

    private val zero = ByteArray(10)

    @Test
    fun `it is a hyphenated uuid`() {
        val id = uuidV7(now = 0, random10 = zero)
        assertEquals(36, id.length)
        for (index in intArrayOf(8, 13, 18, 23)) {
            assertEquals("dash at $index", '-', id[index])
        }
    }

    @Test
    fun `it carries version 7 and the rfc variant`() {
        val id = uuidV7(now = 0, random10 = zero)
        assertEquals('7', id[14])
        assertTrue("variant was ${id[19]}", id[19] in "89ab")
    }

    @Test
    fun `a later timestamp sorts after an earlier one`() {
        val earlier = uuidV7(now = 1_000, random10 = zero)
        val later = uuidV7(now = 2_000, random10 = zero)
        assertTrue("$earlier should sort before $later", earlier < later)
    }

    @Test
    fun `the timestamp occupies the first 48 bits`() {
        val id = uuidV7(now = 0x000000010000L, random10 = zero)
        assertEquals("00000001-0000", id.substring(0, 13))
    }
}
