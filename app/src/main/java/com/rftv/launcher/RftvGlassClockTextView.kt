package com.rftv.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
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
    private val textureMatrix = Matrix()
    private var glassShader: Shader? = null
    private var darkGlassShader: Shader? = null
    private var glassTextureBitmap: Bitmap? = null
    private var glassTextureShader: BitmapShader? = null
    private var darkText = false

    fun setGlassTexture(bitmap: Bitmap?) {
        if (glassTextureBitmap === bitmap) return
        glassTextureBitmap = bitmap
        glassTextureShader = bitmap?.let {
            BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        updateTextureMatrix()
        invalidate()
    }

    fun setDarkText(enabled: Boolean) {
        if (darkText == enabled) return
        darkText = enabled
        invalidate()
    }

    private fun updateTextureMatrix() {
        val bitmap = glassTextureBitmap ?: return
        val shader = glassTextureShader ?: return
        if (width <= 0 || height <= 0 || bitmap.width <= 0 || bitmap.height <= 0) return
        val scale = maxOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val dx = (width - bitmap.width * scale) / 2f
        val dy = (height - bitmap.height * scale) / 2f
        textureMatrix.setScale(scale, scale)
        textureMatrix.postTranslate(dx, dy)
        shader.setLocalMatrix(textureMatrix)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        glassShader = if (height > 0) {
            LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(
                    RftvDesignSystem.ColorTokens.CLOCK_GLASS_TOP,
                    RftvDesignSystem.ColorTokens.CLOCK_GLASS_MIDDLE,
                    RftvDesignSystem.ColorTokens.CLOCK_GLASS_BOTTOM
                ),
                null,
                Shader.TileMode.CLAMP
            )
        } else null
        darkGlassShader = if (height > 0) {
            LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(
                    RftvDesignSystem.ColorTokens.CLOCK_GLASS_DARK_TOP,
                    RftvDesignSystem.ColorTokens.CLOCK_GLASS_DARK_MIDDLE,
                    RftvDesignSystem.ColorTokens.CLOCK_GLASS_DARK_BOTTOM
                ),
                null,
                Shader.TileMode.CLAMP
            )
        } else null
        updateTextureMatrix()
    }

    override fun onDraw(canvas: Canvas) {
        val shader = if (darkText) darkGlassShader else glassTextureShader ?: glassShader
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
        textPaint.color = if (darkText) 0x55000000 else RftvDesignSystem.ColorTokens.CLOCK_GLASS_RIM
        super.onDraw(canvas)

        // Scene glass is mapped into the glyphs; fallback scenes use the cached gradient.
        textPaint.style = Paint.Style.FILL
        textPaint.strokeWidth = originalStrokeWidth
        textPaint.color = if (darkText) 0xEE202936.toInt() else originalColor
        textPaint.shader = shader
        textPaint.setShadowLayer(
            RftvDesignSystem.Size.CLOCK_SHADOW_RADIUS * density,
            0f,
            RftvDesignSystem.Size.CLOCK_SHADOW_DY * density,
            if (darkText) 0x33000000 else RftvDesignSystem.ColorTokens.CLOCK_SHADOW
        )
        super.onDraw(canvas)

        textPaint.clearShadowLayer()
        textPaint.shader = originalShader
        textPaint.style = originalStyle
        textPaint.strokeWidth = originalStrokeWidth
        textPaint.color = originalColor
    }
}
