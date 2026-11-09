package com.rftv.launcher

import android.app.Activity
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
import android.widget.LinearLayout
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** Full-screen interactive RFTV idle screen. Any remote key dismisses it. */
class ScreensaverActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var details: TextView
    private var alive = false

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
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
        val root = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    RftvDesignSystem.ColorTokens.SCREENSAVER_GRADIENT_START,
                    RftvDesignSystem.ColorTokens.SCREENSAVER_GRADIENT_MID,
                    RftvDesignSystem.ColorTokens.SCREENSAVER_GRADIENT_END
                )
            )
            isFocusableInTouchMode = true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(
                dp(RftvDesignSystem.Spacing.SCREENSAVER_HORIZONTAL_PADDING),
                dp(RftvDesignSystem.Spacing.SCREENSAVER_TOP_PADDING),
                dp(RftvDesignSystem.Spacing.SCREENSAVER_HORIZONTAL_PADDING),
                dp(RftvDesignSystem.Spacing.SCREENSAVER_BOTTOM_PADDING)
            )
        }
        clock = TextView(this).apply {
            text = "00:00"
            textSize = RftvDesignSystem.Size.SCREENSAVER_CLOCK_TEXT_SP
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            letterSpacing = RftvDesignSystem.Size.CLOCK_LETTER_SPACING
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        content.addView(clock, LinearLayout.LayoutParams(-1, -2))
        date = TextView(this).apply {
            text = SimpleDateFormat("EEEE, d MMMM", Locale("ru")).format(Date()).replaceFirstChar { it.uppercase() }
            textSize = RftvDesignSystem.Size.SCREENSAVER_DATE_TEXT_SP
            setTextColor(RftvDesignSystem.ColorTokens.SCREENSAVER_TEXT_SECONDARY)
            gravity = Gravity.CENTER
        }
        content.addView(date, LinearLayout.LayoutParams(-1, dp(42)))
        details = TextView(this).apply {
            text = "Загружаем погоду, курсы валют и новости…"
            textSize = RftvDesignSystem.Size.SCREENSAVER_DETAILS_TEXT_SP
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setLineSpacing(dp(RftvDesignSystem.Spacing.SCREENSAVER_LINE_SPACING).toFloat(), 1f)
            setPadding(
                dp(24),
                dp(RftvDesignSystem.Spacing.SCREENSAVER_DETAILS_TOP_PADDING),
                dp(24),
                dp(RftvDesignSystem.Spacing.SCREENSAVER_DETAILS_BOTTOM_PADDING)
            )
            background = RftvDesignSystem.surface(
                this@ScreensaverActivity,
                RftvDesignSystem.ColorTokens.SCREENSAVER_GLASS,
                RftvDesignSystem.Radius.SCREENSAVER_DETAILS
            )
        }
        content.addView(details, LinearLayout.LayoutParams(-1, -2))
        content.addView(TextView(this).apply {
            text = "RFTV  ·  Нажмите любую кнопку, чтобы продолжить"
            textSize = RftvDesignSystem.Size.SCREENSAVER_HINT_TEXT_SP
            setTextColor(RftvDesignSystem.ColorTokens.SCREENSAVER_TEXT_MUTED)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(40)))
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        alive = true
        tick.run()
        loadDashboard()
        handler.removeCallbacks(refreshDashboard)
        handler.postDelayed(refreshDashboard, 15 * 60_000L)
    }

    override fun onPause() {
        alive = false
        handler.removeCallbacksAndMessages(null)
        super.onPause()
    }

    override fun onDestroy() {
        alive = false
        handler.removeCallbacksAndMessages(null)
        io.shutdownNow()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            finish()
            return true
        }
        return true
    }

    private fun loadDashboard(force: Boolean = false) {
        val cache = getSharedPreferences("screensaver_cache", MODE_PRIVATE)
        val cachedText = cache.getString("dashboard", null)
        val cachedAt = cache.getLong("dashboard_at", 0L)
        if (!cachedText.isNullOrBlank()) details.text = cachedText
        if (!force && !cachedText.isNullOrBlank() && System.currentTimeMillis() - cachedAt < 15 * 60_000L) return
        io.execute {
            val text = buildString {
                append("ПОГОДА\n").append(fetchWeather()).append("\n\n")
                append("КУРСЫ ЦБ РФ\n").append(fetchRates()).append("\n\n")
                append("НОВОСТИ\n").append(fetchNews())
            }
            cache.edit().putString("dashboard", text).putLong("dashboard_at", System.currentTimeMillis()).apply()
            handler.post { if (alive && !isFinishing) details.text = text }
        }
    }
    private fun fetchWeather(): String {
        try {
            val json = read("https://api.open-meteo.com/v1/forecast?latitude=55.1599&longitude=61.4026&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min&forecast_days=3&timezone=Asia%2FYekaterinburg")
            val obj = JSONObject(json)
            val now = obj.getJSONObject("current")
            val daily = obj.getJSONObject("daily")
            val forecast = (0 until minOf(3, daily.getJSONArray("temperature_2m_min").length())).joinToString(" · ") { index ->
                daily.getJSONArray("temperature_2m_min").getDouble(index).toInt().toString() + "…" + daily.getJSONArray("temperature_2m_max").getDouble(index).toInt() + "°"
            }
            return "Челябинск · " + now.getDouble("temperature_2m").toInt() + "°C, ощущается " + now.getDouble("apparent_temperature").toInt() + "°C\n" +
                "Влажность " + now.getInt("relative_humidity_2m") + "% · ветер " + now.getDouble("wind_speed_10m").toInt() + " км/ч\nПрогноз на 3 дня: " + forecast
        } catch (_: Exception) {
            return try {
                val current = JSONObject(read("https://wttr.in/Chelyabinsk?format=j1")).getJSONArray("current_condition").getJSONObject(0)
                val description = current.getJSONArray("weatherDesc").getJSONObject(0).getString("value")
                current.getString("temp_C") + "°C · " + description + ", ощущается " + current.getString("FeelsLikeC") + "°C\n" +
                    "Влажность " + current.getString("humidity") + "% · ветер " + current.getString("windspeedKmph") + " км/ч"
            } catch (_: Exception) { "Погодные данные временно недоступны" }
        }
    }
    private fun fetchRates(): String = try {
        val xml = read("https://www.cbr.ru/scripts/XML_daily.asp", "windows-1251")
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(java.io.StringReader(xml))
        var code = ""; var value = ""; var usd = "—"; var eur = "—"
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "CharCode" -> code = parser.nextText()
                    "Value" -> value = parser.nextText()
                }
            } else if (event == XmlPullParser.END_TAG && parser.name == "Valute") {
                if (code == "USD") usd = value
                if (code == "EUR") eur = value
            }
            event = parser.next()
        }
        "USD $usd ₽   ·   EUR $eur ₽"
    } catch (_: Exception) { "Курсы валют временно недоступны" }

    private fun fetchNews(): String = try {
        val xml = read("https://ria.ru/export/rss2/archive/index.xml")
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(java.io.StringReader(xml))
        val titles = mutableListOf<String>()
        var inside = false; var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && titles.size < 3) {
            if (event == XmlPullParser.START_TAG) {
                if (parser.name == "item") inside = true
                if (inside && parser.name == "title") titles.add(parser.nextText().trim())
            } else if (event == XmlPullParser.END_TAG && parser.name == "item") inside = false
            event = parser.next()
        }
        if (titles.isEmpty()) "Новостная лента пуста" else titles.joinToString("\n• ", "• ")
    } catch (_: Exception) { "Новостная лента временно недоступна" }

    private fun read(address: String, charset: String = "UTF-8"): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 7000
            connection.readTimeout = 9000
            connection.setRequestProperty("User-Agent", "RFTV-Launcher")
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader(java.nio.charset.Charset.forName(charset)).use { it.readText() }
        } finally { connection.disconnect() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        fun launch(activity: Activity) {
            activity.startActivity(android.content.Intent(activity, ScreensaverActivity::class.java))
        }
    }
}
