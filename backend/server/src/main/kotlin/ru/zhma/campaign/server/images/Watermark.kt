package ru.zhma.campaign.server.images

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.font.TextLayout
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Водяной знак на картинке, пока кампания не прошла модерацию.
 * Текст повторяется по диагонали полупрозрачно и дублируется плашкой внизу. Оригинал на диске не меняется.
 */
object Watermark {
    init {
        System.setProperty("java.awt.headless", "true")
    }

    fun apply(
        png: ByteArray,
        text: String,
    ): ByteArray {
        val source = ImageIO.read(ByteArrayInputStream(png)) ?: return png
        val w = source.width
        val h = source.height
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        try {
            g.drawImage(source, 0, 0, null)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            val size = (minOf(w, h) / 14).coerceIn(14, 96)
            val font = Font(Font.SANS_SERIF, Font.BOLD, size)
            val layout = TextLayout(text, font, g.fontRenderContext)
            val textW = layout.bounds.width.toInt() + size * 2
            val stepY = size * 4

            val saved = g.transform
            g.transform(AffineTransform.getRotateInstance(-Math.PI / 6, w / 2.0, h / 2.0))
            g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.32f)
            val diag = (w + h) * 1.2
            var y = -diag / 2
            var row = 0
            while (y < h + diag / 2) {
                var x = -diag / 2 + if (row % 2 == 0) 0.0 else textW / 2.0
                while (x < w + diag / 2) {
                    val outline = layout.getOutline(AffineTransform.getTranslateInstance(x, y))
                    g.color = Color(0, 0, 0)
                    g.stroke = BasicStroke(size / 8f)
                    g.draw(outline)
                    g.color = Color(255, 255, 255)
                    g.fill(outline)
                    x += textW
                }
                y += stepY
                row++
            }
            g.transform = saved

            g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.75f)
            val bar = (size * 1.6).toInt()
            g.color = Color(20, 24, 32)
            g.fillRect(0, h - bar, w, bar)
            g.composite = AlphaComposite.SrcOver
            g.color = Color.WHITE
            val small = Font(Font.SANS_SERIF, Font.BOLD, (size * 0.7).toInt().coerceAtLeast(12))
            g.font = small
            val metrics = g.fontMetrics
            val tx = (w - metrics.stringWidth(text)) / 2
            g.drawString(text, tx.coerceAtLeast(8), h - (bar - metrics.ascent) / 2 - 2)
        } finally {
            g.dispose()
        }
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }
}
