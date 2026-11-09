package com.rftv.launcher

import android.service.dreams.DreamService
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import android.util.Xml
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * RFTV ambient screensaver.
 *
 * Android/StarOS must expose Android DreamService in its system settings.
 * The user selects RFTV once in Settings > Display > Screen saver.
 */
class RftvDreamService : DreamService() {
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var root: FrameLayout? = null
    private var wallpaper: ImageView? = null
    private var clock: TextView? = null
    private var date: TextView? = null
    private var ratesText: TextView? = null
    private var weatherText: TextView? = null
    private var russiaText: TextView? = null
    private var worldText: TextView? = null
    private var lastHourKey = -1L
    @Volatile private var dreaming = false

    private data class DashboardData(
        val weather: String,
        val rates: String,
        val russia: String,
        val world: String
    )

    private val refreshTask = object : Runnable {
        override fun run() {
            val hourKey = System.currentTimeMillis() / 3_600_000L
            updateClock()
            if (hourKey != lastHourKey) {
                lastHourKey = hourKey
                refreshOnlineContent(hourKey)
            }
            handler.postDelayed(this, 60_000L)
        }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        dreaming = true
        // A DreamService can be started more than once in the same hour.
        // Force a refresh so a newly created view is never left blank.
        lastHourKey = -1L
        isFullscreen = true
        isInteractive = false
        window?.decorView?.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        buildScreen()
        updateClock()
        refreshTask.run()
    }

    override fun onDreamingStopped() {
        dreaming = false
        handler.removeCallbacksAndMessages(null)
        super.onDreamingStopped()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun buildScreen() {
        val frame = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(12, 20, 32))
        }
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(12, 20, 32))
            contentDescription = "Размытые активные обои главного экрана"
        }
        frame.addView(image, FrameLayout.LayoutParams(-1, -1))
        frame.addView(View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(95, 3, 8, 18), Color.argb(190, 3, 8, 18))
            )
        }, FrameLayout.LayoutParams(-1, -1))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(34), dp(48), dp(30))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val clockCard = glassCard()
        val time = TextView(this).apply {
            text = "00:00"
            textSize = 62f
            letterSpacing = -0.045f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setShadowLayer(12f, 0f, 2f, Color.argb(150, 0, 0, 0))
        }
        val day = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.argb(220, 255, 255, 255))
        }
        clockCard.addView(time, LinearLayout.LayoutParams(-1, -2))
        clockCard.addView(day, LinearLayout.LayoutParams(-1, -2))
        top.addView(clockCard, LinearLayout.LayoutParams(0, dp(142), 1.1f).apply { rightMargin = dp(12) })

        val ratesCard = glassCard()
        ratesCard.addView(cardCaption("КУРСЫ ЦБ РФ"))
        val rates = cardValue("USD — ₽\nEUR — ₽")
        ratesCard.addView(rates, LinearLayout.LayoutParams(-1, 0, 1f))
        top.addView(ratesCard, LinearLayout.LayoutParams(0, dp(142), 1f).apply { rightMargin = dp(12) })

        val weatherCard = glassCard()
        weatherCard.addView(cardCaption("ПОГОДА · ЧЕЛЯБИНСК"))
        val weather = cardValue("Загружаем текущие условия…")
        weatherCard.addView(weather, LinearLayout.LayoutParams(-1, 0, 1f))
        top.addView(weatherCard, LinearLayout.LayoutParams(0, dp(142), 1f))
        content.addView(top, LinearLayout.LayoutParams(-1, dp(142)))

        val news = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        val ruCard = glassCard()
        ruCard.addView(newsHeading("РОССИЯ"))
        val ru = newsValue("Российская лента загружается…")
        ruCard.addView(ru, LinearLayout.LayoutParams(-1, 0, 1f))
        news.addView(ruCard, LinearLayout.LayoutParams(0, dp(510), 1f).apply {
            rightMargin = dp(12); topMargin = dp(18)
        })

        val worldCard = glassCard()
        worldCard.addView(newsHeading("МИР"))
        val world = newsValue("Мировая лента загружается…")
        worldCard.addView(world, LinearLayout.LayoutParams(-1, 0, 1f))
        news.addView(worldCard, LinearLayout.LayoutParams(0, dp(510), 1f).apply {
            leftMargin = dp(12); topMargin = dp(18)
        })
        content.addView(news, LinearLayout.LayoutParams(-1, dp(528)))
        content.addView(TextView(this).apply {
            text = "RFTV  ·  Экран ожидания"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.argb(175, 255, 255, 255))
            setPadding(0, dp(12), 0, 0)
        }, LinearLayout.LayoutParams(-1, dp(34)))

        frame.addView(content, FrameLayout.LayoutParams(-1, -1))
        root = frame
        wallpaper = image
        clock = time
        date = day
        ratesText = rates
        weatherText = weather
        russiaText = ru
        worldText = world
        setContentView(frame)
    }

    private fun glassCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = RftvDesignSystem.surface(this@RftvDreamService, Color.argb(72, 226, 239, 255), 28)
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

    private fun newsHeading(value: String) = TextView(this).apply {
        text = value
        textSize = 20f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.08f
        setTextColor(Color.WHITE)
        setPadding(dp(4), dp(2), dp(4), dp(12))
    }

    private fun newsValue(value: String) = TextView(this).apply {
        text = value
        textSize = 17f
        setTextColor(Color.WHITE)
        maxLines = 8
        setLineSpacing(dp(4).toFloat(), 1f)
        setPadding(dp(6), dp(6), dp(6), dp(6))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun updateClock() {
        val now = Date()
        clock?.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        date?.text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
            .format(now).replaceFirstChar { it.uppercase() }
    }

    private fun refreshOnlineContent(hourKey: Long) {
        executor.execute {
            val imageFile = activeHomeWallpaperFile()
            val dashboard = loadDashboard()
            val blurred = imageFile?.let { file ->
                val source = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inSampleSize = 2
                })
                source?.let {
                    val result = try {
                        GlassBackdrop.createBlurredSnapshot(it, 480, 270, radius = 10, passes = 3)
                    } catch (_: Exception) { null }
                    it.recycle()
                    result
                }
            }
            handler.post {
                if (!dreaming) {
                    blurred?.recycle()
                    return@post
                }
                if (blurred != null) wallpaper?.setImageBitmap(blurred)
                ratesText?.text = dashboard.rates
                weatherText?.text = dashboard.weather
                russiaText?.text = dashboard.russia
                worldText?.text = dashboard.world
            }
        }
    }

    private fun activeHomeWallpaperFile(): File? {
        val prefs = getSharedPreferences("rftv_preferences", MODE_PRIVATE)
        val mode = prefs.getInt("theme_mode", 2)
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val dark = when (mode) {
            1 -> true
            0 -> false
            else -> hour >= 19 || hour < 7
        }
        val index = prefs.getInt(if (dark) "wallpaper_dark" else "wallpaper_light", 0).coerceAtLeast(0)
        val directory = File(filesDir, "wallpapers")
        val full = File(directory, WallpaperImageUrls.cacheFileName(dark, index, false))
        val preview = File(directory, WallpaperImageUrls.cacheFileName(dark, index, true))
        return when {
            full.isFile && full.length() > 10_000L -> full
            preview.isFile && preview.length() > 5_000L -> preview
            else -> null
        }
    }

    private fun readHttpText(address: String, charsetName: String = "UTF-8"): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 10_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "RFTV-Launcher/0.2")
        }
        return try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader(java.nio.charset.Charset.forName(charsetName)).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun loadDashboard(): DashboardData {
        val weather = fetchWeather()
        val rates = fetchRates()
        val russian = fetchRssTitles("https://ria.ru/export/rss2/archive/index.xml")
        val world = fetchRssTitles("https://news.google.com/rss/headlines/section/topic/WORLD?hl=ru&gl=RU&ceid=RU:ru")
        return DashboardData(weather, rates, russian, world)
    }

    private fun fetchWeather(): String {
        return try {
            // Chelyabinsk is the initial default; city selection will be exposed in launcher settings.
            val json = readHttpText("https://api.open-meteo.com/v1/forecast?latitude=55.1599&longitude=61.4026&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m&timezone=Asia%2FYekaterinburg")
            val current = JSONObject(json).getJSONObject("current")
            val temp = current.getDouble("temperature_2m").toInt()
            val feels = current.getDouble("apparent_temperature").toInt()
            val wind = current.getDouble("wind_speed_10m").toInt()
            "$temp°C, ощущается $feels°C, ветер $wind км/ч"
        } catch (_: Exception) {
            "нет данных"
        }
    }

    private fun fetchRates(): String {
        return try {
            val parser = Xml.newPullParser()
            parser.setInput(java.io.StringReader(readHttpText("https://www.cbr.ru/scripts/XML_daily.asp", "windows-1251")))
            var currency = ""
            var value = ""
            var usd = ""
            var eur = ""
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "CharCode" -> currency = parser.nextText()
                        "Value" -> value = parser.nextText()
                    }
                } else if (event == XmlPullParser.END_TAG && parser.name == "Valute") {
                    if (currency == "USD") usd = value
                    if (currency == "EUR") eur = value
                }
                event = parser.next()
            }
            "USD $usd ₽ · EUR $eur ₽"
        } catch (_: Exception) {
            "нет данных"
        }
    }

    private fun fetchRssTitles(feedUrl: String): String {
        return try {
            val parser = Xml.newPullParser()
            parser.setInput(java.io.StringReader(readHttpText(feedUrl)))
            val titles = mutableListOf<String>()
            var inItem = false
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT && titles.size < 5) {
                if (event == XmlPullParser.START_TAG) {
                    if (parser.name == "item") inItem = true
                    if (inItem && parser.name == "title") {
                        val title = parser.nextText().trim()
                        if (title.isNotBlank()) titles.add(title)
                    }
                } else if (event == XmlPullParser.END_TAG && parser.name == "item") {
                    inItem = false
                }
                event = parser.next()
            }
            if (titles.isEmpty()) "Новостная лента пуста" else titles.joinToString("\n• ", "• ")
        } catch (_: Exception) {
            "Лента временно недоступна"
        }
    }
}
