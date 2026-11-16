package com.automotive.swiftlauncher

import android.content.Context
import android.content.SharedPreferences

class PreferenceHelper(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("swift_launcher_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_AA_PKG = "pref_aa_pkg"
        const val KEY_CARPLAY_PKG = "pref_carplay_pkg"
        const val KEY_YOUTUBE_PKG = "pref_youtube_pkg"
        const val KEY_CACHED_WEATHER = "pref_cached_weather"
        const val KEY_WEATHER_CODE = "pref_weather_code"
        const val KEY_IS_24_HOUR = "pref_is_24_hour"
        const val KEY_CITY_NAME = "pref_city_name"

        // Defaults
        const val DEFAULT_AA_PKG = "io.github.slashmax.openheadunit"
        const val DEFAULT_CARPLAY_PKG = "com.zjinnova.zlink"
        const val DEFAULT_YOUTUBE_PKG = "com.google.android.youtube"
        const val DEFAULT_CITY = "Hyderabad"
    }

    var androidAutoPackage: String
        get() = prefs.getString(KEY_AA_PKG, DEFAULT_AA_PKG) ?: DEFAULT_AA_PKG
        set(value) = prefs.edit().putString(KEY_AA_PKG, value).apply()

    var carPlayPackage: String
        get() = prefs.getString(KEY_CARPLAY_PKG, DEFAULT_CARPLAY_PKG) ?: DEFAULT_CARPLAY_PKG
        set(value) = prefs.edit().putString(KEY_CARPLAY_PKG, value).apply()

    var youtubePackage: String
        get() = prefs.getString(KEY_YOUTUBE_PKG, DEFAULT_YOUTUBE_PKG) ?: DEFAULT_YOUTUBE_PKG
        set(value) = prefs.edit().putString(KEY_YOUTUBE_PKG, value).apply()

    var cachedWeather: String
        get() = prefs.getString(KEY_CACHED_WEATHER, "Hyderabad, 28°C Haze") ?: "Hyderabad, 28°C Haze"
        set(value) = prefs.edit().putString(KEY_CACHED_WEATHER, value).apply()

    var weatherCode: Int
        get() = prefs.getInt(KEY_WEATHER_CODE, 1)
        set(value) = prefs.edit().putInt(KEY_WEATHER_CODE, value).apply()

    var is24HourFormat: Boolean
        get() = prefs.getBoolean(KEY_IS_24_HOUR, true)
        set(value) = prefs.edit().putBoolean(KEY_IS_24_HOUR, value).apply()

    var cityName: String
        get() = prefs.getString(KEY_CITY_NAME, DEFAULT_CITY) ?: DEFAULT_CITY
        set(value) = prefs.edit().putString(KEY_CITY_NAME, value).apply()
}
