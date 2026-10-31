package ru.zhma.campaign.ai.image

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

class PlaceholderImageGen : ImageGenClient {

    override suspend fun generate(prompt: String, w: Int, h: Int): ByteArray = solidPng(w, h)

    private fun solidPng(w: Int, h: Int): ByteArray {
        val cw = w.coerceAtLeast(1)
        val ch = h.coerceAtLeast(1)

        // IHDR: width, height, bit depth=8, color type=2 (RGB), compression=0, filter=0, interlace=0
        val ihdr = ByteArrayOutputStream()
        ihdr.writeInt(cw)
        ihdr.writeInt(ch)
        ihdr.write(8)
        ihdr.write(2)
        ihdr.write(0)
        ihdr.write(0)
        ihdr.write(0)

        // Raw pixel data: each row prefixed with filter byte 0
        val raw = ByteArrayOutputStream()
        repeat(ch) {
            raw.write(0)
            repeat(cw) {
                raw.write(PNG_R)
                raw.write(PNG_G)
                raw.write(PNG_B)
            }
        }
        val idat = deflate(raw.toByteArray())

        val out = ByteArrayOutputStream()
        out.write(PNG_SIGNATURE)
        out.writeChunk("IHDR", ihdr.toByteArray())
        out.writeChunk("IDAT", idat)
        out.writeChunk("IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun deflate(input: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(input)
        deflater.finish()
        val buf = ByteArray(4096)
        val out = ByteArrayOutputStream()
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            out.write(buf, 0, n)
        }
        deflater.end()
        return out.toByteArray()
    }

    companion object {
        private const val PNG_R = 0x34
        private const val PNG_G = 0x56
        private const val PNG_B = 0x8B
        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
    }
}

private fun ByteArrayOutputStream.writeInt(v: Int) {
    write((v ushr 24) and 0xFF)
    write((v ushr 16) and 0xFF)
    write((v ushr 8) and 0xFF)
    write(v and 0xFF)
}

private fun ByteArrayOutputStream.writeChunk(type: String, data: ByteArray) {
    writeInt(data.size)
    val typeBytes = type.toByteArray(Charsets.US_ASCII)
    write(typeBytes)
    write(data)
    val crc = CRC32()
    crc.update(typeBytes, 0, typeBytes.size)
    crc.update(data, 0, data.size)
    writeInt(crc.value.toInt())
}
