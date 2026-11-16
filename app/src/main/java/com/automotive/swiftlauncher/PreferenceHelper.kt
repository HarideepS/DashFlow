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

        // Vehicle & Dashboard Customization Keys
        const val KEY_VEHICLE_NAME = "pref_vehicle_name"
        const val KEY_LICENSE_PLATE = "pref_license_plate"
        const val KEY_CAR_PRESET = "pref_car_preset"
        const val KEY_CUSTOM_CAR_PATH = "pref_custom_car_path"
        const val KEY_CAR_SCALE = "pref_car_scale"

        // Defaults
        const val DEFAULT_AA_PKG = "io.github.slashmax.openheadunit"
        const val DEFAULT_CARPLAY_PKG = "com.zjinnova.zlink"
        const val DEFAULT_YOUTUBE_PKG = "com.google.android.youtube"
        const val DEFAULT_CITY = "Hyderabad"
        const val DEFAULT_VEHICLE_NAME = "Maruti Suzuki Swift"
        const val DEFAULT_LICENSE_PLATE = "TG08 G6391"
        const val PRESET_SWIFT = "swift"
        const val PRESET_SEDAN = "sedan"
        const val PRESET_SUV = "suv"
        const val PRESET_SPORTS = "sports"
        const val PRESET_EV = "ev"
        const val PRESET_CUSTOM = "custom"
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

    var vehicleName: String
        get() = prefs.getString(KEY_VEHICLE_NAME, DEFAULT_VEHICLE_NAME) ?: DEFAULT_VEHICLE_NAME
        set(value) = prefs.edit().putString(KEY_VEHICLE_NAME, value).apply()

    var licensePlate: String
        get() = prefs.getString(KEY_LICENSE_PLATE, DEFAULT_LICENSE_PLATE) ?: DEFAULT_LICENSE_PLATE
        set(value) = prefs.edit().putString(KEY_LICENSE_PLATE, value).apply()

    var carPreset: String
        get() = prefs.getString(KEY_CAR_PRESET, PRESET_SWIFT) ?: PRESET_SWIFT
        set(value) = prefs.edit().putString(KEY_CAR_PRESET, value).apply()

    var customCarPath: String
        get() = prefs.getString(KEY_CUSTOM_CAR_PATH, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_CAR_PATH, value).apply()

    var carScale: Float
        get() = prefs.getFloat(KEY_CAR_SCALE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_CAR_SCALE, value).apply()
}
