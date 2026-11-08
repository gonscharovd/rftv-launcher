package com.rftv.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RftvDesignSystemTest {

    @Test
    fun layoutTokensStayWithinUsableTvRanges() {
        assertTrue(RftvDesignSystem.Spacing.XXS > 0)
        assertTrue(RftvDesignSystem.Spacing.XXL > RftvDesignSystem.Spacing.XL)
        assertTrue(RftvDesignSystem.Radius.CONTROL > 0)
        assertTrue(RftvDesignSystem.Radius.DOCK > RftvDesignSystem.Radius.CARD)
        assertTrue(RftvDesignSystem.Size.CLOCK_TEXT_SP > RftvDesignSystem.Size.PAGE_TITLE_SP)
        assertTrue(RftvDesignSystem.Size.DOCK_ICON < RftvDesignSystem.Size.DOCK_TILE_WIDTH)
    }

    @Test
    fun focusAndSurfaceMotionAreBounded() {
        assertTrue(RftvDesignSystem.Size.FOCUS_SCALE > 1f)
        assertTrue(RftvDesignSystem.Size.FOCUS_SCALE <= 1.1f)
        assertTrue(RftvDesignSystem.Motion.FOCUS_DURATION_MS in 80L..250L)
        assertTrue(RftvDesignSystem.Motion.HOME_FADE_DURATION_MS > 0L)
    }

    @Test
    fun semanticRiskColorsRemainDistinct() {
        assertTrue(RftvDesignSystem.ColorTokens.RISK_CRITICAL != RftvDesignSystem.ColorTokens.RISK_REVIEW)
        assertEquals(-1, RftvDesignSystem.ColorTokens.TEXT_PRIMARY)
    }
}
