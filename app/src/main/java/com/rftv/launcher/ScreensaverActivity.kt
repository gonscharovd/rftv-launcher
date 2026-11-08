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
    private lateinit var details: TextView
    private var alive = false

    private val tick = object : Runnable {
        override fun run() {
            if (!alive) return
            clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            handler.postDelayed(this, 15_000L)
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
                intArrayOf(Color.rgb(9, 24, 48), Color.rgb(24, 52, 85), Color.rgb(5, 13, 28))
            )
            isFocusableInTouchMode = true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(72), dp(36), dp(72), dp(44))
        }
        clock = TextView(this).apply {
            text = "00:00"
            textSize = 104f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            letterSpacing = -.04f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        content.addView(clock, LinearLayout.LayoutParams(-1, -2))
        content.addView(TextView(this).apply {
            text = SimpleDateFormat("EEEE, d MMMM", Locale("ru")).format(Date()).replaceFirstChar { it.uppercase() }
            textSize = 23f
            setTextColor(Color.argb(220, 255, 255, 255))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(42)))
        details = TextView(this).apply {
            text = "Загружаем погоду, курсы валют и новости…"
            textSize = 19f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setLineSpacing(dp(8).toFloat(), 1f)
            setPadding(0, dp(34), 0, dp(28))
        }
        content.addView(details, LinearLayout.LayoutParams(-1, -2))
        content.addView(TextView(this).apply {
            text = "RFTV  ·  Нажмите любую кнопку, чтобы продолжить"
            textSize = 14f
            setTextColor(Color.argb(175, 255, 255, 255))
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

    private fun loadDashboard() {
        io.execute {
            val text = buildString {
                append("ПОГОДА\n").append(fetchWeather()).append("\n\n")
                append("КУРСЫ ЦБ РФ\n").append(fetchRates()).append("\n\n")
                append("НОВОСТИ\n").append(fetchNews())
            }
            handler.post { if (alive && !isFinishing) details.text = text }
        }
    }

    private fun fetchWeather(): String = try {
        val json = read("https://api.open-meteo.com/v1/forecast?latitude=55.1599&longitude=61.4026&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min&timezone=Asia%2FYekaterinburg")
        val obj = JSONObject(json)
        val now = obj.getJSONObject("current")
        val daily = obj.getJSONObject("daily")
        "Челябинск · ${now.getDouble("temperature_2m").toInt()}°C, ощущается ${now.getDouble("apparent_temperature").toInt()}°C\nВлажность ${now.getInt("relative_humidity_2m")}% · ветер ${now.getDouble("wind_speed_10m").toInt()} км/ч\nСегодня ${daily.getJSONArray("temperature_2m_min").getDouble(0).toInt()}…${daily.getJSONArray("temperature_2m_max").getDouble(0).toInt()}°C"
    } catch (_: Exception) { "Погодные данные временно недоступны" }

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
