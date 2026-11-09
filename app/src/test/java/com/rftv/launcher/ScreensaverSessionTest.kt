package com.rftv.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreensaverSessionTest {
    @Test
    fun manualAndSystemSessionsCannotOverlap() {
        ScreensaverSession.release()
        assertTrue(ScreensaverSession.tryAcquire())
        assertFalse(ScreensaverSession.tryAcquire())
        ScreensaverSession.release()
        assertTrue(ScreensaverSession.tryAcquire())
        ScreensaverSession.release()
    }
}
