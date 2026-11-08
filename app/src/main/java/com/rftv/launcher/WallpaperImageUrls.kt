package com.rftv.launcher

/**
 * Keeps picker thumbnails separate from full-screen wallpaper files.
 * Unsplash performs the resize server-side, so TV downloads less data and
 * never mistakes a small preview for the full 16:9 background.
 */
internal object WallpaperImageUrls {
    fun thumbnailUrl(imageUrl: String): String =
        imageUrl.replace("w=1920&h=1080", "w=480&h=270")

    fun cacheKey(isDark: Boolean, index: Int, thumbnail: Boolean): String =
        cacheFileName(isDark, index, thumbnail).removeSuffix(".jpg")

    fun cacheFileName(isDark: Boolean, index: Int, thumbnail: Boolean): String {
        val safeIndex = index.coerceAtLeast(0)
        val prefix = if (isDark) "night" else "day"
        val suffix = if (thumbnail) "-thumb" else ""
        return "$prefix-$safeIndex$suffix.jpg"
    }
}
