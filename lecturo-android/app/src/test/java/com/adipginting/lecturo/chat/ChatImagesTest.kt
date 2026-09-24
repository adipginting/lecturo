package com.adipginting.lecturo.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatImagesTest {

    @Test
    fun `images within the cap are kept as they are`() {
        assertEquals(1568 to 1200, scaledImageSize(1568, 1200))
        assertEquals(800 to 600, scaledImageSize(800, 600))
        assertEquals(1 to 1, scaledImageSize(1, 1))
    }

    @Test
    fun `a wide image is scaled down on its long edge`() {
        assertEquals(1568 to 784, scaledImageSize(3136, 1568))
    }

    @Test
    fun `a tall image is scaled down on its long edge`() {
        assertEquals(392 to 1568, scaledImageSize(784, 3136))
    }

    @Test
    fun `scaling never collapses the short edge to zero`() {
        val (width, height) = scaledImageSize(100_000, 1)
        assertEquals(1568, width)
        assertEquals(1, height)
    }

    @Test
    fun `rounding keeps the aspect ratio close`() {
        val (width, height) = scaledImageSize(2000, 1000)
        assertEquals(1568, width)
        assertEquals(784, height)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero sizes are rejected`() {
        scaledImageSize(0, 100)
    }
}
