package ru.zhma.campaign.server

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import ru.zhma.campaign.ai.image.PlaceholderImageGen
import ru.zhma.campaign.server.images.Watermark
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WatermarkTest {
    @Test
    fun watermarkKeepsSizeAndChangesPixels() {
        val png = runBlocking { PlaceholderImageGen().generate("x", 320, 200) }
        val marked = Watermark.apply(png, "ЧЕРНОВИК · НЕ ПРОШЛО МОДЕРАЦИЮ")
        val a = ImageIO.read(ByteArrayInputStream(png))
        val b = ImageIO.read(ByteArrayInputStream(marked))
        assertEquals(a.width, b.width)
        assertEquals(a.height, b.height)
        var diff = 0
        for (x in 0 until a.width step 4) for (y in 0 until a.height step 4) if (a.getRGB(x, y) != b.getRGB(x, y)) diff++
        assertTrue(diff > 50, "водяной знак не нарисовался: $diff")
        java.io.File(System.getProperty("java.io.tmpdir"), "watermark-sample.png").writeBytes(marked)
    }
}
