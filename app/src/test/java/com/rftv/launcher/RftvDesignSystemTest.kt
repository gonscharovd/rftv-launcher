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
        assertTrue(RftvDesignSystem.Spacing.HOME_DOCK_HORIZONTAL_INSET > RftvDesignSystem.Spacing.HOME_ACTION_GAP)
        assertTrue(RftvDesignSystem.Size.HOME_WALLPAPER_BUTTON_HEIGHT in 40..56)
        assertTrue(RftvDesignSystem.Size.DOCK_LABEL_WIDTH < RftvDesignSystem.Size.DOCK_TILE_WIDTH)
        assertTrue(RftvDesignSystem.Size.DOCK_LABEL_HEIGHT in 24..36)
        assertTrue(RftvDesignSystem.Spacing.DOCK_TILE_HORIZONTAL_MARGIN in 0..8)
    }

    @Test
    fun focusAndSurfaceMotionAreBounded() {
        assertTrue(RftvDesignSystem.Size.FOCUS_SCALE > 1f)
        assertTrue(RftvDesignSystem.Size.FOCUS_SCALE <= 1.1f)
        assertTrue(RftvDesignSystem.Size.DOCK_FOCUS_SCALE > 1f)
        assertTrue(RftvDesignSystem.Size.DOCK_FOCUS_SCALE <= 1.1f)
        assertTrue(RftvDesignSystem.Radius.DOCK_TILE > 0)
        assertTrue(RftvDesignSystem.Motion.FOCUS_DURATION_MS in 80L..250L)
        assertTrue(RftvDesignSystem.Motion.HOME_FADE_DURATION_MS > 0L)
    }

    @Test
    fun semanticRiskColorsRemainDistinct() {
        assertTrue(RftvDesignSystem.ColorTokens.RISK_CRITICAL != RftvDesignSystem.ColorTokens.RISK_REVIEW)
        assertEquals(-1, RftvDesignSystem.ColorTokens.TEXT_PRIMARY)
    }
}
