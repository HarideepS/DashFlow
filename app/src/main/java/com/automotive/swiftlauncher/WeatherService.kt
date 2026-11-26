package com.automotive.swiftlauncher

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar
import kotlin.math.roundToInt

data class WeatherResult(
    val displayText: String,
    val weatherCode: Int
)

class WeatherService(context: Context) {

    private val prefs = PreferenceHelper(context)

    suspend fun fetchWeather(overrideLat: Double? = null, overrideLon: Double? = null): WeatherResult {
        return withContext(Dispatchers.IO) {
            try {
                var lat = overrideLat ?: prefs.cachedLat.toDouble()
                var lon = overrideLon ?: prefs.cachedLon.toDouble()

                // Resolve city coordinates via Open-Meteo Geocoding when city name changes
                val targetCity = prefs.cityName.trim()
                if (overrideLat == null && targetCity.isNotEmpty() &&
                    !targetCity.equals(prefs.geocodedCity, ignoreCase = true)
                ) {
                    resolveCityCoordinates(targetCity)?.let { (resolvedLat, resolvedLon) ->
                        lat = resolvedLat
                        lon = resolvedLon
                        prefs.cachedLat = lat.toFloat()
                        prefs.cachedLon = lon.toFloat()
                        prefs.geocodedCity = targetCity
                    }
                } else if (overrideLat != null && overrideLon != null) {
                    prefs.cachedLat = overrideLat.toFloat()
                    prefs.cachedLon = overrideLon.toFloat()
                }

                val urlString =
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,weather_code" +
                        "&daily=sunrise,sunset&timeformat=unixtime&timezone=auto&forecast_days=1"

                val body = httpGet(urlString)
                if (body != null) {
                    val json = JSONObject(body)
                    val current = json.getJSONObject("current")
                    val temp = current.getDouble("temperature_2m").roundToInt()
                    val code = current.getInt("weather_code")
                    val condition = getWeatherCondition(code)

                    // Cache sunrise/sunset timestamps for zero-sensor auto-dimming
                    json.optJSONObject("daily")?.let { daily ->
                        val sunrises = daily.optJSONArray("sunrise")
                        val sunsets = daily.optJSONArray("sunset")
                        if (sunrises != null && sunrises.length() > 0 && sunsets != null && sunsets.length() > 0) {
                            prefs.sunriseEpochSec = sunrises.optLong(0, 0L)
                            prefs.sunsetEpochSec = sunsets.optLong(0, 0L)
                        }
                    }

                    val weatherStr = "${prefs.cityName}, ${temp}°C $condition"
                    prefs.cachedWeather = weatherStr
                    prefs.weatherCode = code
                    return@withContext WeatherResult(weatherStr, code)
                }
            } catch (_: Exception) {
                // Offline fallback
            }
            return@withContext WeatherResult(prefs.cachedWeather, prefs.weatherCode)
        }
    }

    fun isNightTimeNow(): Boolean {
        val sunrise = prefs.sunriseEpochSec
        val sunset = prefs.sunsetEpochSec
        val nowSec = System.currentTimeMillis() / 1000L
        if (sunrise > 0L && sunset > sunrise) {
            // Valid only within +/- 36h of cached day; otherwise fallback to clock hour
            if (nowSec in (sunrise - 43200L)..(sunset + 43200L)) {
                return nowSec < sunrise || nowSec >= sunset
            }
        }
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour < 6 || hour >= 18
    }

    private fun resolveCityCoordinates(city: String): Pair<Double, Double>? {
        return try {
            val encoded = URLEncoder.encode(city, "UTF-8")
            val body = httpGet("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&format=json")
                ?: return null
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return null
            if (results.length() == 0) return null
            val first = results.getJSONObject(0)
            first.getDouble("latitude") to first.getDouble("longitude")
        } catch (_: Exception) {
            null
        }
    }

    private fun httpGet(urlString: String): String? {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4000
            readTimeout = 4000
            requestMethod = "GET"
        }
        return try {
            if (conn.responseCode == 200) {
                BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
                    reader.readText()
                }
            } else {
                null
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun getWeatherCondition(code: Int): String {
        return when (code) {
            0 -> "Clear"
            1, 2, 3 -> "Haze"
            45, 48 -> "Fog"
            51, 53, 55 -> "Drizzle"
            61, 63, 65 -> "Rain"
            71, 73, 75 -> "Snow"
            80, 81, 82 -> "Showers"
            95, 96, 99 -> "Thunderstorm"
            else -> "Fair"
        }
    }
}
