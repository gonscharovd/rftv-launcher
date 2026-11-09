package com.rftv.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View

/**
 * Shared visual language for RFTV.
 *
 * Android 9 / MT9632 profile: translucent fills, thin strokes and short property
 * animations. No real-time blur, RenderEffect or expensive per-frame shaders.
 */
internal object RftvDesignSystem {
    object ColorTokens {
        const val TEXT_PRIMARY = -1
        const val TEXT_SECONDARY = -2037517
        const val TEXT_MUTED = -4473925
        const val HOME_BACKDROP = -16777216
        const val WALLPAPER_FALLBACK = -15657932
        const val PANEL_SCRIM = -855308015
        const val PANEL_GRADIENT_START = -13944498
        const val PANEL_GRADIENT_END = -15656664
        const val PANEL_SURFACE = -266721735
        const val CONTROL_CENTER_SURFACE = -400873670
        const val RISK_CRITICAL = -41130
        const val RISK_REVIEW = -16826
        const val GLASS_STROKE = 0x56FFFFFF
        const val GLASS_FILL = 0x2DFFFFFF
        const val GLASS_FOCUS = 0x78FFFFFF
        const val CLOCK_SHADOW = -1358954496
        const val OVERLAY_TOP = 0x0C000000
        const val OVERLAY_BOTTOM = 0x76000000
        const val LABEL_ON_GLASS = -1092818689
        const val CONTROL_CENTER_SCRIM = 0x90030818
        const val CONTROL_CENTER_GRADIENT_START = -534557820
        const val CONTROL_CENTER_GRADIENT_MID = -301526233
        const val CONTROL_CENTER_GRADIENT_END = -401990613
        const val CONTROL_CENTER_STROKE = 0x7DBEE2FF
        const val CONTROL_CENTER_TILE_START = 0x5C539DE0
        const val CONTROL_CENTER_TILE_END = 0x4808192B
        const val CONTROL_CENTER_TILE_FOCUSED_START = -10765833
        const val CONTROL_CENTER_TILE_FOCUSED_END = -11955743
        const val CONTROL_CENTER_ICON = -10502145
        const val CONTROL_CENTER_SUBTITLE = -3941389
        const val CONTROL_CENTER_FOCUSED_TEXT = -16244161
        const val SCREENSAVER_GRADIENT_START = -16181200
        const val SCREENSAVER_GRADIENT_MID = -15190955
        const val SCREENSAVER_GRADIENT_END = -16446180
        const val SCREENSAVER_TEXT_SECONDARY = -587202561
        const val SCREENSAVER_TEXT_MUTED = -1342177281
        const val SCREENSAVER_GLASS = 0x26FFFFFF
    }

    object Spacing {
        const val XXS = 4
        const val XS = 8
        const val SM = 12
        const val MD = 16
        const val LG = 22
        const val XL = 32
        const val XXL = 54
        const val HOME_TOP_INSET = 34
        const val HOME_DOCK_HORIZONTAL_INSET = 54
        const val HOME_ACTION_GAP = 12
        const val HOME_WALLPAPER_RIGHT_INSET = 56
        const val HOME_WALLPAPER_DOCK_GAP = 38
        const val DOCK_CONTENT_PADDING = 10
        const val DOCK_BOTTOM_INSET = 22
        const val DOCK_TILE_HORIZONTAL_MARGIN = 4
        const val CONTROL_PANEL_TOP = 16
        const val CONTROL_PANEL_BOTTOM = 18
        const val CARD_PADDING_HORIZONTAL = 18
        const val CARD_PADDING_VERTICAL = 14
        const val PANEL_BODY_TOP = 12
        const val PANEL_BODY_BOTTOM = 18
        const val CONTROL_CENTER_PADDING = 18
        const val CONTROL_CENTER_HEADER_BOTTOM = 12
        const val CONTROL_CENTER_TILE_HORIZONTAL = 18
        const val CONTROL_CENTER_TILE_VERTICAL = 10
        const val CONTROL_CENTER_ICON_WIDTH = 56
        const val CONTROL_CENTER_ROW_GAP = 10
        const val CONTROL_CENTER_COLUMN_GAP = 6
        const val CONTROL_CENTER_RIGHT_INSET = 22
        const val CONTROL_CENTER_TOP_INSET = 18
        const val SETTINGS_SIDEBAR_PADDING = 14
        const val SETTINGS_CONTENT_LEFT_PADDING = 24
        const val SCREENSAVER_HORIZONTAL_PADDING = 72
        const val SCREENSAVER_TOP_PADDING = 36
        const val SCREENSAVER_BOTTOM_PADDING = 44
        const val SCREENSAVER_DETAILS_TOP_PADDING = 34
        const val SCREENSAVER_DETAILS_BOTTOM_PADDING = 28
        const val SCREENSAVER_LINE_SPACING = 8
    }

    object GlassAlpha {
        const val BUTTON = 45
        const val BUTTON_FOCUSED = 120
        const val CONTROL_TILE = 38
        const val CONTROL_TILE_FOCUSED = 112
        const val CONTROL_PANEL = 232
        const val PALETTE_SELECTED = 115
        const val PALETTE_RESTING = 32
        const val PALETTE_FOCUSED = 145
        const val HOME_TEXT_SHADOW = 100
        const val HOME_DATE_TEXT = 235
        const val HOME_DATE_SHADOW = 160
        const val DOCK_TILE_RESTING = 25
        const val DOCK_TILE_FOCUSED = 112
        const val DOCK_SURFACE = 34
        const val CARD_SURFACE = 42
        const val SURFACE_STROKE = 86
        const val PANEL_STROKE = 72
        const val SELECTED_RESTING_FLOOR = 82
        const val CONTROL_CENTER_SCRIM = 45
        const val THEME_FOCUSED = 120
        const val THEME_SELECTED = 105
        const val SEARCH_FIELD = 45
        const val RISK_CARD = 36
        const val CONTROL_CENTER_TILE_RESTING = 72
        const val CONTROL_CENTER_TILE_FOCUSED = 125
    }

    object Radius {
        const val CONTROL = 18
        const val CARD = 20
        const val BUTTON = 28
        const val DOCK = 30
        const val DOCK_TILE = 19
        const val CONTROL_CENTER_PANEL = 34
        const val CONTROL_CENTER_TILE = 32
        const val SCREENSAVER_DETAILS = 24
    }

    object Elevation {
        const val DOCK_DP = 16
        const val PANEL_DP = 24
        const val FOCUSED_TILE_DP = 12
        const val CONTROL_CENTER_DP = 28
    }

    object Size {
        const val DOCK_ICON = 64
        const val DOCK_TILE_WIDTH = 112
        const val DOCK_TILE_HEIGHT = 122
        const val DOCK_LABEL_TEXT_SP = 14f
        const val DOCK_LABEL_WIDTH = 100
        const val DOCK_LABEL_HEIGHT = 28
        const val DOCK_FOCUS_SCALE = 1.07f
        const val CLOCK_LETTER_SPACING = -0.045f
        const val CLOCK_SHADOW_RADIUS = 22f
        const val CLOCK_SHADOW_DY = 4f
        const val DATE_SHADOW_RADIUS = 10f
        const val DATE_SHADOW_DY = 2f
        const val DOCK_TILE_PADDING_HORIZONTAL = 7
        const val DOCK_TILE_PADDING_TOP = 5
        const val DOCK_TILE_PADDING_BOTTOM = 4
        const val DOCK_LABEL_TOP_PADDING = 4
        const val EMPTY_DOCK_TEXT_SP = 14f
        const val EMPTY_DOCK_HEIGHT = 122
        const val HOME_DATE_LEFT_PADDING = 4
        const val CLOCK_TEXT_SP = 88f
        const val DATE_TEXT_SP = 18f
        const val PAGE_TITLE_SP = 29f
        const val CONTROL_CENTER_TITLE_SP = 21f
        const val CARD_TITLE_SP = 19f
        const val CARD_LABEL_SP = 11f
        const val CARD_BODY_SP = 13f
        const val SECTION_TITLE_SP = 15f
        const val CONTROL_TITLE_SP = 17f
        const val CONTROL_SUBTITLE_SP = 14f
        const val BUTTON_TEXT_SP = 16f
        const val FOCUS_SCALE = 1.035f
        const val TOP_ACTION_HEIGHT = 54
        const val TOP_SETTINGS_WIDTH = 58
        const val HOME_WALLPAPER_BUTTON_HEIGHT = 42
        const val PANEL_HEADER_HEIGHT = 56
        const val PANEL_CLOSE_HEIGHT = 48
        const val PANEL_WIDTH_FRACTION = 0.88f
        const val SETTINGS_NAV_ITEM_HEIGHT = 58
        const val SETTINGS_SIDEBAR_WIDTH_FRACTION = 0.255f
        const val SETTINGS_PANEL_WIDTH_FRACTION = 0.92f
        const val SETTINGS_PANEL_HEIGHT_FRACTION = 0.88f
        const val CONTROL_CENTER_WIDTH_FRACTION = 0.43f
        const val CONTROL_CENTER_HEIGHT_FRACTION = 0.91f
        const val CONTROL_CENTER_HEADER_TEXT_SP = 23f
        const val CONTROL_CENTER_ICON_TEXT_SP = 29f
        const val CONTROL_CENTER_TITLE_TEXT_SP = 18f
        const val CONTROL_CENTER_SUBTITLE_TEXT_SP = 14f
        const val CONTROL_CENTER_TILE_HEIGHT = 76
        const val SETTINGS_BRAND_TEXT_SP = 23f
        const val SETTINGS_CAPTION_TEXT_SP = 10f
        const val SETTINGS_HEADER_TEXT_SP = 28f
        const val SCREENSAVER_CLOCK_TEXT_SP = 104f
        const val SCREENSAVER_DATE_TEXT_SP = 23f
        const val SCREENSAVER_DETAILS_TEXT_SP = 19f
        const val SCREENSAVER_HINT_TEXT_SP = 14f
    }

    object Motion {
        const val FOCUS_DURATION_MS = 140L
        const val HOME_FADE_DURATION_MS = 220L
        const val SURFACE_FADE_DURATION_MS = 180L
        const val DOCK_REVEAL_DURATION_MS = 180L
        const val SECONDARY_REVEAL_DURATION_MS = 220L
        const val CONTROL_CENTER_REVEAL_DURATION_MS = 220L
        const val SCREENSAVER_REVEAL_DURATION_MS = 280L
    }

    fun surface(
        context: Context,
        fillColor: Int,
        radiusDp: Int,
        strokeAlpha: Int = GlassAlpha.SURFACE_STROKE
    ): GradientDrawable = GradientDrawable().apply {
        setColor(fillColor)
        cornerRadius = dp(context, radiusDp).toFloat()
        setStroke(dp(context, 1), Color.argb(strokeAlpha, 255, 255, 255))
    }

    fun gradientSurface(
        context: Context,
        colors: IntArray,
        radiusDp: Int,
        strokeColor: Int,
        strokeWidthDp: Int = 1
    ): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        colors
    ).apply {
        cornerRadius = dp(context, radiusDp).toFloat()
        if (strokeWidthDp > 0) setStroke(dp(context, strokeWidthDp), strokeColor)
    }

    fun dockSurface(context: Context): GradientDrawable = gradientSurface(
        context,
        intArrayOf(
            Color.argb(GlassAlpha.DOCK_SURFACE + 8, 255, 255, 255),
            Color.argb(GlassAlpha.DOCK_SURFACE - 6, 255, 255, 255)
        ),
        Radius.DOCK,
        Color.argb(GlassAlpha.SURFACE_STROKE, 255, 255, 255)
    )

    fun controlCenterSurface(context: Context): GradientDrawable =
        surface(context, ColorTokens.CONTROL_CENTER_SURFACE, Radius.BUTTON, GlassAlpha.PANEL_STROKE)

    fun panelSurface(context: Context): GradientDrawable =
        surface(context, ColorTokens.PANEL_SURFACE, Radius.DOCK, GlassAlpha.PANEL_STROKE)

    fun cardSurface(context: Context): GradientDrawable =
        surface(context, Color.argb(GlassAlpha.CARD_SURFACE, 255, 255, 255), Radius.CARD, GlassAlpha.SURFACE_STROKE)

    fun focusSurface(
        context: Context,
        focused: Boolean,
        radiusDp: Int,
        restingAlpha: Int = 38,
        focusedAlpha: Int = 120,
        selected: Boolean = false
    ): GradientDrawable = surface(
        context,
        Color.argb(
            if (focused) focusedAlpha else if (selected) maxOf(restingAlpha, GlassAlpha.SELECTED_RESTING_FLOOR) else restingAlpha,
            255, 255, 255
        ),
        radiusDp
    )

    fun applyFocusScale(view: View, focused: Boolean, scale: Float = Size.FOCUS_SCALE) {
        view.animate().scaleX(if (focused) scale else 1f)
            .scaleY(if (focused) scale else 1f)
            .setDuration(Motion.FOCUS_DURATION_MS)
            .start()
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
