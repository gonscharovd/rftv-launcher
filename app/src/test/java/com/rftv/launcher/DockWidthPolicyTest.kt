package com.rftv.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DockWidthPolicyTest {
    @Test
    fun growsWithTileCountUntilScreenCap() {
        val threeTiles = DockWidthPolicy.widthDp(3, 1920, 88)
        val fourTiles = DockWidthPolicy.widthDp(4, 1920, 88)
        assertTrue(fourTiles > threeTiles)
    }

    @Test
    fun hasMinimumThreeTileWidth() {
        val minimum = 26 * 2 + 3 * (54 + 10)
        assertEquals(minimum, DockWidthPolicy.widthDp(1, 1920, 88))
        assertEquals(minimum, DockWidthPolicy.widthDp(2, 1920, 88))
    }

    @Test
    fun neverExceedsConfiguredScreenBound() {
        val width = DockWidthPolicy.widthDp(100, 1920, 80)
        assertTrue(width <= 1920 * 80 / 100)
        assertTrue(width < 1920)
    }

    @Test
    fun narrowScreenStillKeepsMinimumUsableWidth() {
        val minimum = 26 * 2 + 3 * (54 + 10)
        assertEquals(minimum, DockWidthPolicy.widthDp(1, 300, 65))
    }
}
