package com.rftv.launcher

/**
 * Computes a centered Dock capsule width from the number of app tiles plus All Apps.
 * The stored width preference is treated as a maximum cap, not a fixed width.
 */
internal object DockWidthPolicy {
    fun widthDp(tileCount: Int, screenWidthDp: Int, maxWidthPercent: Int): Int {
        val minimumWidthDp = HORIZONTAL_INSETS_DP * 2 + MINIMUM_TILES * (TILE_SIZE_DP + TILE_GAP_DP)
        val naturalWidthDp = HORIZONTAL_INSETS_DP * 2 +
            tileCount.coerceAtLeast(1) * (TILE_SIZE_DP + TILE_GAP_DP)
        val screenBoundDp = (screenWidthDp - SCREEN_SIDE_INSET_DP * 2).coerceAtLeast(minimumWidthDp)
        val preferenceBoundDp = screenWidthDp * maxWidthPercent.coerceIn(65, 100) / 100
        val maximumWidthDp = minOf(screenBoundDp, preferenceBoundDp).coerceAtLeast(minimumWidthDp)
        return naturalWidthDp.coerceIn(minimumWidthDp, maximumWidthDp)
    }

    private const val TILE_SIZE_DP = 54
    private const val TILE_GAP_DP = 10
    private const val HORIZONTAL_INSETS_DP = 26
    private const val MINIMUM_TILES = 3
    private const val SCREEN_SIDE_INSET_DP = 54
}
