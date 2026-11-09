package com.rftv.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreensaverDashboardRepositoryTest {
    @Test
    fun weatherCodesAreMappedToReadableConditions() {
        assertEquals("Ясно", ScreensaverDashboardRepository.weatherDescription(0))
        assertEquals("Переменная облачность", ScreensaverDashboardRepository.weatherDescription(2))
        assertEquals("Туман", ScreensaverDashboardRepository.weatherDescription(45))
        assertEquals("Дождь", ScreensaverDashboardRepository.weatherDescription(61))
        assertEquals("Снег", ScreensaverDashboardRepository.weatherDescription(73))
        assertEquals("Гроза", ScreensaverDashboardRepository.weatherDescription(95))
    }

    @Test
    fun cacheFreshnessRejectsMissingFutureAndExpiredTimestamps() {
        val now = 1_000_000L
        assertTrue(ScreensaverDashboardRepository.isSourceFresh(now - 1_000L, now))
        assertFalse(ScreensaverDashboardRepository.isSourceFresh(0L, now))
        assertFalse(ScreensaverDashboardRepository.isSourceFresh(now + 1L, now))
        assertFalse(ScreensaverDashboardRepository.isSourceFresh(now - 15 * 60_000L, now))
    }
}
