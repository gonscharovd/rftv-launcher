package com.rftv.launcher

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertFalse
import org.junit.Test

class GlassBackdropTest {
    @Test
    fun createsDownsampledBlurWithoutRecyclingSource() {
        val source = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888)
        source.eraseColor(0xFFFF0000.toInt())

        val result = GlassBackdrop.createBlurredSnapshot(source, 16, 9)

        assertNotNull(result)
        assertNotSame(source, result)
        assertEquals(16, result!!.width)
        assertEquals(9, result.height)
        assertFalse(source.isRecycled)
        result.recycle()
        source.recycle()
    }

    @Test
    fun rejectsInvalidDimensions() {
        val source = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        assertEquals(null, GlassBackdrop.createBlurredSnapshot(source, 0, 8))
        source.recycle()
    }
}
