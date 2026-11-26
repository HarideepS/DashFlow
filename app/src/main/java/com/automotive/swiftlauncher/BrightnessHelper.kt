package com.automotive.swiftlauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast

class BrightnessHelper(private val activity: Activity) {

    enum class BrightnessLevel(val title: String, val systemValue: Int, val windowFloat: Float) {
        DAY("100% Brightness", 255, 1.0f),
        DUSK("50% Brightness", 128, 0.5f),
        NIGHT("15% Night Mode", 40, 0.15f),
        AUTO("Auto Brightness", -1, -1f)
    }

    private var currentLevelIndex = 0
    private val levels = BrightnessLevel.values()

    fun cycleBrightness(): BrightnessLevel {
        currentLevelIndex = (currentLevelIndex + 1) % levels.size
        val level = levels[currentLevelIndex]
        applyLevel(level, showToast = true)
        return level
    }

    fun applySunBasedDimming(isNight: Boolean) {
        val target = if (isNight) BrightnessLevel.NIGHT else BrightnessLevel.DAY
        currentLevelIndex = levels.indexOf(target).coerceAtLeast(0)
        applyLevel(target, showToast = false)
    }

    private fun applyLevel(level: BrightnessLevel, showToast: Boolean) {
        val window = activity.window
        val lp = window.attributes

        if (level == BrightnessLevel.AUTO) {
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = lp
            trySetSystemAutoBrightness(true)
        } else {
            lp.screenBrightness = level.windowFloat
            window.attributes = lp
            trySetSystemAutoBrightness(false)
            trySetSystemBrightness(level.systemValue)
        }
        if (showToast) {
            Toast.makeText(activity, level.title, Toast.LENGTH_SHORT).show()
        }
    }

    private fun trySetSystemBrightness(value: Int) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.System.canWrite(activity)) {
                    Settings.System.putInt(
                        activity.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS,
                        value
                    )
                }
            } else {
                Settings.System.putInt(
                    activity.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    value
                )
            }
        } catch (_: Exception) {
            // Window brightness override will still be active
        }
    }

    private fun trySetSystemAutoBrightness(enable: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(activity)) {
                return
            }
            val mode = if (enable) {
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            } else {
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            }
            Settings.System.putInt(
                activity.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                mode
            )
        } catch (_: Exception) {
            // Silently ignore permission hurdles
        }
    }

    fun requestWriteSettingsPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(activity)) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = Uri.parse("package:" + activity.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    activity.startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }
}
