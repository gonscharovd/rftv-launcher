package com.rftv.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlaySnapshotSizingTest {
    @Test
    fun fullHdIsRenderedDirectlyAtQuarterLinearResolution() {
        assertEquals(OverlaySnapshotSize(480, 270), OverlaySnapshotSizing.fit(1920, 1080))
    }

    @Test
    fun hdAndSmallerSourcesAreNotUpscaled() {
        assertEquals(OverlaySnapshotSize(480, 270), OverlaySnapshotSizing.fit(1280, 720))
        assertEquals(OverlaySnapshotSize(384, 216), OverlaySnapshotSizing.fit(384, 216))
    }

    @Test
    fun portraitSourceKeepsAspectRatio() {
        assertEquals(OverlaySnapshotSize(480, 853), OverlaySnapshotSizing.fit(1080, 1920))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyDimensions() {
        OverlaySnapshotSizing.fit(0, 1080)
    }
}
