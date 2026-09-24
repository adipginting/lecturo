package com.adipginting.lecturo.chat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The decode subsample: the largest power of two that keeps the long edge at
 * or above the display cap, so a screenshot shown 40dp wide is not decoded at
 * full size.
 */
class ChatImageViewTest {

    @Test
    fun `an image already within the cap is decoded whole`() {
        assertEquals(1, sampleSizeFor(800, 600, 1024))
        assertEquals(1, sampleSizeFor(1024, 1024, 1024))
    }

    @Test
    fun `a larger image is halved until the long edge stays above the cap`() {
        assertEquals(2, sampleSizeFor(2048, 1000, 1024))
        assertEquals(4, sampleSizeFor(4096, 1000, 1024))
        assertEquals(8, sampleSizeFor(8192, 8192, 1024))
    }

    @Test
    fun `the sample never drops the long edge below the cap`() {
        val widths = listOf(1025, 1568, 1600, 2047, 2048, 3000)
        widths.forEach { width ->
            val sample = sampleSizeFor(width, 100, 1024)
            check(width / sample >= 1024) { "sample $sample under-decodes $width" }
        }
    }
}
