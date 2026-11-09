package com.rftv.launcher

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Shared, cached data source for both the manual and Android DreamService screensavers.
 * Every source is fetched independently and in parallel. A failed source never overwrites
 * its last successful value or freshness timestamp.
 */
internal object ScreensaverDashboardRepository {
    private const val TAG = "RFTVScreensaverData"
    private const val CACHE_NAME = "screensaver_cache"
    private const val FRESH_FOR_MS = 15 * 60_000L
    private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    private const val REQUEST_TIMEOUT_MS = 8_000
    private const val TASK_TIMEOUT_SECONDS = 14L

    private val requests = Executors.newFixedThreadPool(4)

    data class Snapshot(
        val weather: String,
        val rates: String,
        val russia: List<String>,
        val world: List<String>,
        val updatedAt: Long
    )

    fun readCached(context: Context): Snapshot {
        val cache = context.getSharedPreferences(CACHE_NAME, Context.MODE_PRIVATE)
        return Snapshot(
            weather = cache.getString("weather", "") ?: "",
            rates = cache.getString("rates", "") ?: "",
            russia = (cache.getString("russia", "") ?: "").split("\n").filter { it.isNotBlank() },
            world = (cache.getString("world", "") ?: "").split("\n").filter { it.isNotBlank() },
            updatedAt = listOf(
                cache.getLong("weather_at", 0L),
                cache.getLong("rates_at", 0L),
                cache.getLong("russia_at", 0L),
                cache.getLong("world_at", 0L),
                cache.getLong("dashboard_at", 0L)
            ).maxOrNull() ?: 0L
        )
    }

    fun isFresh(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val cache = context.getSharedPreferences(CACHE_NAME, Context.MODE_PRIVATE)
        val legacyTimestamp = cache.getLong("dashboard_at", 0L)
        fun sourceFresh(key: String, timestampKey: String): Boolean {
            val value = cache.getString(key, "").orEmpty()
            val timestamp = cache.getLong(timestampKey, legacyTimestamp)
            return value.isNotBlank() && isSourceFresh(timestamp, now)
        }
        return sourceFresh("weather", "weather_at") &&
            sourceFresh("rates", "rates_at") &&
            sourceFresh("russia", "russia_at") &&
            sourceFresh("world", "world_at")
    }

    internal fun isSourceFresh(timestamp: Long, now: Long): Boolean =
        timestamp > 0L && now >= timestamp && now - timestamp < FRESH_FOR_MS

    fun load(context: Context, force: Boolean = false): Snapshot {
        val cached = readCached(context)
        if (!force && isFresh(context)) return cached

        val weatherFuture: java.util.concurrent.Future<String> =
            requests.submit(Callable<String> { fetchWeather() })
        val ratesFuture: java.util.concurrent.Future<String> =
            requests.submit(Callable<String> { fetchRates() })
        val russiaFuture: java.util.concurrent.Future<List<String>> =
            requests.submit(Callable<List<String>> { fetchNews("https://ria.ru/export/rss2/archive/index.xml") })
        val worldFuture: java.util.concurrent.Future<List<String>> =
            requests.submit(Callable<List<String>> { fetchNews("https://news.google.com/rss/headlines/section/topic/WORLD?hl=ru&gl=RU&ceid=RU:ru") })

        val weatherValue: String?
        val ratesValue: String?
        val russiaValue: List<String>?
        val worldValue: List<String>?
        try {
            weatherValue = await(weatherFuture)
            ratesValue = await(ratesFuture)
            russiaValue = await(russiaFuture)
            worldValue = await(worldFuture)
        } catch (interrupted: InterruptedException) {
            weatherFuture.cancel(true)
            ratesFuture.cancel(true)
            russiaFuture.cancel(true)
            worldFuture.cancel(true)
            Thread.currentThread().interrupt()
            return cached
        }

        val successful = mutableMapOf<String, Any>()
        weatherValue?.takeIf { it.isNotBlank() }?.let { successful["weather"] = it }
        ratesValue?.takeIf { it.isNotBlank() }?.let { successful["rates"] = it }
        russiaValue?.takeIf { it.any(String::isNotBlank) }?.let { successful["russia"] = it }
        worldValue?.takeIf { it.any(String::isNotBlank) }?.let { successful["world"] = it }

        val now = System.currentTimeMillis()
        val cache = context.getSharedPreferences(CACHE_NAME, Context.MODE_PRIVATE)
        val edit = cache.edit()
        successful["weather"]?.let {
            edit.putString("weather", it as String).putLong("weather_at", now)
        }
        successful["rates"]?.let {
            edit.putString("rates", it as String).putLong("rates_at", now)
        }
        successful["russia"]?.let {
            val titles = (it as List<*>).filterIsInstance<String>()
            edit.putString("russia", titles.joinToString("\n")).putLong("russia_at", now)
        }
        successful["world"]?.let {
            val titles = (it as List<*>).filterIsInstance<String>()
            edit.putString("world", titles.joinToString("\n")).putLong("world_at", now)
        }
        if (successful.isNotEmpty()) edit.putLong("dashboard_at", now)
        edit.apply()

        return readCached(context)
    }

    private fun <T> await(future: java.util.concurrent.Future<T>): T? {
        return try {
            future.get(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (interrupted: InterruptedException) {
            future.cancel(true)
            throw interrupted
        } catch (error: Exception) {
            future.cancel(true)
            Log.w(TAG, "Dashboard source request failed", error.cause ?: error)
            null
        }
    }

    internal fun weatherDescription(code: Int): String = when (code) {
        0 -> "Ясно"
        1, 2 -> "Переменная облачность"
        3 -> "Облачно"
        45, 48 -> "Туман"
        in 51..67 -> "Дождь"
        in 71..77 -> "Снег"
        in 80..82 -> "Ливень"
        in 85..86 -> "Снегопад"
        95, 96, 99 -> "Гроза"
        else -> "Погодные условия"
    }

    private fun fetchWeather(): String {
        val address = "https://api.open-meteo.com/v1/forecast?latitude=55.1599&longitude=61.4026" +
            "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m" +
            "&timezone=Asia%2FYekaterinburg"
        val current = JSONObject(read(address)).getJSONObject("current")
        val temperature = current.getDouble("temperature_2m").toInt()
        val feels = current.getDouble("apparent_temperature").toInt()
        val wind = current.getDouble("wind_speed_10m").toInt()
        val condition = weatherDescription(current.getInt("weather_code"))
        return temperature.toString() + "°C · " + condition +
            "\nОщущается " + feels + "°C · ветер " + wind + " км/ч"
    }

    private fun fetchRates(): String {
        val xml = read("https://www.cbr.ru/scripts/XML_daily.asp", "windows-1251")
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))
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
        check(usd.isNotBlank() && eur.isNotBlank()) { "CBR response has no USD/EUR values" }
        return "USD  " + usd + " ₽\nEUR  " + eur + " ₽"
    }

    private fun fetchNews(feedUrl: String): List<String> {
        val xml = read(feedUrl)
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))
        val titles = mutableListOf<String>()
        var inItem = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && titles.size < 5) {
            if (event == XmlPullParser.START_TAG) {
                if (parser.name == "item") inItem = true
                if (inItem && parser.name == "title") {
                    val title = parser.nextText().replace(Regex("<[^>]*>"), "").trim()
                    if (title.isNotBlank() && !title.equals("Новости", ignoreCase = true)) {
                        titles.add(title)
                    }
                }
            } else if (event == XmlPullParser.END_TAG && parser.name == "item") {
                inItem = false
            }
            event = parser.next()
        }
        check(titles.isNotEmpty()) { "RSS feed contains no article titles: " + feedUrl }
        return titles
    }

    private fun read(address: String, charsetName: String = "UTF-8"): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = REQUEST_TIMEOUT_MS
            connection.readTimeout = REQUEST_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.useCaches = true
            connection.setRequestProperty("User-Agent", "RFTV-Launcher/0.8")
            connection.setRequestProperty(
                "Accept",
                "application/rss+xml, application/xml, application/json, text/xml, */*"
            )
            val status = connection.responseCode
            check(status in 200..299) { "HTTP " + status + " for " + address }
            val contentType = connection.contentType.orEmpty().lowercase()
            check(
                contentType.isBlank() ||
                    contentType.contains("xml") ||
                    contentType.contains("json") ||
                    contentType.startsWith("text/plain")
            ) { "Unexpected content type " + contentType + " for " + address }

            val bytes = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    check(total <= MAX_RESPONSE_BYTES) { "Response exceeds 2 MB: " + address }
                    bytes.write(buffer, 0, count)
                }
            }
            String(bytes.toByteArray(), Charset.forName(charsetName))
        } finally {
            connection.disconnect()
        }
    }
}
