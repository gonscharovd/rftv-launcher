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
        const val PANEL_SCRIM = 0xCD050911.toInt()
        const val PANEL_GRADIENT_START = 0xFF2B394E.toInt()
        const val PANEL_GRADIENT_END = 0xFF111928.toInt()
        const val PANEL_SURFACE = 0xF01A2639.toInt()
        const val GLASS_STROKE = 0x56FFFFFF
        const val GLASS_FILL = 0x2DFFFFFF
        const val GLASS_FOCUS = 0x78FFFFFF
        const val CLOCK_SHADOW = 0xAF000000.toInt()
        const val OVERLAY_TOP = 0x0C000000
        const val OVERLAY_BOTTOM = 0x76000000
        const val LABEL_ON_GLASS = 0xBEDCE8FF.toInt()
    }

    object Spacing {
        const val XXS = 4
        const val XS = 8
        const val SM = 12
        const val MD = 16
        const val LG = 22
        const val XL = 32
        const val XXL = 54
    }

    object Radius {
        const val CONTROL = 18
        const val CARD = 20
        const val BUTTON = 28
        const val DOCK = 30
    }

    object Elevation {
        const val DOCK_DP = 16
        const val PANEL_DP = 24
        const val FOCUSED_TILE_DP = 12
    }

    object Size {
        const val DOCK_ICON = 64
        const val DOCK_TILE_WIDTH = 112
        const val DOCK_TILE_HEIGHT = 122
        const val DOCK_LABEL_TEXT_SP = 14f
        const val CLOCK_TEXT_SP = 88f
        const val DATE_TEXT_SP = 18f
        const val PAGE_TITLE_SP = 29f
        const val CARD_TITLE_SP = 19f
        const val CARD_LABEL_SP = 11f
        const val CARD_BODY_SP = 13f
        const val SECTION_TITLE_SP = 15f
        const val CONTROL_TITLE_SP = 17f
        const val CONTROL_SUBTITLE_SP = 14f
        const val BUTTON_TEXT_SP = 16f
        const val FOCUS_SCALE = 1.035f
    }

    object Motion {
        const val FOCUS_DURATION_MS = 140L
        const val HOME_FADE_DURATION_MS = 220L
    }

    fun surface(
        context: Context,
        fillColor: Int,
        radiusDp: Int,
        strokeAlpha: Int = 86
    ): GradientDrawable = GradientDrawable().apply {
        setColor(fillColor)
        cornerRadius = dp(context, radiusDp).toFloat()
        setStroke(dp(context, 1), Color.argb(strokeAlpha, 255, 255, 255))
    }

    fun dockSurface(context: Context): GradientDrawable =
        surface(context, Color.argb(34, 255, 255, 255), Radius.DOCK)

    fun panelSurface(context: Context): GradientDrawable =
        surface(context, ColorTokens.PANEL_SURFACE, Radius.DOCK, 72)

    fun cardSurface(context: Context): GradientDrawable =
        surface(context, Color.argb(42, 255, 255, 255), Radius.CARD)

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
            if (focused) focusedAlpha else if (selected) maxOf(restingAlpha, 82) else restingAlpha,
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
