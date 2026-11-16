package com.automotive.swiftlauncher

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

data class WeatherResult(
    val displayText: String,
    val weatherCode: Int
)

class WeatherService(context: Context) {

    private val prefs = PreferenceHelper(context)

    suspend fun fetchWeather(lat: Double = 17.3850, lon: Double = 78.4867): WeatherResult {
        return withContext(Dispatchers.IO) {
            try {
                val urlString =
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code"
                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.requestMethod = "GET"

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()

                    val json = JSONObject(response.toString())
                    val current = json.getJSONObject("current")
                    val temp = current.getDouble("temperature_2m").roundToInt()
                    val code = current.getInt("weather_code")
                    val condition = getWeatherCondition(code)

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
