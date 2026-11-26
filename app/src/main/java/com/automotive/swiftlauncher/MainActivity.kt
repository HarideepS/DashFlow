package com.automotive.swiftlauncher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.automotive.swiftlauncher.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferenceHelper
    private lateinit var weatherService: WeatherService
    private lateinit var brightnessHelper: BrightnessHelper
    private lateinit var audioManager: AudioManager
    private lateinit var mediaSessionHelper: MediaSessionHelper
    private lateinit var speedometerHelper: SpeedometerHelper

    private var targetRebindKey: String? = null
    private var lastNonZeroVolume: Int = 8
    private var lastKnownSpeedKmh: Int = -1
    private var pendingDialogDismiss: (() -> Unit)? = null

    // Activity Result Launcher for re-binding cards or picking boot auto-launch app
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
                    PreferenceHelper.KEY_BOOT_LAUNCH_PKG -> {
                        prefs.bootLaunchPackage = selectedPkg
                        prefs.bootLaunchLabel = selectedLabel ?: selectedPkg
                        Toast.makeText(this, "Boot auto-launch set to: $selectedLabel", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Activity Result Launcher for picking custom car image from gallery
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            saveCustomCarImage(uri)
        }
    }

    // Activity Result Launcher for GPS speedometer location permission
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            speedometerHelper.start()
            Toast.makeText(this, "GPS Speedometer active", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Location permission needed for live GPS speed", Toast.LENGTH_SHORT).show()
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

        mediaSessionHelper = MediaSessionHelper(this) { state ->
            runOnUiThread { renderMediaState(state) }
        }
        speedometerHelper = SpeedometerHelper(this) { kmh, _ ->
            lastKnownSpeedKmh = kmh
            runOnUiThread { renderSpeedometer() }
        }

        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (currentVol > 0) {
            lastNonZeroVolume = currentVol
        }

        setupClockAndDate()
        setupTelemetryAndMedia()
        setupVehicleDisplay()
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
        applyVehicleCustomizations()
        applyTelemetrySettings()

        if (prefs.autoNightDimming) {
            brightnessHelper.applySunBasedDimming(weatherService.isNightTimeNow())
        }

        mediaSessionHelper.start()
        if (prefs.showSpeedometer && speedometerHelper.hasLocationPermission()) {
            speedometerHelper.start()
        }

        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction("android.media.VOLUME_CHANGED_ACTION")
        }
        registerReceiver(systemStateReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        mediaSessionHelper.stop()
        speedometerHelper.stop()
        try {
            unregisterReceiver(systemStateReceiver)
        } catch (_: Exception) {}
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Intercept standard steering-wheel / hardware media keys while on the launcher
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                mediaSessionHelper.togglePlayPause()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                mediaSessionHelper.skipToNext()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                mediaSessionHelper.skipToPrevious()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun setupClockAndDate() {
        applyClockFormat(prefs.is24HourFormat)
        updateDateText()

        // Tapping the time / date area toggles 12h vs 24h format
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

    private fun setupTelemetryAndMedia() {
        binding.speedometerContainer.setOnClickListener {
            if (!speedometerHelper.hasLocationPermission()) {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            } else {
                prefs.speedUnitMph = !prefs.speedUnitMph
                renderSpeedometer()
                val unit = if (prefs.speedUnitMph) "MPH" else "KM/H"
                Toast.makeText(this, "Speed unit: $unit", Toast.LENGTH_SHORT).show()
            }
        }

        binding.mediaTrackInfo.setOnClickListener {
            mediaSessionHelper.openPlayerApp()
        }
        binding.btnMediaPrev.setOnClickListener {
            mediaSessionHelper.skipToPrevious()
        }
        binding.btnMediaPlayPause.setOnClickListener {
            mediaSessionHelper.togglePlayPause()
        }
        binding.btnMediaNext.setOnClickListener {
            mediaSessionHelper.skipToNext()
        }
    }

    private fun applyTelemetrySettings() {
        binding.speedometerContainer.visibility = if (prefs.showSpeedometer) View.VISIBLE else View.GONE
        renderSpeedometer()
        applyAccentColor()
    }

    private fun applyAccentColor() {
        val parsedColor = try {
            Color.parseColor(prefs.accentColor)
        } catch (_: Exception) {
            Color.parseColor(PreferenceHelper.DEFAULT_ACCENT_COLOR)
        }
        binding.textDayDate.setTextColor(parsedColor)
        binding.iconCustomizeVehicle.setColorFilter(parsedColor)
        binding.btnMediaPlayPause.setColorFilter(parsedColor)
    }

    private fun renderSpeedometer() {
        binding.textSpeedUnit.text = if (prefs.speedUnitMph) "MPH" else "KM/H"
        if (!speedometerHelper.hasLocationPermission() || lastKnownSpeedKmh < 0) {
            binding.textSpeedValue.text = "--"
        } else {
            val displaySpeed = if (prefs.speedUnitMph) {
                (lastKnownSpeedKmh * 0.621371f).roundToInt()
            } else {
                lastKnownSpeedKmh
            }
            binding.textSpeedValue.text = displaySpeed.toString()
        }
    }

    private fun renderMediaState(state: MediaUiState) {
        if (!state.hasActiveSession) {
            binding.mediaControllerBar.visibility = View.GONE
            return
        }
        binding.mediaControllerBar.visibility = View.VISIBLE
        binding.textMediaTitle.text = state.title
        binding.textMediaArtist.text = state.artist.ifEmpty { "Now Playing" }
        binding.textMediaArtist.visibility = View.VISIBLE
        binding.btnMediaPlayPause.setImageResource(
            if (state.isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play
        )
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

    private fun setupVehicleDisplay() {
        applyVehicleCustomizations()

        // Tapping the car or vehicle info badge opens the Vehicle Customizer
        binding.imageCar.setOnClickListener {
            showVehicleSettingsDialog()
        }
        binding.vehicleInfoContainer.setOnClickListener {
            showVehicleSettingsDialog()
        }
    }

    private fun applyVehicleCustomizations() {
        // 1. Vehicle Model Name
        binding.textVehicleName.text = prefs.vehicleName.uppercase(Locale.getDefault())

        // 2. License Plate / Tag
        val plate = prefs.licensePlate.trim()
        if (plate.isNotEmpty()) {
            binding.textLicensePlate.visibility = View.VISIBLE
            binding.textLicensePlate.text = plate.uppercase(Locale.getDefault())
        } else {
            binding.textLicensePlate.visibility = View.GONE
        }

        // 3. Display Scaling
        binding.imageCar.scaleX = prefs.carScale
        binding.imageCar.scaleY = prefs.carScale

        // 4. Car Visual (Custom Image vs Built-in Presets)
        if (prefs.carPreset == PreferenceHelper.PRESET_CUSTOM && prefs.customCarPath.isNotBlank()) {
            val file = File(prefs.customCarPath)
            if (file.exists()) {
                val bitmap = loadCustomCarBitmap(file.absolutePath)
                if (bitmap != null) {
                    binding.imageCar.setImageBitmap(bitmap)
                    return
                }
            }
        }

        val resId = when (prefs.carPreset) {
            PreferenceHelper.PRESET_SEDAN -> R.drawable.car_preset_sedan
            PreferenceHelper.PRESET_SUV -> R.drawable.car_preset_suv
            PreferenceHelper.PRESET_SPORTS -> R.drawable.car_preset_sports
            PreferenceHelper.PRESET_EV -> R.drawable.car_preset_ev
            else -> R.drawable.car_swift
        }
        binding.imageCar.setImageResource(resId)
    }

    private fun loadCustomCarBitmap(path: String): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            var sampleSize = 1
            while (options.outWidth / sampleSize > 1024 || options.outHeight / sampleSize > 600) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(path, decodeOptions)
        } catch (_: Exception) {
            null
        }
    }

    private fun saveCustomCarImage(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val customFile = File(filesDir, "custom_car_display.png")
                FileOutputStream(customFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                prefs.carPreset = PreferenceHelper.PRESET_CUSTOM
                prefs.customCarPath = customFile.absolutePath
                applyVehicleCustomizations()
                Toast.makeText(this, "Custom vehicle photo applied!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not load image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showVehicleSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_launcher_settings, null)
        val editVehicleName = dialogView.findViewById<EditText>(R.id.editVehicleName)
        val editLicensePlate = dialogView.findViewById<EditText>(R.id.editLicensePlate)
        val editCity = dialogView.findViewById<EditText>(R.id.editCity)
        val textCarVisualStatus = dialogView.findViewById<TextView>(R.id.textCarVisualStatus)
        val btnClearCustomImage = dialogView.findViewById<Button>(R.id.btnClearCustomImage)
        val checkShowSpeedometer = dialogView.findViewById<CheckBox>(R.id.checkShowSpeedometer)
        val checkSpeedMph = dialogView.findViewById<CheckBox>(R.id.checkSpeedMph)
        val checkAutoDimming = dialogView.findViewById<CheckBox>(R.id.checkAutoDimming)
        val btnEnableMediaAccess = dialogView.findViewById<Button>(R.id.btnEnableMediaAccess)
        val textBootLaunchApp = dialogView.findViewById<TextView>(R.id.textBootLaunchApp)

        var selectedPreset = prefs.carPreset
        var selectedScale = prefs.carScale
        var selectedAccent = prefs.accentColor

        editVehicleName.setText(prefs.vehicleName)
        editLicensePlate.setText(prefs.licensePlate)
        editCity.setText(prefs.cityName)
        checkShowSpeedometer.isChecked = prefs.showSpeedometer
        checkSpeedMph.isChecked = prefs.speedUnitMph
        checkAutoDimming.isChecked = prefs.autoNightDimming

        btnEnableMediaAccess.text = if (mediaSessionHelper.hasNotificationAccess()) {
            "🎵 Media Bar Connected (Tap to Configure)"
        } else {
            "🎵 Enable Headless Media Bar (Notification Access)"
        }
        textBootLaunchApp.text = "Auto-Launch on Cold Boot: ${prefs.bootLaunchLabel}"

        fun updateVisualStatusUI() {
            if (selectedPreset == PreferenceHelper.PRESET_CUSTOM && prefs.customCarPath.isNotBlank()) {
                textCarVisualStatus.text = "Active visual: Custom Gallery Photo"
                btnClearCustomImage.visibility = View.VISIBLE
            } else {
                val label = when (selectedPreset) {
                    PreferenceHelper.PRESET_SEDAN -> "Sedan (Sport Silhouette)"
                    PreferenceHelper.PRESET_SUV -> "SUV (Crossover Silhouette)"
                    PreferenceHelper.PRESET_SPORTS -> "Sports GT (Supercar Silhouette)"
                    PreferenceHelper.PRESET_EV -> "Cyber EV (Electric Silhouette)"
                    else -> "Swift (OEM Render)"
                }
                textCarVisualStatus.text = "Active visual: $label"
                btnClearCustomImage.visibility = View.GONE
            }
        }
        updateVisualStatusUI()

        dialogView.findViewById<Button>(R.id.btnPresetSwift).setOnClickListener {
            selectedPreset = PreferenceHelper.PRESET_SWIFT
            updateVisualStatusUI()
        }
        dialogView.findViewById<Button>(R.id.btnPresetSedan).setOnClickListener {
            selectedPreset = PreferenceHelper.PRESET_SEDAN
            updateVisualStatusUI()
        }
        dialogView.findViewById<Button>(R.id.btnPresetSuv).setOnClickListener {
            selectedPreset = PreferenceHelper.PRESET_SUV
            updateVisualStatusUI()
        }
        dialogView.findViewById<Button>(R.id.btnPresetSports).setOnClickListener {
            selectedPreset = PreferenceHelper.PRESET_SPORTS
            updateVisualStatusUI()
        }
        dialogView.findViewById<Button>(R.id.btnPresetEv).setOnClickListener {
            selectedPreset = PreferenceHelper.PRESET_EV
            updateVisualStatusUI()
        }

        dialogView.findViewById<Button>(R.id.btnPickCustomImage).setOnClickListener {
            pendingDialogDismiss?.invoke()
            imagePickerLauncher.launch("image/*")
        }

        btnClearCustomImage.setOnClickListener {
            selectedPreset = PreferenceHelper.PRESET_SWIFT
            prefs.customCarPath = ""
            updateVisualStatusUI()
        }

        dialogView.findViewById<Button>(R.id.btnSizeSmall).setOnClickListener {
            selectedScale = 0.85f
            Toast.makeText(this, "Scale: 85% Compact", Toast.LENGTH_SHORT).show()
        }
        dialogView.findViewById<Button>(R.id.btnSizeNormal).setOnClickListener {
            selectedScale = 1.0f
            Toast.makeText(this, "Scale: 100% Default", Toast.LENGTH_SHORT).show()
        }
        dialogView.findViewById<Button>(R.id.btnSizeLarge).setOnClickListener {
            selectedScale = 1.15f
            Toast.makeText(this, "Scale: 115% Hero", Toast.LENGTH_SHORT).show()
        }

        // Accent Color Selection
        val accentButtons = mapOf(
            R.id.btnAccentBlue to "#4D7DF0",
            R.id.btnAccentCyan to "#00E5FF",
            R.id.btnAccentEmerald to "#10B981",
            R.id.btnAccentRed to "#EF4444",
            R.id.btnAccentAmber to "#F59E0B"
        )
        accentButtons.forEach { (btnId, hex) ->
            dialogView.findViewById<Button>(btnId).setOnClickListener {
                selectedAccent = hex
                Toast.makeText(this, "Accent color selected", Toast.LENGTH_SHORT).show()
            }
        }

        btnEnableMediaAccess.setOnClickListener {
            mediaSessionHelper.openNotificationAccessSettings()
        }

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
            .setView(dialogView)
            .create()

        pendingDialogDismiss = { dialog.dismiss() }

        dialogView.findViewById<Button>(R.id.btnPickBootApp).setOnClickListener {
            dialog.dismiss()
            openAppPicker(PreferenceHelper.KEY_BOOT_LAUNCH_PKG)
        }
        dialogView.findViewById<Button>(R.id.btnClearBootApp).setOnClickListener {
            prefs.bootLaunchPackage = ""
            prefs.bootLaunchLabel = "None (Stay on DashFlow)"
            textBootLaunchApp.text = "Auto-Launch on Cold Boot: None (Stay on DashFlow)"
        }

        dialogView.findViewById<Button>(R.id.btnCancelSettings).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnSaveSettings).setOnClickListener {
            val newName = editVehicleName.text.toString().trim()
            val newPlate = editLicensePlate.text.toString().trim()
            val newCity = editCity.text.toString().trim()

            prefs.vehicleName = if (newName.isNotBlank()) newName else PreferenceHelper.DEFAULT_VEHICLE_NAME
            prefs.licensePlate = newPlate
            prefs.carPreset = selectedPreset
            prefs.carScale = selectedScale
            prefs.accentColor = selectedAccent
            prefs.showSpeedometer = checkShowSpeedometer.isChecked
            prefs.speedUnitMph = checkSpeedMph.isChecked
            prefs.autoNightDimming = checkAutoDimming.isChecked

            if (prefs.showSpeedometer && !speedometerHelper.hasLocationPermission()) {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            } else if (prefs.showSpeedometer) {
                speedometerHelper.start()
            } else {
                speedometerHelper.stop()
            }

            if (prefs.autoNightDimming) {
                brightnessHelper.applySunBasedDimming(weatherService.isNightTimeNow())
            }

            val cityChanged = newCity.isNotBlank() && !newCity.equals(prefs.cityName, ignoreCase = true)
            if (cityChanged) {
                prefs.cityName = newCity
                loadWeather()
            }

            applyVehicleCustomizations()
            applyTelemetrySettings()
            dialog.dismiss()
            Toast.makeText(this, "Vehicle settings applied!", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
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

        // 4. Settings (Rectangle Tile) - tap opens system settings, long-press opens vehicle customizer!
        binding.cardSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
        binding.cardSettings.setOnLongClickListener {
            showVehicleSettingsDialog()
            true
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
            val loc = if (prefs.cityName == PreferenceHelper.DEFAULT_CITY) {
                speedometerHelper.getLastKnownLocation()
            } else {
                null
            }
            val result = weatherService.fetchWeather(loc?.latitude, loc?.longitude)
            binding.textWeather.text = result.displayText
            updateWeatherIcon(result.weatherCode)
            if (prefs.autoNightDimming) {
                brightnessHelper.applySunBasedDimming(weatherService.isNightTimeNow())
            }
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
