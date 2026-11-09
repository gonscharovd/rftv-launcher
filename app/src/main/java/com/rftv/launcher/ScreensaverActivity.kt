package com.rftv.launcher

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

/** Full-screen dashboard screensaver. Any remote key dismisses it. */
class ScreensaverActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newFixedThreadPool(4)
    private val refreshing = AtomicBoolean(false)
    private var dashboardFuture: Future<*>? = null
    private var wallpaperFuture: Future<*>? = null
    @Volatile private var pendingWallpaperBitmap: Bitmap? = null
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var rates: TextView
    private lateinit var weather: TextView
    private lateinit var russiaList: LinearLayout
    private lateinit var worldList: LinearLayout
    private lateinit var wallpaper: ImageView
    private var alive = false
    private var sessionAcquired = false

    private val tick = object : Runnable {
        override fun run() {
            if (!alive) return
            val now = Date()
            clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
            date.text = SimpleDateFormat("EEEE, d MMMM", Locale("ru")).format(now)
                .replaceFirstChar { it.uppercase() }
            handler.postDelayed(this, 15_000L)
        }
    }

    private val refreshDashboard = object : Runnable {
        override fun run() {
            if (!alive) return
            loadDashboard(force = true)
            handler.postDelayed(this, 15 * 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionAcquired = ScreensaverSession.tryAcquire()
        if (!sessionAcquired) {
            finish()
            return
        }
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
        buildScreen()
        loadCurrentWallpaper()
        renderDashboard(readCachedDashboard())
    }

    override fun onResume() {
        super.onResume()
        alive = true
        tick.run()
        if (!::wallpaper.isInitialized || (wallpaper.drawable == null && wallpaperFuture?.isDone != false && pendingWallpaperBitmap == null)) loadCurrentWallpaper()
        loadDashboard()
        handler.removeCallbacks(refreshDashboard)
        handler.postDelayed(refreshDashboard, 15 * 60_000L)
    }

    override fun onPause() {
        alive = false
        dashboardFuture?.cancel(true)
        dashboardFuture = null
        wallpaperFuture?.cancel(true)
        wallpaperFuture = null
        pendingWallpaperBitmap?.takeIf { !it.isRecycled }?.recycle()
        pendingWallpaperBitmap = null
        refreshing.set(false)
        handler.removeCallbacksAndMessages(null)
        super.onPause()
    }

    override fun onDestroy() {
        alive = false
        handler.removeCallbacksAndMessages(null)
        dashboardFuture?.cancel(true)
        wallpaperFuture?.cancel(true)
        pendingWallpaperBitmap?.takeIf { !it.isRecycled }?.recycle()
        pendingWallpaperBitmap = null
        io.shutdownNow()
        if (sessionAcquired) ScreensaverSession.release()
        sessionAcquired = false
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            finish()
            return true
        }
        return true
    }

    private fun buildScreen() {
        val screenHeightDp = resources.configuration.screenHeightDp
        val topCardHeightDp = if (screenHeightDp < 760) 118 else 142
        val newsCardHeightDp = (screenHeightDp - 116 - topCardHeightDp).coerceIn(220, 620)
        val root = FrameLayout(this).apply {
            setBackground(
                GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(
                        RftvDesignSystem.ColorTokens.SCREENSAVER_GRADIENT_START,
                        RftvDesignSystem.ColorTokens.SCREENSAVER_GRADIENT_MID,
                        RftvDesignSystem.ColorTokens.SCREENSAVER_GRADIENT_END
                    )
                )
            )
            isFocusableInTouchMode = true
        }
        wallpaper = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Размытые активные обои главного экрана"
        }
        root.addView(wallpaper, FrameLayout.LayoutParams(-1, -1))
        root.addView(View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(105, 3, 8, 18), Color.argb(188, 3, 8, 18))
            )
        }, FrameLayout.LayoutParams(-1, -1))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(dp(48), dp(34), dp(48), dp(30))
        }
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val clockCard = glassCard().apply { orientation = LinearLayout.VERTICAL }
        clock = TextView(this).apply {
            text = "00:00"
            textSize = if (screenHeightDp < 760) 52f else RftvDesignSystem.Size.SCREENSAVER_CLOCK_TEXT_SP
            letterSpacing = -0.045f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.WHITE)
            setShadowLayer(12f, 0f, 2f, Color.argb(150, 0, 0, 0))
        }
        date = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.argb(220, 255, 255, 255))
            setPadding(0, dp(2), 0, 0)
        }
        clockCard.addView(clock, LinearLayout.LayoutParams(-1, -2))
        clockCard.addView(date, LinearLayout.LayoutParams(-1, -2))
        topRow.addView(clockCard, LinearLayout.LayoutParams(0, dp(172), 1.1f).apply {
            rightMargin = dp(12)
        })

        val ratesCard = glassCard().apply { orientation = LinearLayout.VERTICAL }
        ratesCard.addView(cardCaption("КУРСЫ ЦБ РФ"))
        rates = cardValue("USD  — ₽\nEUR  — ₽")
        ratesCard.addView(rates, LinearLayout.LayoutParams(-1, 0, 1f))
        topRow.addView(ratesCard, LinearLayout.LayoutParams(0, dp(topCardHeightDp), 1f).apply {
            rightMargin = dp(12)
        })

        val weatherCard = glassCard().apply { orientation = LinearLayout.VERTICAL }
        weatherCard.addView(cardCaption("ПОГОДА · ЧЕЛЯБИНСК"))
        weather = cardValue("Загрузка текущих условий…")
        weatherCard.addView(weather, LinearLayout.LayoutParams(-1, 0, 1f))
        topRow.addView(weatherCard, LinearLayout.LayoutParams(0, dp(topCardHeightDp), 1f))
        content.addView(topRow, LinearLayout.LayoutParams(-1, dp(topCardHeightDp)))

        val newsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        val russiaCard = glassCard().apply { orientation = LinearLayout.VERTICAL }
        russiaCard.addView(sectionHeading("РОССИЯ"))
        val russiaScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
        }
        russiaList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        russiaScroll.addView(russiaList)
        russiaCard.addView(russiaScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        newsRow.addView(russiaCard, LinearLayout.LayoutParams(0, 0, 1f).apply {
            height = dp(480)
            rightMargin = dp(12)
            topMargin = dp(18)
        })

        val worldCard = glassCard().apply { orientation = LinearLayout.VERTICAL }
        worldCard.addView(sectionHeading("МИР"))
        val worldScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
        }
        worldList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        worldScroll.addView(worldList)
        worldCard.addView(worldScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        newsRow.addView(worldCard, LinearLayout.LayoutParams(0, 0, 1f).apply {
            height = dp(newsCardHeightDp)
            leftMargin = dp(12)
            topMargin = dp(18)
        })
        content.addView(newsRow, LinearLayout.LayoutParams(-1, dp(498)))
        content.addView(TextView(this).apply {
            text = "RFTV  ·  Нажмите любую кнопку, чтобы продолжить"
            textSize = 13f
            setTextColor(Color.argb(175, 255, 255, 255))
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }, LinearLayout.LayoutParams(-1, dp(34)))
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    private fun glassCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = RftvDesignSystem.surface(
            this@ScreensaverActivity,
            Color.argb(72, 226, 239, 255),
            28
        )
        setPadding(dp(22), dp(16), dp(22), dp(16))
        elevation = dp(12).toFloat()
    }

    private fun cardCaption(value: String) = TextView(this).apply {
        text = value
        textSize = 11f
        letterSpacing = 0.15f
        setTextColor(Color.argb(190, 255, 255, 255))
    }

    private fun cardValue(value: String) = TextView(this).apply {
        text = value
        textSize = 22f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(8), 0, 0)
    }

    private fun sectionHeading(value: String) = TextView(this).apply {
        text = value
        textSize = 20f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.08f
        setTextColor(Color.WHITE)
        setPadding(dp(4), dp(2), dp(4), dp(12))
    }

    private fun addNewsItems(target: LinearLayout, items: List<String>, unavailable: String) {
        target.removeAllViews()
        val visible = items.filter { it.isNotBlank() }.take(5)
        if (visible.isEmpty()) {
            target.addView(newsItem(unavailable))
            return
        }
        visible.forEach { target.addView(newsItem(it)) }
    }

    private fun newsItem(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 16f
        setTextColor(Color.WHITE)
        maxLines = 4
        setLineSpacing(dp(3).toFloat(), 1f)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = RftvDesignSystem.surface(
            this@ScreensaverActivity,
            Color.argb(48, 240, 247, 255),
            18
        )
        val params = LinearLayout.LayoutParams(-1, -2)
        params.bottomMargin = dp(10)
        layoutParams = params
    }

    private fun loadCurrentWallpaper() {
        val prefs = getSharedPreferences("rftv_preferences", MODE_PRIVATE)
        val themeMode = prefs.getInt("theme_mode", 2)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val dark = when (themeMode) {
            1 -> true
            0 -> false
            else -> hour >= 19 || hour < 7
        }
        val index = prefs.getInt(if (dark) "wallpaper_dark" else "wallpaper_light", 0).coerceAtLeast(0)
        val directory = File(filesDir, "wallpapers")
        val full = File(directory, WallpaperImageUrls.cacheFileName(dark, index, false))
        val preview = File(directory, WallpaperImageUrls.cacheFileName(dark, index, true))
        val selected = when {
            full.isFile && full.length() > 10_000L -> full
            preview.isFile && preview.length() > 5_000L -> preview
            else -> null
        } ?: return

        wallpaperFuture = io.submit {
            val source = try {
                BitmapFactory.decodeFile(selected.absolutePath, BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inSampleSize = 2
                })
            } catch (_: Exception) { null }
            val blurred = source?.let {
                try {
                    GlassBackdrop.createBlurredSnapshot(it, 480, 270, radius = 10, passes = 3)
                } catch (_: Exception) { null }
            }
            source?.recycle()
            if (Thread.currentThread().isInterrupted) {
                blurred?.takeIf { !it.isRecycled }?.recycle()
                return@submit
            }
            pendingWallpaperBitmap = blurred
            handler.post {
                if (pendingWallpaperBitmap !== blurred) {
                    blurred?.takeIf { !it.isRecycled }?.recycle()
                    return@post
                }
                pendingWallpaperBitmap = null
                if (alive && !isFinishing && blurred != null) wallpaper.setImageBitmap(blurred)
                else blurred?.takeIf { !it.isRecycled }?.recycle()
            }
        }
    }

    private fun readCachedDashboard(): ScreensaverDashboardRepository.Snapshot =
        ScreensaverDashboardRepository.readCached(this)

    private fun renderDashboard(data: ScreensaverDashboardRepository.Snapshot) {
        if (!::weather.isInitialized) return
        weather.text = data.weather.ifBlank { "Погодные данные пока недоступны" }
        rates.text = data.rates.ifBlank { "Курсы ЦБ РФ пока недоступны" }
        addNewsItems(russiaList, data.russia, "Российская лента пока недоступна")
        addNewsItems(worldList, data.world, "Мировая лента пока недоступна")
    }

    private fun loadDashboard(force: Boolean = false) {
        if (!refreshing.compareAndSet(false, true)) return
        if (!force && ScreensaverDashboardRepository.isFresh(this)) {
            refreshing.set(false)
            return
        }
        dashboardFuture = io.submit {
            try {
                val next = ScreensaverDashboardRepository.load(this, force)
                handler.post {
                    if (alive && !isFinishing) renderDashboard(next)
                }
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (error: Exception) {
                android.util.Log.w("RFTVScreensaver", "Dashboard refresh failed", error)
            } finally {
                refreshing.set(false)
            }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        fun launch(activity: Activity) {
            activity.startActivity(android.content.Intent(activity, ScreensaverActivity::class.java))
        }
    }
}
