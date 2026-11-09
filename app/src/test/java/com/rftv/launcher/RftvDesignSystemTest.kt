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
        assertTrue(RftvDesignSystem.Size.DOCK_TILE_PADDING_HORIZONTAL > 0)
        assertTrue(RftvDesignSystem.Size.DOCK_TILE_PADDING_TOP > 0)
        assertTrue(RftvDesignSystem.Size.DOCK_TILE_PADDING_BOTTOM > 0)
        assertTrue(RftvDesignSystem.Size.EMPTY_DOCK_HEIGHT > RftvDesignSystem.Size.DOCK_TILE_HEIGHT / 2)
        assertTrue(RftvDesignSystem.Size.EMPTY_DOCK_TEXT_SP in 12f..18f)
        assertTrue(RftvDesignSystem.Size.HOME_DATE_LEFT_PADDING >= 0)
        assertTrue(RftvDesignSystem.Spacing.DOCK_TILE_HORIZONTAL_MARGIN in 0..8)
        assertTrue(RftvDesignSystem.Size.SETTINGS_SIDEBAR_WIDTH_FRACTION in 0.20f..0.32f)
        assertTrue(RftvDesignSystem.Size.SETTINGS_PANEL_WIDTH_FRACTION in 0.88f..0.96f)
        assertTrue(RftvDesignSystem.Size.SETTINGS_PANEL_HEIGHT_FRACTION in 0.82f..0.94f)
        assertTrue(RftvDesignSystem.Size.SETTINGS_NAV_ITEM_HEIGHT in 48..72)
    }

    @Test
    fun focusAndSurfaceMotionAreBounded() {
        assertTrue(RftvDesignSystem.Size.FOCUS_SCALE > 1f)
        assertTrue(RftvDesignSystem.Size.FOCUS_SCALE <= 1.1f)
        assertTrue(RftvDesignSystem.Size.DOCK_FOCUS_SCALE > 1f)
        assertTrue(RftvDesignSystem.Size.DOCK_FOCUS_SCALE <= 1.1f)
        assertTrue(RftvDesignSystem.Radius.DOCK_TILE > 0)
        assertTrue(RftvDesignSystem.GlassAlpha.DOCK_TILE_RESTING < RftvDesignSystem.GlassAlpha.DOCK_TILE_FOCUSED)
        assertTrue(RftvDesignSystem.GlassAlpha.DOCK_TILE_FOCUSED <= 255)
        assertTrue(RftvDesignSystem.GlassAlpha.DOCK_SURFACE in 0..255)
        assertTrue(RftvDesignSystem.GlassAlpha.CARD_SURFACE in 0..255)
        assertTrue(RftvDesignSystem.GlassAlpha.DOCK_SURFACE < RftvDesignSystem.GlassAlpha.CARD_SURFACE)
        assertTrue(RftvDesignSystem.GlassAlpha.SURFACE_STROKE in 0..255)
        assertTrue(RftvDesignSystem.GlassAlpha.PANEL_STROKE in 0..255)
        assertTrue(RftvDesignSystem.GlassAlpha.SELECTED_RESTING_FLOOR in 0..255)
        assertTrue(RftvDesignSystem.Size.CLOCK_SHADOW_RADIUS > RftvDesignSystem.Size.DATE_SHADOW_RADIUS)
        assertTrue(RftvDesignSystem.Motion.FOCUS_DURATION_MS in 80L..250L)
        assertTrue(RftvDesignSystem.Motion.HOME_FADE_DURATION_MS > 0L)
        assertTrue(RftvDesignSystem.Motion.DOCK_REVEAL_DURATION_MS in 100L..250L)
        assertTrue(RftvDesignSystem.Motion.SECONDARY_REVEAL_DURATION_MS in 100L..300L)
    }

    @Test
    fun semanticRiskColorsRemainDistinct() {
        assertTrue(RftvDesignSystem.ColorTokens.RISK_CRITICAL != RftvDesignSystem.ColorTokens.RISK_REVIEW)
        assertEquals(-1, RftvDesignSystem.ColorTokens.TEXT_PRIMARY)
    }
}
