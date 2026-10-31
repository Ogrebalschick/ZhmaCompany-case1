package ru.zhma.campaign.ai.image

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

class PlaceholderImageGenTest {
    private val gen = PlaceholderImageGen()

    @Test
    fun `png signature is present`() =
        runBlocking {
            val png = gen.generate("any", 100, 50)
            val sig = intArrayOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            for (i in sig.indices) {
                assertEquals(sig[i], png[i].toInt() and 0xFF, "signature byte $i")
            }
        }

    @Test
    fun `png decodes to correct dimensions`() =
        runBlocking {
            val png = gen.generate("any", 120, 60)
            val img: BufferedImage = ImageIO.read(ByteArrayInputStream(png))
            assertEquals(120, img.width)
            assertEquals(60, img.height)
            // Pixel check: solid color (R,G,B) = (0x34, 0x56, 0x8B)
            val color = img.getRGB(5, 5) and 0xFFFFFF
            assertEquals((0x34 shl 16) or (0x56 shl 8) or 0x8B, color)
        }

    @Test
    fun `clamps to at least one pixel`() =
        runBlocking {
            val png = gen.generate("x", 0, 0)
            val img: BufferedImage = ImageIO.read(ByteArrayInputStream(png))
            assertTrue(img.width >= 1 && img.height >= 1)
        }
}
