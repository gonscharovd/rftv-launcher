package com.rftv.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.min

/** Small dependency-free vector glyphs for the TV Control Center. */
internal class RftvControlIconView(context: Context, private val kind: String) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = RftvDesignSystem.ColorTokens.CONTROL_CENTER_ICON
        style = Paint.Style.STROKE
        strokeWidth = 3.2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        val save = canvas.save()
        canvas.translate((width - size) / 2f, (height - size) / 2f)
        canvas.scale(size / 64f, size / 64f)
        when (kind) {
            "wifi" -> drawWifi(canvas)
            "bluetooth" -> drawBluetooth(canvas)
            "theme" -> drawTheme(canvas)
            "memory" -> drawMemory(canvas)
            "clock" -> drawClock(canvas)
            "settings" -> drawSettings(canvas)
            "tv" -> drawTv(canvas)
            "weather" -> drawWeather(canvas)
        }
        canvas.restoreToCount(save)
    }

    private fun drawWifi(c: Canvas) {
        c.drawArc(RectF(9f, 12f, 55f, 58f), 225f, 90f, false, paint)
        c.drawArc(RectF(18f, 22f, 46f, 50f), 225f, 90f, false, paint)
        c.drawArc(RectF(27f, 32f, 37f, 42f), 225f, 90f, false, paint)
        c.drawCircle(32f, 52f, 2.8f, paint)
    }

    private fun drawBluetooth(c: Canvas) {
        val p = Path().apply {
            moveTo(29f, 8f); lineTo(46f, 22f); lineTo(20f, 44f)
            moveTo(29f, 8f); lineTo(29f, 56f); lineTo(46f, 42f); lineTo(20f, 20f)
        }
        c.drawPath(p, paint)
    }

    private fun drawTheme(c: Canvas) {
        c.drawCircle(32f, 32f, 22f, paint)
        val half = Path().apply {
            moveTo(32f, 10f)
            arcTo(RectF(10f, 10f, 54f, 54f), -90f, 180f)
            close()
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = RftvDesignSystem.ColorTokens.CONTROL_CENTER_ICON
            style = Paint.Style.FILL
        }
        c.drawPath(half, fill)
    }

    private fun drawMemory(c: Canvas) {
        c.drawRoundRect(RectF(13f, 19f, 51f, 45f), 5f, 5f, paint)
        for (x in 19..45 step 9) {
            c.drawLine(x.toFloat(), 13f, x.toFloat(), 19f, paint)
            c.drawLine(x.toFloat(), 45f, x.toFloat(), 51f, paint)
        }
        c.drawLine(20f, 27f, 44f, 27f, paint)
        c.drawLine(20f, 36f, 44f, 36f, paint)
    }

    private fun drawClock(c: Canvas) {
        c.drawCircle(32f, 32f, 23f, paint)
        c.drawLine(32f, 18f, 32f, 32f, paint)
        c.drawLine(32f, 32f, 43f, 38f, paint)
    }

    private fun drawSettings(c: Canvas) {
        c.drawCircle(32f, 32f, 10f, paint)
        for (angle in 0..7) {
            val radians = Math.toRadians(angle * 45.0)
            val x1 = (32 + 15 * kotlin.math.cos(radians)).toFloat()
            val y1 = (32 + 15 * kotlin.math.sin(radians)).toFloat()
            val x2 = (32 + 23 * kotlin.math.cos(radians)).toFloat()
            val y2 = (32 + 23 * kotlin.math.sin(radians)).toFloat()
            c.drawLine(x1, y1, x2, y2, paint)
        }
    }

    private fun drawWeather(c: Canvas) {
        paint.style = Paint.Style.STROKE
        c.drawCircle(20f, 22f, 9f, paint)
        c.drawLine(20f, 5f, 20f, 9f, paint)
        c.drawLine(20f, 35f, 20f, 39f, paint)
        c.drawLine(3f, 22f, 7f, 22f, paint)
        c.drawLine(33f, 22f, 37f, 22f, paint)
        c.drawLine(8f, 10f, 11f, 13f, paint)
        c.drawLine(29f, 31f, 32f, 34f, paint)
        c.drawRoundRect(RectF(17f, 28f, 57f, 53f), 12f, 12f, paint)
        c.drawArc(RectF(25f, 20f, 52f, 49f), 180f, 180f, false, paint)
    }

    private fun drawTv(c: Canvas) {
        c.drawRoundRect(RectF(9f, 13f, 55f, 45f), 5f, 5f, paint)
        c.drawLine(24f, 53f, 40f, 53f, paint)
        c.drawLine(32f, 45f, 32f, 53f, paint)
        c.drawLine(23f, 8f, 32f, 14f, paint)
        c.drawLine(41f, 8f, 32f, 14f, paint)
    }
}
