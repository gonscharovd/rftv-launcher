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
import java.io.FileOutputStream
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
    private var info: TextView? = null
    private var lastHourKey = -1L
    private var dataText = "Загружаем погоду, курсы и новости…"
    @Volatile private var dreaming = false

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
        }
        frame.addView(image, FrameLayout.LayoutParams(-1, -1))
        frame.addView(View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(24, 0, 0, 0), Color.argb(175, 0, 0, 0))
            )
        }, FrameLayout.LayoutParams(-1, -1))

        val time = TextView(this).apply {
            text = "00:00"
            textSize = 94f
            letterSpacing = -0.04f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setShadowLayer(20f, 0f, 3f, Color.argb(170, 0, 0, 0))
            gravity = Gravity.CENTER
        }
        val day = TextView(this).apply {
            textSize = 23f
            setTextColor(Color.argb(220, 255, 255, 255))
            setShadowLayer(8f, 0f, 2f, Color.argb(170, 0, 0, 0))
            gravity = Gravity.CENTER
        }
        val timeStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(time, LinearLayout.LayoutParams(-1, -2))
            addView(day, LinearLayout.LayoutParams(-1, 42))
        }
        frame.addView(timeStack, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(54, 0, 54, 28)
            gravity = Gravity.BOTTOM
        }
        val dashboard = TextView(this).apply {
            text = dataText
            textSize = 17f
            setTextColor(Color.WHITE)
            maxLines = 4
            setShadowLayer(8f, 0f, 2f, Color.BLACK)
        }
        info = dashboard
        bottom.addView(dashboard, LinearLayout.LayoutParams(-1, -2))
        bottom.addView(TextView(this).apply {
            text = "Фото: LoremFlickr  ·  Погода: Open-Meteo  ·  Курсы: Банк России  ·  Новости: РИА Новости / Google News"
            textSize = 10f
            setTextColor(Color.argb(190, 255, 255, 255))
            setPadding(0, 12, 0, 0)
        })
        frame.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        root = frame
        wallpaper = image
        clock = time
        date = day
        setContentView(frame)
    }

    private fun updateClock() {
        val now = Date()
        clock?.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        date?.text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
            .format(now).replaceFirstChar { it.uppercase() }
    }

    private fun refreshOnlineContent(hourKey: Long) {
        executor.execute {
            val imageFile = downloadWallpaper(hourKey) ?: latestCachedWallpaper()
            val dashboard = loadDashboard()
            handler.post {
                if (!dreaming) return@post
                if (imageFile != null && imageFile.exists()) {
                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath, options)
                    if (bitmap != null) wallpaper?.setImageBitmap(bitmap)
                }
                dataText = dashboard
                info?.text = dashboard
            }
        }
    }

    private fun downloadWallpaper(hourKey: Long): File? {
        val directory = File(cacheDir, "rftv-screensaver").apply { mkdirs() }
        val file = File(directory, "landscape-$hourKey.jpg")
        if (file.exists() && file.length() > 20_000L) return file
        var connection: HttpURLConnection? = null
        try {
            // The lock value changes every hour, producing a different random landscape.
            val url = "https://loremflickr.com/1920/1080/landscape,nature?lock=$hourKey"
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 25_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "RFTV-Launcher/0.2")
            }
            connection.connect()
            if (connection.responseCode !in 200..299) return null
            val temp = File(directory, file.name + ".part")
            connection.inputStream.use { input ->
                FileOutputStream(temp).use { output -> input.copyTo(output) }
            }
            if (temp.length() < 20_000L) {
                temp.delete()
                return null
            }
            if (file.exists()) file.delete()
            temp.renameTo(file)
            // Keep only the latest two images to protect the TV's limited storage.
            directory.listFiles()?.filter { it.name.startsWith("landscape-") && it != file }
                ?.sortedByDescending { it.lastModified() }?.drop(1)?.forEach { it.delete() }
            return file
        } catch (_: Exception) {
            return null
        } finally {
            connection?.disconnect()
        }
    }

    private fun latestCachedWallpaper(): File? {
        val directory = File(cacheDir, "rftv-screensaver")
        return directory.listFiles()
            ?.filter { it.name.startsWith("landscape-") && it.extension.equals("jpg", ignoreCase = true) && it.length() > 20_000L }
            ?.maxByOrNull { it.lastModified() }
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

    private fun loadDashboard(): String {
        val weather = fetchWeather()
        val rates = fetchRates()
        val russian = fetchRssTitle("https://ria.ru/export/rss2/archive/index.xml")
        val world = fetchRssTitle("https://news.google.com/rss/headlines/section/topic/WORLD?hl=ru&gl=RU&ceid=RU:ru")
        return buildString {
            append("ПОГОДА  ").append(weather).append("     ")
            append("ВАЛЮТА  ").append(rates).append('\n')
            append("РОССИЯ  ").append(russian).append('\n')
            append("МИР  ").append(world)
        }
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

    private fun fetchRssTitle(feedUrl: String): String {
        return try {
            val parser = Xml.newPullParser()
            parser.setInput(java.io.StringReader(readHttpText(feedUrl)))
            var inItem = false
            var title = ""
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    if (parser.name == "item") inItem = true
                    if (inItem && parser.name == "title" && title.isEmpty()) {
                        title = parser.nextText().trim()
                    }
                } else if (event == XmlPullParser.END_TAG && parser.name == "item" && inItem) {
                    if (title.isNotEmpty()) return title
                    inItem = false
                }
                event = parser.next()
            }
            "нет новостей"
        } catch (_: Exception) {
            "лента недоступна"
        }
    }
}
