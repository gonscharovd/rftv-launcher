package com.rftv.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** Compact macOS-style floating Dock label with a centered pointer. */
internal class DockTooltipDrawable(
    context: Context,
    private val textWidthPx: Float
) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val radius = 6.5f * density
    private val pointerWidth = 9.5f * density
    private val pointerHeight = 5f * density
    private val horizontalTextPadding = 24f * density
    private val minBubbleWidth = 44f * density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xEB1C1E24.toInt()
    }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = maxOf(1f, 0.8f * density)
        color = 0x2EFFFFFF
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val width = b.width().toFloat()
        val height = b.height().toFloat() - pointerHeight
        if (width <= 0f || height <= 0f) return
        val bubbleWidth = (textWidthPx + horizontalTextPadding)
            .coerceAtLeast(minBubbleWidth)
            .coerceAtMost(width - border.strokeWidth)
        val centerX = b.exactCenterX()
        val left = centerX - bubbleWidth / 2f
        val right = centerX + bubbleWidth / 2f
        val halfStroke = border.strokeWidth / 2f
        val path = Path().apply {
            addRoundRect(
                RectF(left + halfStroke, b.top + halfStroke, right - halfStroke, b.top + height),
                radius, radius, Path.Direction.CW
            )
            moveTo(centerX - pointerWidth / 2f, b.top + height - halfStroke)
            lineTo(centerX, b.top + height + pointerHeight)
            lineTo(centerX + pointerWidth / 2f, b.top + height - halfStroke)
            close()
        }
        canvas.drawPath(path, fill)
        canvas.drawPath(path, border)
    }

    override fun setAlpha(alpha: Int) {
        fill.alpha = alpha
        border.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        fill.colorFilter = colorFilter
        border.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
