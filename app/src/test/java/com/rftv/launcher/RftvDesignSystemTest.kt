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
        assertTrue(RftvDesignSystem.Size.PANEL_WIDTH_FRACTION in 0.84f..0.92f)
        assertTrue(RftvDesignSystem.Size.SETTINGS_SIDEBAR_WIDTH_FRACTION in 0.20f..0.32f)
        assertTrue(RftvDesignSystem.Size.SETTINGS_PANEL_WIDTH_FRACTION in 0.88f..0.96f)
        assertTrue(RftvDesignSystem.Size.SETTINGS_PANEL_HEIGHT_FRACTION in 0.82f..0.94f)
        assertTrue(RftvDesignSystem.Size.SETTINGS_NAV_ITEM_HEIGHT in 32..48)
    }

    @Test
    fun focusAndSurfaceMotionAreBounded() {
        assertTrue(RftvDesignSystem.Size.FOCUS_SCALE > 1f)
        assertTrue(RftvDesignSystem.Size.FOCUS_SCALE <= 1.1f)
        assertTrue(RftvDesignSystem.Size.DOCK_FOCUS_SCALE > 1f)
        assertTrue(RftvDesignSystem.Size.DOCK_FOCUS_SCALE <= 1.15f)
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
    fun controlCenterAndScreensaverTokensStayWithinTvFriendlyRanges() {
        assertTrue(RftvDesignSystem.Radius.CONTROL_CENTER_PANEL >= 24)
        assertEquals(54, RftvDesignSystem.Size.DOCK_TILE_SIZE)
        assertEquals(26, RftvDesignSystem.Size.DOCK_ICON)
        assertEquals(293, RftvDesignSystem.Size.CONTROL_CENTER_PANEL_WIDTH)
        assertTrue(RftvDesignSystem.Radius.CONTROL_CENTER_TILE >= 24)
        assertTrue(RftvDesignSystem.Size.CONTROL_CENTER_WIDTH_FRACTION in 0.38f..0.48f)
        assertTrue(RftvDesignSystem.Size.CONTROL_CENTER_HEIGHT_FRACTION in 0.85f..0.96f)
        assertTrue(RftvDesignSystem.Size.CONTROL_CENTER_TILE_HEIGHT in 64..88)
        assertEquals(293, RftvDesignSystem.Size.CONTROL_CENTER_PANEL_WIDTH)
        assertEquals(11, RftvDesignSystem.Spacing.CONTROL_CENTER_COLUMN_GAP)
        assertEquals(56, RftvDesignSystem.Size.CONTROL_CENTER_COMPACT_TILE_HEIGHT)
        assertEquals(25, RftvDesignSystem.Size.CONTROL_CENTER_TILE_ICON_SIZE)
        assertEquals(7, RftvDesignSystem.Size.CONTROL_CENTER_TILE_BOTTOM_GAP)
        assertTrue(RftvDesignSystem.Size.CONTROL_CENTER_FOCUS_SCALE > 1f)
        assertTrue(RftvDesignSystem.Motion.CONTROL_CENTER_REVEAL_DURATION_MS in 120L..300L)
        assertTrue(RftvDesignSystem.GlassAlpha.CONTROL_CENTER_TILE_RESTING in 0..255)
        assertTrue(RftvDesignSystem.GlassAlpha.CONTROL_CENTER_TILE_FOCUSED in 0..255)
        assertTrue(RftvDesignSystem.Size.SCREENSAVER_CLOCK_TEXT_SP in 52f..72f)
        assertTrue(RftvDesignSystem.Size.SCREENSAVER_HINT_TEXT_SP in 12f..18f)
    }

    @Test
    fun sharedGlassMaterialsUseCentralizedBoundedTokens() {
        val restingDock = RftvDesignSystem.ColorTokens.DOCK_SURFACE
        val focusedDock = RftvDesignSystem.ColorTokens.DOCK_TILE_FOCUSED
        val restingInteractive = RftvDesignSystem.ColorTokens.INTERACTIVE_RESTING_TOP
        val focusedInteractive = RftvDesignSystem.ColorTokens.INTERACTIVE_FOCUSED_TOP

        assertEquals(0x54, restingDock ushr 24 and 0xFF)
        assertTrue((focusedDock ushr 24 and 0xFF) > (restingDock ushr 24 and 0xFF))
        assertTrue((restingInteractive ushr 24 and 0xFF) < (focusedInteractive ushr 24 and 0xFF))
        assertEquals(23, RftvDesignSystem.Radius.DOCK_SURFACE)
        assertEquals(12, RftvDesignSystem.Radius.SETTINGS_HINT)
        assertEquals(7, RftvDesignSystem.Radius.SETTINGS_NAV_ICON)
        assertEquals(0x38, RftvDesignSystem.ColorTokens.DOCK_SURFACE_STROKE ushr 24 and 0xFF)
        assertEquals(0x26, RftvDesignSystem.ColorTokens.SETTINGS_HINT_STROKE ushr 24 and 0xFF)
    }

    @Test
    fun semanticRiskColorsRemainDistinct() {
        assertTrue(RftvDesignSystem.ColorTokens.RISK_CRITICAL != RftvDesignSystem.ColorTokens.RISK_REVIEW)
        assertEquals(-1, RftvDesignSystem.ColorTokens.TEXT_PRIMARY)
    }
}
