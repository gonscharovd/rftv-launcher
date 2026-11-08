package com.rftv.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View

/**
 * Shared visual tokens for RFTV's lightweight Liquid Glass interface.
 * Uses translucent fills and strokes instead of real-time blur for Android 9 TV hardware.
 */
internal object RftvDesignSystem {
    object ColorTokens {
        const val TEXT_PRIMARY = Color.WHITE
        const val TEXT_SECONDARY = 0xFFE0E8F2.toInt()
        const val GLASS_STROKE = 0x56FFFFFF
        const val GLASS_FILL = 0x2DFFFFFF
        const val GLASS_FOCUS = 0x78FFFFFF
        const val PANEL_DARK = 0xE81B273A.toInt()
    }

    object Size {
        const val DOCK_ICON = 64
        const val DOCK_TILE_WIDTH = 112
        const val DOCK_TILE_HEIGHT = 122
        const val DOCK_LABEL_TEXT_SP = 14f
        const val CONTROL_TITLE_SP = 17f
        const val CONTROL_SUBTITLE_SP = 14f
        const val BUTTON_TEXT_SP = 16f
        const val FOCUS_SCALE = 1.035f
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
            .setDuration(140L)
            .start()
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
