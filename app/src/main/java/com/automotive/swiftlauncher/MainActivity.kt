package com.automotive.swiftlauncher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.automotive.swiftlauncher.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferenceHelper
    private lateinit var weatherService: WeatherService
    private lateinit var brightnessHelper: BrightnessHelper
    private lateinit var audioManager: AudioManager

    private var targetRebindKey: String? = null
    private var lastNonZeroVolume: Int = 8

    // Activity Result Launcher for re-binding cards
    private val appPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val selectedPkg = result.data?.getStringExtra(AppDrawerActivity.EXTRA_SELECTED_PACKAGE)
            val selectedLabel = result.data?.getStringExtra(AppDrawerActivity.EXTRA_SELECTED_LABEL)
            if (selectedPkg != null && targetRebindKey != null) {
                when (targetRebindKey) {
                    PreferenceHelper.KEY_AA_PKG -> {
                        prefs.androidAutoPackage = selectedPkg
                        Toast.makeText(this, "Android Auto mapped to: $selectedLabel", Toast.LENGTH_SHORT).show()
                    }
                    PreferenceHelper.KEY_CARPLAY_PKG -> {
                        prefs.carPlayPackage = selectedPkg
                        Toast.makeText(this, "CarPlay mapped to: $selectedLabel", Toast.LENGTH_SHORT).show()
                    }
                    PreferenceHelper.KEY_YOUTUBE_PKG -> {
                        prefs.youtubePackage = selectedPkg
                        Toast.makeText(this, "YouTube mapped to: $selectedLabel", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private val systemStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiManager.WIFI_STATE_CHANGED_ACTION -> updateWifiState()
                "android.media.VOLUME_CHANGED_ACTION" -> updateVolumeState()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemUI()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferenceHelper(this)
        weatherService = WeatherService(this)
        brightnessHelper = BrightnessHelper(this)
        audioManager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (currentVol > 0) {
            lastNonZeroVolume = currentVol
        }

        setupClockAndDate()
        setupCards()
        setupToggles()
        loadWeather()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        updateWifiState()
        updateVolumeState()
        updateDateText()

        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction("android.media.VOLUME_CHANGED_ACTION")
        }
        registerReceiver(systemStateReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(systemStateReceiver)
        } catch (_: Exception) {}
    }

    private fun setupClockAndDate() {
        applyClockFormat(prefs.is24HourFormat)
        updateDateText()

        // Tapping the time / date area toggles 12h vs 24h format!
        binding.clockContainer.setOnClickListener {
            prefs.is24HourFormat = !prefs.is24HourFormat
            applyClockFormat(prefs.is24HourFormat)
            val msg = if (prefs.is24HourFormat) "24-hour clock enabled" else "12-hour clock (AM/PM) enabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        binding.weatherContainer.setOnClickListener {
            Toast.makeText(this, "Updating weather...", Toast.LENGTH_SHORT).show()
            loadWeather()
        }
    }

    private fun applyClockFormat(is24Hour: Boolean) {
        if (is24Hour) {
            binding.textClock.format12Hour = "HH:mm"
            binding.textClock.format24Hour = "HH:mm"
        } else {
            binding.textClock.format12Hour = "hh:mm a"
            binding.textClock.format24Hour = "hh:mm a"
        }
    }

    private fun updateDateText() {
        val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
        binding.textDayDate.text = dayFormat.format(Date())
    }



    private fun setupCards() {
        // 1. Android Auto (Square Card with Official AA Icon)
        binding.cardAndroidAuto.setOnClickListener {
            launchPackageOrPrompt(
                prefs.androidAutoPackage,
                "Open Headunit not installed.\nLong-press to assign another app.",
                PreferenceHelper.KEY_AA_PKG
            )
        }
        binding.cardAndroidAuto.setOnLongClickListener {
            openAppPicker(PreferenceHelper.KEY_AA_PKG)
            true
        }

        // 2. Apple CarPlay (Square Card with Official CarPlay Icon)
        binding.cardCarPlay.setOnClickListener {
            launchPackageOrPrompt(
                prefs.carPlayPackage,
                "ZLink not installed.\nLong-press to assign another app.",
                PreferenceHelper.KEY_CARPLAY_PKG
            )
        }
        binding.cardCarPlay.setOnLongClickListener {
            openAppPicker(PreferenceHelper.KEY_CARPLAY_PKG)
            true
        }

        // 3. YouTube (Square Card with Official YouTube Icon)
        binding.cardYouTube.setOnClickListener {
            launchPackageOrPrompt(
                prefs.youtubePackage,
                "YouTube not installed.\nLong-press to assign another app.",
                PreferenceHelper.KEY_YOUTUBE_PKG
            )
        }
        binding.cardYouTube.setOnLongClickListener {
            openAppPicker(PreferenceHelper.KEY_YOUTUBE_PKG)
            true
        }

        // 4. Settings (Rectangle Tile)
        binding.cardSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }

        // 5. All Apps (Rectangle Tile)
        binding.cardAllApps.setOnClickListener {
            val intent = Intent(this, AppDrawerActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupToggles() {
        // Wi-Fi Toggle
        binding.btnWifi.setOnClickListener {
            toggleWifiAction()
        }
        binding.btnWifi.setOnLongClickListener {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            true
        }

        // Bluetooth Settings
        binding.btnBluetooth.setOnClickListener {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }

        // Screen Brightness Cycle
        binding.btnBrightness.setOnClickListener {
            brightnessHelper.cycleBrightness()
        }
        binding.btnBrightness.setOnLongClickListener {
            brightnessHelper.requestWriteSettingsPermissionIfNeeded()
            true
        }

        // Media Volume Toggle (Mute / Unmute)
        binding.btnVolume.setOnClickListener {
            toggleVolumeMute()
        }
        binding.btnVolume.setOnLongClickListener {
            startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
            true
        }
    }

    private fun toggleVolumeMute() {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (current > 0) {
            lastNonZeroVolume = current
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
            Toast.makeText(this, "Muted", Toast.LENGTH_SHORT).show()
        } else {
            val target = if (lastNonZeroVolume > 0) lastNonZeroVolume else (audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * 0.6).toInt()
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI or AudioManager.FLAG_PLAY_SOUND)
            Toast.makeText(this, "Unmuted ($target)", Toast.LENGTH_SHORT).show()
        }
        updateVolumeState()
    }

    private fun updateVolumeState() {
        try {
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (current == 0) {
                binding.iconVolume.setImageResource(R.drawable.ic_volume_mute)
                binding.labelVolume.text = "Mute"
                binding.iconVolume.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
            } else {
                binding.iconVolume.setImageResource(R.drawable.ic_volume_up)
                binding.labelVolume.text = "Vol"
                binding.iconVolume.setColorFilter(ContextCompat.getColor(this, R.color.text_primary))
            }
        } catch (_: Exception) {}
    }

    private fun toggleWifiAction() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val panelIntent = Intent(Settings.Panel.ACTION_WIFI)
                startActivity(panelIntent)
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
        } else {
            try {
                val isEnabled = wifiManager.isWifiEnabled
                wifiManager.isWifiEnabled = !isEnabled
                Toast.makeText(
                    this,
                    if (!isEnabled) "Wi-Fi Enabled" else "Wi-Fi Disabled",
                    Toast.LENGTH_SHORT
                ).show()
                updateWifiState()
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
        }
    }

    private fun updateWifiState() {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val isEnabled = wifiManager.isWifiEnabled
            binding.btnWifi.setBackgroundResource(
                if (isEnabled) R.drawable.bg_toggle_pill_active else R.drawable.bg_toggle_pill
            )
            binding.iconWifi.setColorFilter(
                ContextCompat.getColor(this, if (isEnabled) R.color.card_android_auto else R.color.text_primary)
            )
        } catch (_: Exception) {}
    }

    private fun loadWeather() {
        lifecycleScope.launch {
            val result = weatherService.fetchWeather()
            binding.textWeather.text = result.displayText
            updateWeatherIcon(result.weatherCode)
        }
    }

    private fun updateWeatherIcon(code: Int) {
        val iconRes = when (code) {
            0 -> R.drawable.ic_weather_sunny
            1, 2, 3 -> R.drawable.ic_weather_cloudy
            51, 53, 55, 61, 63, 65, 80, 81, 82 -> R.drawable.ic_weather_drizzle
            else -> R.drawable.ic_weather_drizzle
        }
        binding.iconWeather.setImageResource(iconRes)
    }

    private fun launchPackageOrPrompt(packageName: String, notFoundMessage: String, rebindKey: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        } else {
            Toast.makeText(this, notFoundMessage, Toast.LENGTH_LONG).show()
            openAppPicker(rebindKey)
        }
    }

    private fun openAppPicker(rebindKey: String) {
        targetRebindKey = rebindKey
        val intent = Intent(this, AppDrawerActivity::class.java).apply {
            action = AppDrawerActivity.ACTION_PICK_APP
        }
        appPickerLauncher.launch(intent)
    }

    private fun hideSystemUI() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // As a Home Launcher, do not exit on back press
    }
}
