package io.github.x07xh.xiaoxu.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.ByteArrayOutputStream

/**
 * Rasterizes ONLY the precise SVG subset emitted by our bundled
 * qrcode-generator.mjs createSvgTag(4,4) fallback.
 * Never loads remote resources, HTML, images or arbitrary SVG paths.
 */
internal object NativeQrSvgRenderer {
    private val document = Regex(
        """\A<svg version="1\.1" xmlns="http://www\.w3\.org/2000/svg" width="(\d+)px" height="(\d+)px" viewBox="0 0 (\d+) (\d+)"  preserveAspectRatio="xMinYMin meet"><rect width="100%" height="100%" fill="white" cx="0" cy="0"/><path d="([^"]+)" stroke="transparent" fill="black"/></svg>\z"""
    )
    private val module = Regex("""M(\d+),(\d+)l4,0 0,4 -4,0 0,-4z """)

    fun rasterize(input: ByteArray): ByteArray {
        require(input.size in 500..220000)
        val text = String(input, Charsets.UTF_8)
        val fields = document.matchEntire(text)
            ?: throw IllegalArgumentException("invalid_svg_template")
        val sizes = (1..4).map { fields.groupValues[it].toIntOrNull() }
        val size = sizes[0] ?: throw IllegalArgumentException("invalid_size")
        if (size !in 88..1024 || sizes.any { it != size } ||
            (size - 8) % 4 != 0) throw IllegalArgumentException("invalid_dimensions")
        val data = fields.groupValues[5]
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val brush = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.FILL
            }
            var offset = 0
            var count = 0
            while (offset < data.length) {
                val found = module.find(data, offset)
                    ?: throw IllegalArgumentException("invalid_modules")
                if (found.range.first != offset)
                    throw IllegalArgumentException("unexpected_svg_path")
                val x = found.groupValues[1].toIntOrNull()
                    ?: throw IllegalArgumentException("invalid_x")
                val y = found.groupValues[2].toIntOrNull()
                    ?: throw IllegalArgumentException("invalid_y")
                if (x < 4 || y < 4 || x + 4 > size - 4 || y + 4 > size - 4 ||
                    x % 4 != 0 || y % 4 != 0)
                    throw IllegalArgumentException("out_of_bounds_module")
                canvas.drawRect(x.toFloat(), y.toFloat(),
                    (x + 4).toFloat(), (y + 4).toFloat(), brush)
                offset = found.range.last + 1
                count++
                if (count > 20000) throw IllegalArgumentException("too_many_modules")
            }
            if (count < 80) throw IllegalArgumentException("incomplete_qr")
            return ByteArrayOutputStream().use { result ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, result))
                    throw IllegalStateException("rasterize_failed")
                val bytes = result.toByteArray()
                if (bytes.size > 220000) throw IllegalArgumentException("png_too_large")
                bytes
            }
        } finally { bitmap.recycle() }
    }
}
