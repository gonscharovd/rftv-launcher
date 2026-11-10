package com.rftv.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import android.widget.TextView

/**
 * Lightweight tvOS-inspired glass clock typography.
 *
 * Uses a cached vertical text shader and a thin specular rim. It does not capture
 * the screen, allocate bitmaps or run blur work; the shader is rebuilt only on resize.
 */
internal class RftvGlassClockTextView(context: Context) : TextView(context) {
    private val density = resources.displayMetrics.density
    private var glassShader: Shader? = null

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        glassShader = if (height > 0) {
            LinearGradient(
                0f,
                0f,
                0f,
                height.toFloat(),
                intArrayOf(
                    RftvDesignSystem.ColorTokens.CLOCK_GLASS_TOP,
                    RftvDesignSystem.ColorTokens.CLOCK_GLASS_MIDDLE,
                    RftvDesignSystem.ColorTokens.CLOCK_GLASS_BOTTOM
                ),
                null,
                Shader.TileMode.CLAMP
            )
        } else {
            null
        }
    }

    override fun onDraw(canvas: Canvas) {
        val shader = glassShader
        if (shader == null || text.isNullOrEmpty()) {
            super.onDraw(canvas)
            return
        }

        val textPaint = paint
        val originalColor = textPaint.color
        val originalShader = textPaint.shader
        val originalStyle = textPaint.style
        val originalStrokeWidth = textPaint.strokeWidth

        // Fine bright rim pass creates a glass edge without a costly bitmap effect.
        textPaint.clearShadowLayer()
        textPaint.shader = null
        textPaint.style = Paint.Style.STROKE
        textPaint.strokeWidth = (0.75f * density).coerceAtLeast(1f)
        textPaint.color = RftvDesignSystem.ColorTokens.CLOCK_GLASS_RIM
        super.onDraw(canvas)

        // Soft vertical reflection and a restrained shadow keep the digits legible on photos.
        textPaint.style = Paint.Style.FILL
        textPaint.strokeWidth = originalStrokeWidth
        textPaint.color = originalColor
        textPaint.shader = shader
        textPaint.setShadowLayer(
            RftvDesignSystem.Size.CLOCK_SHADOW_RADIUS * density,
            0f,
            RftvDesignSystem.Size.CLOCK_SHADOW_DY * density,
            RftvDesignSystem.ColorTokens.CLOCK_SHADOW
        )
        super.onDraw(canvas)

        textPaint.clearShadowLayer()
        textPaint.shader = originalShader
        textPaint.style = originalStyle
        textPaint.strokeWidth = originalStrokeWidth
        textPaint.color = originalColor
    }
}
