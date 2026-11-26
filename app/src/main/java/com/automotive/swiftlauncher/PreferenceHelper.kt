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
        const val KEY_GEOCODED_CITY = "pref_geocoded_city"
        const val KEY_CACHED_LAT = "pref_cached_lat"
        const val KEY_CACHED_LON = "pref_cached_lon"
        const val KEY_SUNRISE_EPOCH = "pref_sunrise_epoch"
        const val KEY_SUNSET_EPOCH = "pref_sunset_epoch"

        // Vehicle & Dashboard Customization Keys
        const val KEY_VEHICLE_NAME = "pref_vehicle_name"
        const val KEY_LICENSE_PLATE = "pref_license_plate"
        const val KEY_CAR_PRESET = "pref_car_preset"
        const val KEY_CUSTOM_CAR_PATH = "pref_custom_car_path"
        const val KEY_CAR_SCALE = "pref_car_scale"
        const val KEY_ACCENT_COLOR = "pref_accent_color"

        // Head-Unit Telemetry & Utility Keys
        const val KEY_SPEED_MPH = "pref_speed_mph"
        const val KEY_SHOW_SPEEDOMETER = "pref_show_speedometer"
        const val KEY_AUTO_DIMMING = "pref_auto_dimming"
        const val KEY_BOOT_LAUNCH_PKG = "pref_boot_launch_pkg"
        const val KEY_BOOT_LAUNCH_LABEL = "pref_boot_launch_label"

        // Defaults
        const val DEFAULT_AA_PKG = "io.github.slashmax.openheadunit"
        const val DEFAULT_CARPLAY_PKG = "com.zjinnova.zlink"
        const val DEFAULT_YOUTUBE_PKG = "com.google.android.youtube"
        const val DEFAULT_CITY = "Hyderabad"
        const val DEFAULT_LAT = 17.3850f
        const val DEFAULT_LON = 78.4867f
        const val DEFAULT_VEHICLE_NAME = "Maruti Suzuki Swift"
        const val DEFAULT_LICENSE_PLATE = "TG08 G6391"
        const val DEFAULT_ACCENT_COLOR = "#4D7DF0"
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
        get() = prefs.getString(KEY_CACHED_WEATHER, null) ?: "$cityName, --°C"
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

    var geocodedCity: String
        get() = prefs.getString(KEY_GEOCODED_CITY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEOCODED_CITY, value).apply()

    var cachedLat: Float
        get() = prefs.getFloat(KEY_CACHED_LAT, DEFAULT_LAT)
        set(value) = prefs.edit().putFloat(KEY_CACHED_LAT, value).apply()

    var cachedLon: Float
        get() = prefs.getFloat(KEY_CACHED_LON, DEFAULT_LON)
        set(value) = prefs.edit().putFloat(KEY_CACHED_LON, value).apply()

    var sunriseEpochSec: Long
        get() = prefs.getLong(KEY_SUNRISE_EPOCH, 0L)
        set(value) = prefs.edit().putLong(KEY_SUNRISE_EPOCH, value).apply()

    var sunsetEpochSec: Long
        get() = prefs.getLong(KEY_SUNSET_EPOCH, 0L)
        set(value) = prefs.edit().putLong(KEY_SUNSET_EPOCH, value).apply()

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

    var accentColor: String
        get() = prefs.getString(KEY_ACCENT_COLOR, DEFAULT_ACCENT_COLOR) ?: DEFAULT_ACCENT_COLOR
        set(value) = prefs.edit().putString(KEY_ACCENT_COLOR, value).apply()

    var speedUnitMph: Boolean
        get() = prefs.getBoolean(KEY_SPEED_MPH, false)
        set(value) = prefs.edit().putBoolean(KEY_SPEED_MPH, value).apply()

    var showSpeedometer: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SPEEDOMETER, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_SPEEDOMETER, value).apply()

    var autoNightDimming: Boolean
        get() = prefs.getBoolean(KEY_AUTO_DIMMING, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_DIMMING, value).apply()

    var bootLaunchPackage: String
        get() = prefs.getString(KEY_BOOT_LAUNCH_PKG, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BOOT_LAUNCH_PKG, value).apply()

    var bootLaunchLabel: String
        get() = prefs.getString(KEY_BOOT_LAUNCH_LABEL, "None (Stay on DashFlow)") ?: "None (Stay on DashFlow)"
        set(value) = prefs.edit().putString(KEY_BOOT_LAUNCH_LABEL, value).apply()
}
