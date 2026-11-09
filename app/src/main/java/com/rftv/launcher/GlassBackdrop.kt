package com.rftv.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

/**
 * Low-resolution backdrop blur for Liquid Glass overlays on API 24+.
 *
 * This is intentionally a snapshot operation: callers run it off the UI thread and
 * reuse the resulting bitmap until the wallpaper changes. It is not a per-frame effect.
 */
internal object GlassBackdrop {
    fun createBlurredSnapshot(
        source: Bitmap,
        width: Int,
        height: Int,
        radius: Int = 7,
        passes: Int = 3
    ): Bitmap? {
        if (source.isRecycled || width <= 0 || height <= 0) return null
        val safeRadius = radius.coerceIn(1, 24)
        val safePasses = passes.coerceIn(1, 5)
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(
            source,
            null,
            Rect(0, 0, width, height),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        )
        val pixels = IntArray(width * height)
        val scratch = IntArray(width * height)
        output.getPixels(pixels, 0, width, 0, 0, width, height)
        repeat(safePasses) {
            boxBlur(pixels, scratch, width, height, safeRadius, horizontal = true)
            boxBlur(scratch, pixels, width, height, safeRadius, horizontal = false)
        }
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }

    private fun boxBlur(
        input: IntArray,
        output: IntArray,
        width: Int,
        height: Int,
        radius: Int,
        horizontal: Boolean
    ) {
        val length = if (horizontal) width else height
        val lines = if (horizontal) height else width
        val divisor = radius * 2 + 1
        for (line in 0 until lines) {
            var red = 0
            var green = 0
            var blue = 0
            for (offset in -radius..radius) {
                val position = offset.coerceIn(0, length - 1)
                val color = input[if (horizontal) line * width + position else position * width + line]
                red += (color ushr 16) and 0xFF
                green += (color ushr 8) and 0xFF
                blue += color and 0xFF
            }
            for (position in 0 until length) {
                output[if (horizontal) line * width + position else position * width + line] =
                    (0xFF shl 24) or
                        ((red / divisor) shl 16) or
                        ((green / divisor) shl 8) or
                        (blue / divisor)
                val leaving = (position - radius).coerceIn(0, length - 1)
                val entering = (position + radius + 1).coerceIn(0, length - 1)
                val oldColor = input[if (horizontal) line * width + leaving else leaving * width + line]
                val newColor = input[if (horizontal) line * width + entering else entering * width + line]
                red += ((newColor ushr 16) and 0xFF) - ((oldColor ushr 16) and 0xFF)
                green += ((newColor ushr 8) and 0xFF) - ((oldColor ushr 8) and 0xFF)
                blue += (newColor and 0xFF) - (oldColor and 0xFF)
            }
        }
    }
}
