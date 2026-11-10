/*
 * Adapted from tvOS Launcher Weather.java by techgenius (Copyright 2026 techgenius).
 * Modified for RFTV: fixed Chelyabinsk coordinates, Android/Kotlin integration,
 * app-wide cache and Home/forecast/screensaver consumers.
 * Licensed under the Apache License, Version 2.0. See third_party/tvos-launcher/LICENSE.
 */

package com.rftv.launcher

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Resilient weather source for Home, forecast and screensaver.
 *
 * Open-Meteo is preferred; MET Norway and wttr.in are independent fallbacks.
 * A successful response is cached for 15 minutes and a failed refresh never
 * erases the last good forecast. Network calls must be made off the main thread.
 */
internal object RftvWeatherRepository {
    private const val PREFS = "rftv_weather_cache"
    private const val CACHE_KEY = "snapshot"
    private const val FRESH_MS = 15 * 60_000L
    private const val LAT = 55.1599
    private const val LON = 61.4026
    private const val PLACE = "Челябинск"
    private const val TIME_ZONE = "Asia/Yekaterinburg"
    private const val MAX_BYTES = 2 * 1024 * 1024

    data class Day(
        val date: String,
        val label: String,
        val code: Int,
        val minimum: Int,
        val maximum: Int
    )

    data class Snapshot(
        val place: String,
        val provider: String,
        val temperature: Int,
        val feelsLike: Int,
        val code: Int,
        val humidity: Int,
        val wind: Int,
        val windUnit: String,
        val fetchedAt: Long,
        val days: List<Day>
    ) {
        val summary: String
            get() = "$temperature° · ${weatherDescription(code)}\n$place"
    }

    fun cached(context: Context): Snapshot? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(CACHE_KEY, null)
            ?: return null
        return decode(raw)
    }

    fun load(context: Context, force: Boolean = false): Snapshot {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cached = cached(context)
        val now = System.currentTimeMillis()
        if (!force && cached != null && now >= cached.fetchedAt && now - cached.fetchedAt < FRESH_MS) {
            return cached
        }

        val sources: List<Pair<String, () -> Snapshot>> = listOf(
            "Open-Meteo" to { fetchOpenMeteo() },
            "MET Norway" to { fetchMetNorway() },
            "wttr.in" to { fetchWttr() }
        )
        val errors = mutableListOf<String>()
        for ((name, fetch) in sources) {
            try {
                val result = fetch().copy(provider = name, fetchedAt = System.currentTimeMillis())
                prefs.edit().putString(CACHE_KEY, encode(result)).apply()
                return result
            } catch (error: Exception) {
                errors += "$name: ${error.message ?: error.javaClass.simpleName}"
            }
        }
        if (cached != null) return cached
        throw IllegalStateException("Не удалось получить погоду. " + errors.joinToString("; "))
    }

    internal fun weatherDescription(code: Int): String = when (code) {
        0 -> "Ясно"
        1 -> "Преимущественно ясно"
        2 -> "Переменная облачность"
        3 -> "Облачно"
        45, 48 -> "Туман"
        in 51..57 -> "Морось"
        in 61..67 -> "Дождь"
        in 71..77 -> "Снег"
        in 80..82 -> "Ливень"
        in 85..86 -> "Снегопад"
        95, 96, 99 -> "Гроза"
        else -> "Погодные условия"
    }

    internal fun metCode(symbol: String?): Int {
        if (symbol.isNullOrBlank()) return 3
        val value = symbol.lowercase(Locale.US)
            .replace("_day", "")
            .replace("_night", "")
            .replace("_polartwilight", "")
        return when {
            "thunder" in value -> 95
            "snow" in value -> if (value.startsWith("heavy")) 75 else if (value.startsWith("light")) 71 else 73
            "sleet" in value -> 66
            "rain" in value && "showers" in value -> if (value.startsWith("heavy")) 82 else 80
            "rain" in value -> if (value.startsWith("heavy")) 65 else if (value.startsWith("light")) 61 else 63
            value == "clearsky" -> 0
            value == "fair" -> 1
            value == "partlycloudy" -> 2
            value == "fog" -> 45
            else -> 3
        }
    }

    internal fun wwoCode(code: Int): Int = when (code) {
        113 -> 0
        116 -> 2
        143, 248, 260 -> 45
        176, 263, 266, 293, 296, 353 -> 61
        299, 302, 356 -> 63
        305, 308, 359 -> 65
        179, 227, 323, 326, 368 -> 71
        329, 332, 335, 371 -> 73
        230, 338, 392, 395 -> 75
        182, 185, 281, 284, 311, 314, 317, 320, 350, 362, 365, 374, 377 -> 66
        200, 386, 389 -> 95
        else -> 3
    }

    private fun fetchOpenMeteo(): Snapshot {
        val address = "https://api.open-meteo.com/v1/forecast?latitude=$LAT&longitude=$LON" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
            "&forecast_days=6&timezone=Asia%2FYekaterinburg"
        val payload = JSONObject(read(address))
        val current = payload.getJSONObject("current")
        val daily = payload.getJSONObject("daily")
        val dates = daily.getJSONArray("time")
        val codes = daily.getJSONArray("weather_code")
        val lows = daily.getJSONArray("temperature_2m_min")
        val highs = daily.getJSONArray("temperature_2m_max")
        val days = (0 until minOf(5, dates.length())).map { index ->
            val date = dates.getString(index)
            Day(
                date = date,
                label = formatDate(date),
                code = codes.getInt(index),
                minimum = lows.getDouble(index).roundToInt(),
                maximum = highs.getDouble(index).roundToInt()
            )
        }
        check(days.isNotEmpty()) { "Open-Meteo returned no daily forecast" }
        return Snapshot(
            place = PLACE,
            provider = "Open-Meteo",
            temperature = current.getDouble("temperature_2m").roundToInt(),
            feelsLike = current.optDouble("apparent_temperature", current.getDouble("temperature_2m")).roundToInt(),
            code = current.getInt("weather_code"),
            humidity = current.optInt("relative_humidity_2m"),
            wind = current.optDouble("wind_speed_10m").roundToInt(),
            windUnit = "км/ч",
            fetchedAt = System.currentTimeMillis(),
            days = days
        )
    }

    private data class MutableDay(
        val date: String,
        var minimum: Int = Int.MAX_VALUE,
        var maximum: Int = Int.MIN_VALUE,
        var code: Int = 3,
        var noonDistance: Int = Int.MAX_VALUE
    )

    private fun fetchMetNorway(): Snapshot {
        val address = String.format(
            Locale.US,
            "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f",
            LAT,
            LON
        )
        val payload = JSONObject(read(address, "application/json"))
        val series = payload.getJSONObject("properties").getJSONArray("timeseries")
        val utc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone(TIME_ZONE)
        }
        val localZone = TimeZone.getTimeZone(TIME_ZONE)
        val grouped = LinkedHashMap<String, MutableDay>()
        var currentTemperature: Int? = null
        var currentFeels = 0
        var currentCode = 3
        var currentHumidity = 0
        var currentWind = 0
        val now = System.currentTimeMillis()

        for (index in 0 until series.length()) {
            val entry = series.getJSONObject(index)
            val timestamp = utc.parse(entry.getString("time"))?.time ?: continue
            val data = entry.getJSONObject("data")
            val details = data.getJSONObject("instant").getJSONObject("details")
            val tempC = details.getDouble("air_temperature")
            val temp = tempC.roundToInt()
            val humidity = details.optDouble("relative_humidity", 50.0)
            val windMs = details.optDouble("wind_speed", 0.0)
            val symbol = data.optJSONObject("next_1_hours")?.optJSONObject("summary")?.optString("symbol_code")
                ?: data.optJSONObject("next_6_hours")?.optJSONObject("summary")?.optString("symbol_code")
            val code = metCode(symbol)

            if (currentTemperature == null) {
                currentTemperature = temp
                currentFeels = apparentTemperature(tempC, humidity, windMs).roundToInt()
                currentCode = code
                currentHumidity = humidity.roundToInt()
                currentWind = (windMs * 3.6).roundToInt()
            }

            val date = dateFormat.format(Date(timestamp))
            val day = grouped.getOrPut(date) { MutableDay(date) }
            day.minimum = minOf(day.minimum, temp)
            day.maximum = maxOf(day.maximum, temp)
            val localCalendar = Calendar.getInstance(localZone).apply { timeInMillis = timestamp }
            val noonDistance = abs(localCalendar.get(Calendar.HOUR_OF_DAY) - 12)
            if (noonDistance < day.noonDistance && symbol != null) {
                day.noonDistance = noonDistance
                day.code = code
            }
        }

        val days = grouped.values.take(5).map { day ->
            Day(day.date, formatDate(day.date), day.code, day.minimum, day.maximum)
        }
        check(currentTemperature != null && days.isNotEmpty()) { "MET Norway returned no forecast" }
        return Snapshot(
            PLACE, "MET Norway", currentTemperature!!, currentFeels, currentCode,
            currentHumidity, currentWind, "км/ч", now, days
        )
    }

    private fun fetchWttr(): Snapshot {
        val address = String.format(Locale.US, "https://wttr.in/%.4f,%.4f?format=j1", LAT, LON)
        val payload = JSONObject(read(address, "application/json"))
        val current = payload.getJSONArray("current_condition").getJSONObject(0)
        val daysArray = payload.getJSONArray("weather")
        val days = (0 until minOf(5, daysArray.length())).map { index ->
            val day = daysArray.getJSONObject(index)
            var dayCode = 3
            val hourly = day.optJSONArray("hourly")
            if (hourly != null) {
                for (hourIndex in 0 until hourly.length()) {
                    val hour = hourly.getJSONObject(hourIndex)
                    if (hour.optInt("time", -1) == 1200) {
                        dayCode = wwoCode(hour.optInt("weatherCode", 116))
                        break
                    }
                }
            }
            val date = day.getString("date")
            Day(
                date,
                formatDate(date),
                dayCode,
                day.getString("mintempC").toDouble().roundToInt(),
                day.getString("maxtempC").toDouble().roundToInt()
            )
        }
        check(days.isNotEmpty()) { "wttr.in returned no daily forecast" }
        val place = try {
            payload.getJSONArray("nearest_area").getJSONObject(0)
                .getJSONArray("areaName").getJSONObject(0).getString("value")
        } catch (_: Exception) {
            PLACE
        }
        return Snapshot(
            place = place,
            provider = "wttr.in",
            temperature = current.getString("temp_C").toDouble().roundToInt(),
            feelsLike = current.optString("FeelsLikeC", current.getString("temp_C")).toDouble().roundToInt(),
            code = wwoCode(current.optInt("weatherCode", 116)),
            humidity = current.optInt("humidity"),
            wind = current.optString("windspeedKmph", "0").toDouble().roundToInt(),
            windUnit = "км/ч",
            fetchedAt = System.currentTimeMillis(),
            days = days
        )
    }

    private fun apparentTemperature(tempC: Double, humidity: Double, windMs: Double): Double {
        val vapour = humidity / 100.0 * 6.105 * exp(17.27 * tempC / (237.7 + tempC))
        return tempC + 0.33 * vapour - 0.70 * windMs - 4.0
    }

    private fun formatDate(date: String): String {
        return try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone(TIME_ZONE) }.parse(date) ?: return date
            SimpleDateFormat("EEEE, d MMMM", Locale("ru")).format(parsed)
                .replaceFirstChar { it.uppercase() }
        } catch (_: Exception) {
            date
        }
    }

    private fun read(address: String, accept: String = "application/json"): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 9_000
            connection.readTimeout = 12_000
            connection.instanceFollowRedirects = true
            connection.useCaches = true
            connection.setRequestProperty("User-Agent", "RFTV-Launcher/0.9 (Android TV; github.com/gonscharovd/rftv-launcher)")
            connection.setRequestProperty("Accept", "$accept, */*;q=0.5")
            val status = connection.responseCode
            check(status in 200..299) { "HTTP $status" }
            val bytes = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    check(total <= MAX_BYTES) { "Weather response exceeds 2 MB" }
                    bytes.write(buffer, 0, count)
                }
            }
            String(bytes.toByteArray(), Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(snapshot: Snapshot): String {
        val days = org.json.JSONArray()
        snapshot.days.forEach { day ->
            days.put(JSONObject()
                .put("date", day.date)
                .put("label", day.label)
                .put("code", day.code)
                .put("minimum", day.minimum)
                .put("maximum", day.maximum))
        }
        return JSONObject()
            .put("place", snapshot.place)
            .put("provider", snapshot.provider)
            .put("temperature", snapshot.temperature)
            .put("feelsLike", snapshot.feelsLike)
            .put("code", snapshot.code)
            .put("humidity", snapshot.humidity)
            .put("wind", snapshot.wind)
            .put("windUnit", snapshot.windUnit)
            .put("fetchedAt", snapshot.fetchedAt)
            .put("days", days)
            .toString()
    }

    private fun decode(raw: String): Snapshot? = try {
        val json = JSONObject(raw)
        val daysJson = json.getJSONArray("days")
        val days = (0 until daysJson.length()).map { index ->
            val day = daysJson.getJSONObject(index)
            Day(
                day.getString("date"),
                day.getString("label"),
                day.getInt("code"),
                day.getInt("minimum"),
                day.getInt("maximum")
            )
        }
        Snapshot(
            json.optString("place", PLACE),
            json.optString("provider", "cached"),
            json.getInt("temperature"),
            json.optInt("feelsLike", json.getInt("temperature")),
            json.getInt("code"),
            json.optInt("humidity"),
            json.optInt("wind"),
            json.optString("windUnit", "км/ч"),
            json.getLong("fetchedAt"),
            days
        )
    } catch (_: Exception) {
        null
    }
}
