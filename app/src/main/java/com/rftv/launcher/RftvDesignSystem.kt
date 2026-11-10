package com.rftv.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View

/**
 * Shared visual language for RFTV.
 *
 * Liquid Glass design system for Android TV. Blur is supplied by a backdrop snapshot
 * pipeline on legacy Android and native APIs where available; never blur every frame.
 */
internal object RftvDesignSystem {
    object ColorTokens {
        const val TEXT_PRIMARY = -1
        const val TEXT_SECONDARY = -2037517
        const val TEXT_MUTED = -4473925
        const val HOME_BACKDROP = -16777216
        const val WALLPAPER_FALLBACK = 0xFF0D1118.toInt()
        const val PANEL_SCRIM = 0xB8000000.toInt()
        const val PANEL_GRADIENT_START = 0xE61C2028.toInt()
        const val PANEL_GRADIENT_END = 0xE60D1118.toInt()
        const val PANEL_SURFACE = 0xD91A1D25.toInt()
        const val CONTROL_CENTER_SURFACE = 0xE61B1F28.toInt()
        const val RISK_CRITICAL = -41130
        const val RISK_REVIEW = -16826
        const val GLASS_STROKE = 0x30FFFFFF
        const val GLASS_FILL = 0x20FFFFFF
        const val GLASS_FOCUS = 0xE6FFFFFF
        const val CLOCK_SHADOW = -1358954496
        const val OVERLAY_TOP = 0x16000000
        const val OVERLAY_BOTTOM = 0x82000000.toInt()
        const val LABEL_ON_GLASS = 0xB8FFFFFF.toInt()
        const val CONTROL_CENTER_SCRIM = 0xB0000000.toInt()
        const val CONTROL_CENTER_GRADIENT_START = 0xE91C2028.toInt()
        const val CONTROL_CENTER_GRADIENT_MID = 0xE31A1E26.toInt()
        const val CONTROL_CENTER_GRADIENT_END = 0xEA0D1118.toInt()
        const val CONTROL_CENTER_STROKE = 0x38FFFFFF
        const val CONTROL_CENTER_TILE_START = 0x24FFFFFF
        const val CONTROL_CENTER_TILE_END = 0x24FFFFFF
        const val CONTROL_CENTER_TILE_FOCUSED_START = 0xF7FFFFFF.toInt()
        const val CONTROL_CENTER_TILE_FOCUSED_END = 0xF7FFFFFF.toInt()
        const val CONTROL_CENTER_ICON = 0xFFFFFFFF.toInt()
        const val CONTROL_CENTER_SUBTITLE = 0xB8FFFFFF.toInt()
        const val CONTROL_CENTER_FOCUSED_TEXT = 0xFF17191F.toInt()
        const val SCREENSAVER_GRADIENT_START = 0xFF151A22.toInt()
        const val SCREENSAVER_GRADIENT_MID = 0xFF0F141C.toInt()
        const val SCREENSAVER_GRADIENT_END = 0xFF080B10.toInt()
        const val SCREENSAVER_TEXT_SECONDARY = -587202561
        const val SCREENSAVER_TEXT_MUTED = -1342177281
        const val SCREENSAVER_GLASS = 0x26FFFFFF
        const val SETTINGS_SHELL_TOP = 0xEE20232A.toInt()
        const val SETTINGS_SHELL_BOTTOM = 0xEE111318.toInt()
        const val SETTINGS_SIDEBAR_TOP = 0x553C414B
        const val SETTINGS_SIDEBAR_BOTTOM = 0x33272B34
        const val SETTINGS_NAV_SELECTED_TOP = 0x40FFFFFF
        const val SETTINGS_NAV_SELECTED_BOTTOM = 0x28FFFFFF
        const val SETTINGS_NAV_FOCUSED_STROKE = 0xF2FFFFFF.toInt()
        const val INTERACTIVE_FOCUSED_TEXT = 0xFF17191F.toInt()
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
        const val DOCK_TILE_GAP = 10
        const val DOCK_EDGE_INSET = 13
        const val DOCK_CONTENT_HORIZONTAL_PADDING = 13
        const val DOCK_BOTTOM_INSET = 14
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
        const val SETTINGS_CONTENT_LEFT_PADDING = 18
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
        const val DOCK_TILE_RESTING = 12
        const val DOCK_TILE_FOCUSED = 118
        const val DOCK_TILE_STROKE_RESTING = 24
        const val DOCK_TILE_STROKE_FOCUSED = 190
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
        const val DOCK_PILL = 76
        const val DOCK_TILE = 13
        const val CONTROL_CENTER_PANEL = 26
        const val CONTROL_CENTER_TILE = 28
        const val SCREENSAVER_DETAILS = 24
        const val SETTINGS_SHELL = 26
        const val SETTINGS_SIDEBAR = 18
        const val SETTINGS_NAV = 12
    }

    object Elevation {
        const val DOCK_DP = 16
        const val PANEL_DP = 24
        const val FOCUSED_TILE_DP = 12
        const val CONTROL_CENTER_DP = 28
    }

    object Size {
        const val DOCK_ICON = 26
        const val DOCK_TILE_SIZE = 54
        const val DOCK_TILE_WIDTH = 112
        const val DOCK_TILE_HEIGHT = 122
        const val DOCK_LABEL_TEXT_SP = 11.5f
        const val DOCK_LABEL_WIDTH = 100
        const val DOCK_LABEL_HEIGHT = 28
        const val DOCK_FOCUS_SCALE = 1.13f
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
        const val CLOCK_TEXT_SP = 122f
        const val DATE_TEXT_SP = 20f
        const val HOME_WEATHER_TEXT_SP = 18f
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
        const val SETTINGS_NAV_ITEM_HEIGHT = 35
        const val SETTINGS_SIDEBAR_WIDTH_FRACTION = 0.255f
        const val SETTINGS_PANEL_WIDTH_FRACTION = 0.92f
        const val SETTINGS_PANEL_HEIGHT_FRACTION = 0.88f
        const val CONTROL_CENTER_WIDTH_FRACTION = 0.40f
        const val CONTROL_CENTER_PANEL_WIDTH = 293
        const val CONTROL_CENTER_HEIGHT_FRACTION = 0.91f
        const val CONTROL_CENTER_HEADER_TEXT_SP = 23f
        const val CONTROL_CENTER_ICON_TEXT_SP = 29f
        const val CONTROL_CENTER_TITLE_TEXT_SP = 18f
        const val CONTROL_CENTER_SUBTITLE_TEXT_SP = 14f
        const val CONTROL_CENTER_TILE_HEIGHT = 76
        const val SETTINGS_BRAND_TEXT_SP = 23f
        const val SETTINGS_CAPTION_TEXT_SP = 10f
        const val SETTINGS_HEADER_TEXT_SP = 28f
        const val SCREENSAVER_CLOCK_TEXT_SP = 64f
        const val SCREENSAVER_DATE_TEXT_SP = 23f
        const val SCREENSAVER_DETAILS_TEXT_SP = 19f
        const val SCREENSAVER_HINT_TEXT_SP = 14f
    }

    object Motion {
        const val FOCUS_DURATION_MS = 140L
        const val HOME_FADE_DURATION_MS = 220L
        const val SURFACE_FADE_DURATION_MS = 180L
        const val DOCK_REVEAL_DURATION_MS = 180L
        const val DOCK_FOCUS_DURATION_MS = 190L
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

    fun interactiveSurface(context: Context, focused: Boolean, radiusDp: Int): GradientDrawable = gradientSurface(
        context,
        if (focused) intArrayOf(0xF2FFFFFF.toInt(), 0xDFFFFFFF.toInt())
        else intArrayOf(0x32FFFFFF, 0x18FFFFFF),
        radiusDp,
        if (focused) ColorTokens.SETTINGS_NAV_FOCUSED_STROKE else 0x38FFFFFF
    )

    fun dockSurface(context: Context): GradientDrawable = gradientSurface(
        context,
        intArrayOf(0x541E141C, 0x541E141C),
        23,
        0x38FFFFFF
    )

    fun dockTileSurface(context: Context, focused: Boolean): GradientDrawable =
        GradientDrawable().apply {
            setColor(if (focused) 0xDDF8FAFF.toInt() else Color.TRANSPARENT)
            cornerRadius = dp(context, Radius.DOCK_TILE).toFloat()
            if (focused) setStroke(dp(context, 2), 0xF2FFFFFF.toInt())
        }

    fun controlCenterSurface(context: Context): GradientDrawable =
        surface(context, ColorTokens.CONTROL_CENTER_SURFACE, Radius.BUTTON, GlassAlpha.PANEL_STROKE)

    fun settingsShellSurface(context: Context): GradientDrawable = gradientSurface(
        context,
        intArrayOf(0xEA222634.toInt(), 0xDE161924.toInt()),
        Radius.SETTINGS_SHELL,
        ColorTokens.CONTROL_CENTER_STROKE
    )

    fun settingsSidebarSurface(context: Context): GradientDrawable = gradientSurface(
        context,
        intArrayOf(0x9412141E.toInt(), 0x800E1018.toInt()),
        Radius.SETTINGS_SIDEBAR,
        Color.argb(GlassAlpha.SURFACE_STROKE, 255, 255, 255)
    )

    fun settingsNavigationSurface(context: Context, focused: Boolean, selected: Boolean): GradientDrawable {
        val colors = when {
            focused -> intArrayOf(0xF7FFFFFF.toInt(), 0xF7FFFFFF.toInt())
            selected -> intArrayOf(0x28FFFFFF, 0x1CFFFFFF)
            else -> intArrayOf(0x00FFFFFF, 0x00FFFFFF)
        }
        return gradientSurface(
            context,
            colors,
            Radius.SETTINGS_NAV,
            if (focused) ColorTokens.SETTINGS_NAV_FOCUSED_STROKE else 0x38FFFFFF
        )
    }

    fun settingsHintSurface(context: Context): GradientDrawable = gradientSurface(
        context,
        intArrayOf(0xD916161F.toInt(), 0xD916161F.toInt()),
        12,
        0x26FFFFFF
    )

    fun settingsNavIconSurface(context: Context, focused: Boolean): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(context, 7).toFloat()
        setColor(if (focused) 0x14000000 else 0x1AFFFFFF)
    }

    fun panelSurface(context: Context): GradientDrawable =
        surface(context, ColorTokens.PANEL_SURFACE, Radius.DOCK, GlassAlpha.PANEL_STROKE)

    fun cardSurface(context: Context): GradientDrawable =
        gradientSurface(context, intArrayOf(0x36FFFFFF, 0x1EFFFFFF), Radius.CARD, 0x30FFFFFF)

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
