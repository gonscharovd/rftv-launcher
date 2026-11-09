package com.rftv.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassBackdropTest {
    @Test
    fun boxBlurKeepsDimensionsAndSoftensHighContrastPixels() {
        val width = 8
        val height = 8
        val source = IntArray(width * height) { index ->
            if ((index % width) < width / 2) 0xFFFF0000.toInt() else 0xFF0000FF.toInt()
        }

        val result = GlassBackdrop.blurPixels(source, width, height, radius = 1, passes = 2)

        assertEquals(width * height, result.size)
        assertNotEquals(source[3 * width + 3], result[3 * width + 3])
        assertTrue(result.all { (it ushr 24) == 0xFF })
    }

    @Test
    fun rejectsMismatchedPixelBuffer() {
        assertThrows(IllegalArgumentException::class.java) {
            GlassBackdrop.blurPixels(intArrayOf(0), 2, 2)
        }
    }
}
