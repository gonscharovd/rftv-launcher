package com.rftv.launcher

import kotlin.math.min

internal data class OverlaySnapshotSize(val width: Int, val height: Int)

internal object OverlaySnapshotSizing {
    fun fit(width: Int, height: Int, maxWidth: Int = 480): OverlaySnapshotSize {
        require(width > 0 && height > 0 && maxWidth > 0)
        val scale = min(1f, maxWidth / width.toFloat())
        return OverlaySnapshotSize(
            width = (width * scale).toInt().coerceAtLeast(1),
            height = (height * scale).toInt().coerceAtLeast(1)
        )
    }
}
