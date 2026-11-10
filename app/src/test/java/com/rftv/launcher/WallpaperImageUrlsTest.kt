package com.rftv.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperImageUrlsTest {

    @Test
    fun thumbnailRequestsAreResizedToPickerResolution() {
        val source = "https://images.unsplash.com/photo-123?auto=format&fit=crop&fm=jpg&q=82&w=1920&h=1080"
        assertEquals(
            "https://images.unsplash.com/photo-123?auto=format&fit=crop&fm=jpg&q=82&w=480&h=270",
            WallpaperImageUrls.thumbnailUrl(source)
        )
    }

    @Test
    fun thumbnailAndFullResolutionUseDifferentCacheFiles() {
        assertEquals("day-2.jpg", WallpaperImageUrls.cacheFileName(false, 2, false))
        assertEquals("day-2-thumb.jpg", WallpaperImageUrls.cacheFileName(false, 2, true))
        assertEquals("night-9-thumb.jpg", WallpaperImageUrls.cacheFileName(true, 9, true))
    }

    @Test
    fun previewAndFullSizeFailuresHaveIndependentKeys() {
        assertEquals("day-4", WallpaperImageUrls.cacheKey(false, 4, false))
        assertEquals("day-4-thumb", WallpaperImageUrls.cacheKey(false, 4, true))
    }

    @Test
    fun selectionKeysDistinguishThemeAndWallpaperWithoutThumbnailState() {
        assertEquals("day-2", WallpaperImageUrls.selectionKey(false, 2))
        assertEquals("night-2", WallpaperImageUrls.selectionKey(true, 2))
    }

    @Test
    fun bundledAssetsUseStableDayAndNightPaths() {
        assertEquals("wallpapers/day-0.jpg", WallpaperImageUrls.assetPath(false, 0))
        assertEquals("wallpapers/night-9.jpg", WallpaperImageUrls.assetPath(true, 9))
        assertEquals("wallpapers/day-0.jpg", WallpaperImageUrls.assetPath(false, -1))
    }

    @Test
    fun negativeIndexesCannotEscapeWallpaperCacheNamespace() {
        assertEquals("night-0.jpg", WallpaperImageUrls.cacheFileName(true, -1, false))
        assertEquals("night-0", WallpaperImageUrls.selectionKey(true, -1))
    }
}
