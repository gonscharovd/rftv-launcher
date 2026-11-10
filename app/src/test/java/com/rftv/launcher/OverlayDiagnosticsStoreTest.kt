package com.rftv.launcher

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayDiagnosticsStoreTest {
    @After
    fun clearHistory() {
        OverlayDiagnosticsStore.clear()
    }

    @Test
    fun keepsOnlyLatestTenOpeningsInReverseChronologicalOrder() {
        repeat(12) { index ->
            OverlayDiagnosticsStore.begin("app_catalog", index.toLong(), index.toLong())
        }

        val latest = OverlayDiagnosticsStore.latest()
        assertEquals(10, latest.size)
        assertTrue(latest.first().openCount > latest.last().openCount)
        assertEquals(9, latest.first().openCount - latest.last().openCount)
    }

    @Test
    fun distinguishesFirstOpeningFromRepeatedOpening() {
        val first = OverlayDiagnosticsStore.begin("control_center", 100L, 200L)
        val second = OverlayDiagnosticsStore.begin("control_center", 110L, 210L)

        assertEquals("cold", first.temperature)
        assertEquals("repeat", second.temperature)
    }

    @Test
    fun clearingHistoryDoesNotResetColdOrRepeatClassification() {
        OverlayDiagnosticsStore.begin("app_catalog", 1L, 1L)
        OverlayDiagnosticsStore.clear()

        val next = OverlayDiagnosticsStore.begin("app_catalog", 2L, 2L)
        assertEquals("repeat", next.temperature)
        assertEquals(1, OverlayDiagnosticsStore.latest().size)
    }

    @Test
    fun storesOpenAndBlurMeasurementsWithoutDiskPersistence() {
        val record = OverlayDiagnosticsStore.begin("app_catalog", 100L, 200L)
        OverlayDiagnosticsStore.opened(record, 7L, 31L, 4096, 120L, 220L)
        OverlayDiagnosticsStore.blurCompleted(record, 14L, 1024, true)

        val stored = OverlayDiagnosticsStore.latest().single()
        assertEquals(7L, stored.captureMs)
        assertEquals(31L, stored.openToVisibleMs)
        assertEquals(4096, stored.sourceBitmapBytes)
        assertEquals(14L, stored.blurMs)
        assertEquals(1024, stored.blurredBitmapBytes)
        assertTrue(stored.blurSucceeded)
        assertEquals(100L, stored.javaHeapBeforeBytes)
        assertEquals(120L, stored.javaHeapAfterBytes)
        assertEquals(200L, stored.nativeHeapBeforeBytes)
        assertEquals(220L, stored.nativeHeapAfterBytes)
    }
}
