package com.automotive.swiftlauncher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
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
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.automotive.swiftlauncher.databinding.ActivityMainBinding
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
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
    private var targetTileSlot: Int? = null
    private var reopenTileCustomizerAfterPick: Boolean = false
    private var lastNonZeroVolume: Int = 8
    private var lastKnownSpeedKmh: Int = -1
    private var pendingDialogDismiss: (() -> Unit)? = null

    // Activity Result Launcher for re-binding tiles or picking boot auto-launch app
    private val appPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val selectedPkg = result.data?.getStringExtra(AppDrawerActivity.EXTRA_SELECTED_PACKAGE)
            val selectedLabel = result.data?.getStringExtra(AppDrawerActivity.EXTRA_SELECTED_LABEL)
            if (selectedPkg != null) {
                val slot = targetTileSlot
                if (slot != null) {
                    val resolvedLabel = selectedLabel ?: selectedPkg
                    targetTileSlot = null
                    if (reopenTileCustomizerAfterPick) {
                        reopenTileCustomizerAfterPick = false
                        showTileCustomizerDialog(
                            slot = slot,
                            overridePkg = selectedPkg,
                            overrideLabel = resolvedLabel,
                            overrideIconMode = PreferenceHelper.ICON_MODE_APP
                        )
                    } else {
                        prefs.saveTileConfig(
                            slot,
                            TileConfig(selectedPkg, resolvedLabel, PreferenceHelper.ICON_MODE_APP)
                        )
                        renderCustomTiles()
                        Toast.makeText(this, "Tile mapped to: $resolvedLabel", Toast.LENGTH_SHORT).show()
                    }
                } else if (targetRebindKey == PreferenceHelper.KEY_BOOT_LAUNCH_PKG) {
                    targetRebindKey = null
                    prefs.bootLaunchPackage = selectedPkg
                    prefs.bootLaunchLabel = selectedLabel ?: selectedPkg
                    Toast.makeText(this, "Boot auto-launch set to: $selectedLabel", Toast.LENGTH_SHORT).show()
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

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(PreferenceHelper.wrapWithCustomDpi(newBase))
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
        renderCustomTiles()

        if (prefs.autoNightDimming) {
            brightnessHelper.applySunBasedDimming(weatherService.isNightTimeNow())
        }

        mediaSessionHelper.start()
        if (prefs.showSpeedometer && speedometerHelper.hasLocationPermission()) {
            speedometerHelper.start()
        }
        syncFloatingMuteOverlay(promptIfMissing = !prefs.floatingMutePrompted)

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

    private fun parseAccentColor(hex: String = prefs.accentColor): Int {
        return try {
            Color.parseColor(hex)
        } catch (_: Exception) {
            Color.parseColor(PreferenceHelper.DEFAULT_ACCENT_COLOR)
        }
    }

    private fun createTintedPillDrawable(
        accentColor: Int,
        fillAlpha: Int = 44,
        strokeAlpha: Int = 255,
        strokeDp: Float = 1.5f
    ): Drawable {
        val fillColor = Color.argb(
            fillAlpha,
            Color.red(accentColor),
            Color.green(accentColor),
            Color.blue(accentColor)
        )
        val strokeColor = Color.argb(
            strokeAlpha,
            Color.red(accentColor),
            Color.green(accentColor),
            Color.blue(accentColor)
        )
        val radiusPx = 14f * resources.displayMetrics.density
        val strokePx = (strokeDp * resources.displayMetrics.density).roundToInt()
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(fillColor)
            setStroke(strokePx, strokeColor)
        }
        val rippleColor = ColorStateList.valueOf(Color.argb(38, 255, 255, 255))
        return RippleDrawable(rippleColor, shape, null)
    }

    private fun applyAccentColor() {
        val parsedColor = parseAccentColor()
        binding.textDayDate.setTextColor(parsedColor)
        binding.textSpeedUnit.setTextColor(parsedColor)
        binding.speedometerContainer.background = createTintedPillDrawable(
            parsedColor,
            fillAlpha = 20,
            strokeAlpha = 120,
            strokeDp = 1f
        )
        binding.btnMediaPlayPause.setColorFilter(parsedColor)
        binding.chevronSettings.setColorFilter(parsedColor)
        binding.chevronAllApps.setColorFilter(parsedColor)
        updateWifiState()
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
        val checkFloatingMute = dialogView.findViewById<CheckBox>(R.id.checkFloatingMute)
        val seekUiScale = dialogView.findViewById<SeekBar>(R.id.seekUiScale)
        val textUiScaleValue = dialogView.findViewById<TextView>(R.id.textUiScaleValue)
        val btnResetUiScale = dialogView.findViewById<Button>(R.id.btnResetUiScale)
        val btnEnableMediaAccess = dialogView.findViewById<Button>(R.id.btnEnableMediaAccess)
        val textBootLaunchApp = dialogView.findViewById<TextView>(R.id.textBootLaunchApp)

        var selectedPreset = prefs.carPreset
        var selectedScale = prefs.carScale
        var selectedUiScale = prefs.uiScale.coerceIn(0.70f, 1.30f)
        var selectedAccent = prefs.accentColor

        fun updateUiScaleLabel() {
            val pct = (selectedUiScale * 100f).roundToInt()
            textUiScaleValue.text = if (abs(selectedUiScale - 1.0f) < 0.01f) {
                "Whole-Launcher DPI Scale: $pct% (Native)"
            } else {
                "Whole-Launcher DPI Scale: $pct%"
            }
        }
        seekUiScale.progress = ((selectedUiScale - 0.70f) / 0.05f).roundToInt().coerceIn(0, 12)
        updateUiScaleLabel()

        seekUiScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                selectedUiScale = 0.70f + (progress * 0.05f)
                updateUiScaleLabel()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnResetUiScale.setOnClickListener {
            selectedUiScale = 1.0f
            seekUiScale.progress = 6
            updateUiScaleLabel()
        }

        editVehicleName.setText(prefs.vehicleName)
        editLicensePlate.setText(prefs.licensePlate)
        editCity.setText(prefs.cityName)
        checkShowSpeedometer.isChecked = prefs.showSpeedometer
        checkSpeedMph.isChecked = prefs.speedUnitMph
        checkAutoDimming.isChecked = prefs.autoNightDimming
        checkFloatingMute.isChecked = prefs.floatingMuteEnabled

        btnEnableMediaAccess.text = if (mediaSessionHelper.hasNotificationAccess()) {
            "🎵 Media Bar Connected (Tap to Configure)"
        } else {
            "🎵 Enable Headless Media Bar (Notification Access)"
        }
        textBootLaunchApp.text = "Auto-Launch on Cold Boot: ${prefs.bootLaunchLabel}"

        val sectionHeaders = listOf(
            R.id.sectionHeader1,
            R.id.sectionHeader2,
            R.id.sectionHeader3,
            R.id.sectionHeader4,
            R.id.sectionHeader5
        ).map { dialogView.findViewById<TextView>(it) }
        val btnPickCustomImage = dialogView.findViewById<Button>(R.id.btnPickCustomImage)
        val btnSaveSettings = dialogView.findViewById<Button>(R.id.btnSaveSettings)

        val presetButtons = mapOf(
            PreferenceHelper.PRESET_HATCHBACK to dialogView.findViewById<Button>(R.id.btnPresetHatchback),
            PreferenceHelper.PRESET_SEDAN to dialogView.findViewById<Button>(R.id.btnPresetSedan),
            PreferenceHelper.PRESET_SUV to dialogView.findViewById<Button>(R.id.btnPresetSuv),
            PreferenceHelper.PRESET_SPORTS to dialogView.findViewById<Button>(R.id.btnPresetSports),
            PreferenceHelper.PRESET_EV to dialogView.findViewById<Button>(R.id.btnPresetEv)
        )
        val sizeButtons = listOf(
            0.85f to dialogView.findViewById<Button>(R.id.btnSizeSmall),
            1.0f to dialogView.findViewById<Button>(R.id.btnSizeNormal),
            1.15f to dialogView.findViewById<Button>(R.id.btnSizeLarge)
        )
        val accentButtons = mapOf(
            R.id.btnAccentBlue to "#4D7DF0",
            R.id.btnAccentCyan to "#00E5FF",
            R.id.btnAccentEmerald to "#10B981",
            R.id.btnAccentRed to "#EF4444",
            R.id.btnAccentAmber to "#F59E0B"
        )

        fun updateDialogSelectionsAndTheme() {
            val accentColor = parseAccentColor(selectedAccent)
            val accentStateList = ColorStateList.valueOf(accentColor)
            val subtleFill = ColorStateList.valueOf(
                Color.argb(36, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            )
            val defaultStroke = ColorStateList.valueOf(Color.parseColor("#2F3A50"))
            val transparentFill = ColorStateList.valueOf(Color.TRANSPARENT)

            sectionHeaders.forEach { it.setTextColor(accentColor) }
            checkShowSpeedometer.buttonTintList = accentStateList
            checkSpeedMph.buttonTintList = accentStateList
            checkAutoDimming.buttonTintList = accentStateList
            checkFloatingMute.buttonTintList = accentStateList
            seekUiScale.progressTintList = accentStateList
            seekUiScale.thumbTintList = accentStateList
            btnResetUiScale.setTextColor(accentColor)
            btnSaveSettings.backgroundTintList = accentStateList
            (btnPickCustomImage as? MaterialButton)?.apply {
                setTextColor(accentColor)
                strokeColor = accentStateList
            }
            (btnEnableMediaAccess as? MaterialButton)?.apply {
                setTextColor(accentColor)
                strokeColor = accentStateList
            }

            val normalizedPreset = if (selectedPreset == "swift") PreferenceHelper.PRESET_HATCHBACK else selectedPreset
            presetButtons.forEach { (key, btn) ->
                val active = (key == normalizedPreset)
                (btn as? MaterialButton)?.apply {
                    strokeColor = if (active) accentStateList else defaultStroke
                    backgroundTintList = if (active) subtleFill else transparentFill
                }
            }

            sizeButtons.forEach { (scale, btn) ->
                val active = kotlin.math.abs(selectedScale - scale) < 0.05f
                (btn as? MaterialButton)?.apply {
                    strokeColor = if (active) accentStateList else defaultStroke
                    backgroundTintList = if (active) subtleFill else transparentFill
                }
            }

            accentButtons.forEach { (btnId, hex) ->
                val btn = dialogView.findViewById<Button>(btnId)
                val btnColor = parseAccentColor(hex)
                val active = hex.equals(selectedAccent, ignoreCase = true)
                (btn as? MaterialButton)?.apply {
                    strokeColor = ColorStateList.valueOf(btnColor)
                    backgroundTintList = if (active) {
                        ColorStateList.valueOf(
                            Color.argb(56, Color.red(btnColor), Color.green(btnColor), Color.blue(btnColor))
                        )
                    } else {
                        transparentFill
                    }
                }
            }
        }

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
                    else -> "Hatchback"
                }
                textCarVisualStatus.text = "Active visual: $label"
                btnClearCustomImage.visibility = View.GONE
            }
            updateDialogSelectionsAndTheme()
        }
        updateVisualStatusUI()

        presetButtons.forEach { (presetKey, btn) ->
            btn.setOnClickListener {
                selectedPreset = presetKey
                updateVisualStatusUI()
            }
        }

        btnPickCustomImage.setOnClickListener {
            pendingDialogDismiss?.invoke()
            imagePickerLauncher.launch("image/*")
        }

        btnClearCustomImage.setOnClickListener {
            selectedPreset = PreferenceHelper.PRESET_HATCHBACK
            prefs.customCarPath = ""
            updateVisualStatusUI()
        }

        sizeButtons.forEach { (scale, btn) ->
            btn.setOnClickListener {
                selectedScale = scale
                updateDialogSelectionsAndTheme()
            }
        }

        accentButtons.forEach { (btnId, hex) ->
            dialogView.findViewById<Button>(btnId).setOnClickListener {
                selectedAccent = hex
                updateDialogSelectionsAndTheme()
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

            val uiScaleChanged = abs(prefs.uiScale - selectedUiScale) >= 0.01f
            prefs.vehicleName = if (newName.isNotBlank()) newName else PreferenceHelper.DEFAULT_VEHICLE_NAME
            prefs.licensePlate = newPlate
            prefs.carPreset = selectedPreset
            prefs.carScale = selectedScale
            prefs.uiScale = selectedUiScale
            prefs.accentColor = selectedAccent
            prefs.showSpeedometer = checkShowSpeedometer.isChecked
            prefs.speedUnitMph = checkSpeedMph.isChecked
            prefs.autoNightDimming = checkAutoDimming.isChecked
            prefs.floatingMuteEnabled = checkFloatingMute.isChecked

            if (prefs.floatingMuteEnabled && !Settings.canDrawOverlays(this)) {
                openOverlayPermissionSettings()
            } else {
                FloatingMuteService.sync(this)
            }

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
            if (uiScaleChanged) {
                Toast.makeText(this, "Applying ${(selectedUiScale * 100).roundToInt()}% DPI scale...", Toast.LENGTH_SHORT).show()
                recreate()
            } else {
                Toast.makeText(this, "Vehicle settings applied!", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
        val maxDialogWidth = (resources.displayMetrics.density * 680).toInt()
        val targetWidth = minOf((resources.displayMetrics.widthPixels * 0.84f).toInt(), maxDialogWidth)
        val maxHeight = (resources.displayMetrics.heightPixels * 0.90f).toInt()
        dialog.window?.setLayout(targetWidth, maxHeight)
    }

    private fun setupCards() {
        val tileSlots = listOf(
            Triple(1, binding.cardAndroidAuto, binding.iconCardAA to binding.textCardAA),
            Triple(2, binding.cardCarPlay, binding.iconCardCarPlay to binding.textCardCarPlay),
            Triple(3, binding.cardYouTube, binding.iconCardYouTube to binding.textCardYouTube)
        )

        tileSlots.forEach { (slot, cardView, _) ->
            cardView.setOnClickListener {
                val config = prefs.getTileConfig(slot)
                launchTileOrPrompt(slot, config)
            }
            cardView.setOnLongClickListener {
                showTileCustomizerDialog(slot)
                true
            }
        }
        renderCustomTiles()

        // Universal Rectangle Tiles: System Settings (long-press opens Dashboard Customizer) & All Apps
        binding.cardSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
        binding.cardSettings.setOnLongClickListener {
            showVehicleSettingsDialog()
            true
        }

        binding.cardAllApps.setOnClickListener {
            val intent = Intent(this, AppDrawerActivity::class.java)
            startActivity(intent)
        }
    }

    private fun renderCustomTiles() {
        val tileViews = listOf(
            Triple(1, binding.iconCardAA, binding.textCardAA),
            Triple(2, binding.iconCardCarPlay, binding.textCardCarPlay),
            Triple(3, binding.iconCardYouTube, binding.textCardYouTube)
        )
        tileViews.forEach { (slot, iconView, textView) ->
            val config = prefs.getTileConfig(slot)
            textView.text = config.label
            iconView.clearColorFilter()
            iconView.imageTintList = null

            when (config.iconMode) {
                PreferenceHelper.ICON_MODE_AA -> {
                    iconView.setImageResource(R.drawable.ic_aa_custom)
                }
                PreferenceHelper.ICON_MODE_CARPLAY -> {
                    iconView.setImageResource(R.drawable.ic_carplay_custom)
                }
                PreferenceHelper.ICON_MODE_YOUTUBE -> {
                    iconView.setImageResource(R.drawable.ic_youtube)
                    iconView.setColorFilter(Color.parseColor("#FF0000"))
                }
                else -> {
                    try {
                        val appIcon = packageManager.getApplicationIcon(config.packageName)
                        iconView.setImageDrawable(appIcon)
                    } catch (_: Exception) {
                        iconView.setImageResource(R.drawable.ic_apps)
                        iconView.setColorFilter(Color.parseColor("#9DA8B9"))
                    }
                }
            }
        }
    }

    private fun showTileCustomizerDialog(
        slot: Int,
        overridePkg: String? = null,
        overrideLabel: String? = null,
        overrideIconMode: String? = null
    ) {
        val current = prefs.getTileConfig(slot)
        var selectedPkg = overridePkg ?: current.packageName
        var selectedIconMode = overrideIconMode ?: current.iconMode

        val dialogView = layoutInflater.inflate(R.layout.dialog_customize_tile, null)
        val textTitle = dialogView.findViewById<TextView>(R.id.textTileDialogTitle)
        val textBoundAppInfo = dialogView.findViewById<TextView>(R.id.textBoundAppInfo)
        val editTileLabel = dialogView.findViewById<EditText>(R.id.editTileLabel)
        val btnChangeTileApp = dialogView.findViewById<Button>(R.id.btnChangeTileApp)
        val btnResetTile = dialogView.findViewById<Button>(R.id.btnResetTile)
        val btnCancelTile = dialogView.findViewById<Button>(R.id.btnCancelTile)
        val btnSaveTile = dialogView.findViewById<Button>(R.id.btnSaveTile)

        val sectionHeaders = listOf(
            R.id.tileSectionApp,
            R.id.tileSectionLabel,
            R.id.tileSectionIcon
        ).map { dialogView.findViewById<TextView>(it) }

        val iconButtons = mapOf(
            PreferenceHelper.ICON_MODE_APP to dialogView.findViewById<Button>(R.id.btnIconModeApp),
            PreferenceHelper.ICON_MODE_AA to dialogView.findViewById<Button>(R.id.btnIconModeAA),
            PreferenceHelper.ICON_MODE_CARPLAY to dialogView.findViewById<Button>(R.id.btnIconModeCarPlay),
            PreferenceHelper.ICON_MODE_YOUTUBE to dialogView.findViewById<Button>(R.id.btnIconModeYouTube)
        )

        textTitle.text = "Customize Tile #$slot"
        editTileLabel.setText(overrideLabel ?: current.label)

        fun resolveAppDisplayName(pkg: String): String {
            return try {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                "Not installed"
            }
        }
        textBoundAppInfo.text = "${resolveAppDisplayName(selectedPkg)} ($selectedPkg)"

        val accentColor = parseAccentColor()
        val accentStateList = ColorStateList.valueOf(accentColor)
        val subtleFill = ColorStateList.valueOf(
            Color.argb(36, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        )
        val defaultStroke = ColorStateList.valueOf(Color.parseColor("#2F3A50"))
        val transparentFill = ColorStateList.valueOf(Color.TRANSPARENT)

        sectionHeaders.forEach { it.setTextColor(accentColor) }
        btnSaveTile.backgroundTintList = accentStateList
        (btnChangeTileApp as? MaterialButton)?.apply {
            setTextColor(accentColor)
            strokeColor = accentStateList
        }

        fun updateIconModeUI() {
            iconButtons.forEach { (mode, btn) ->
                val active = mode == selectedIconMode
                (btn as? MaterialButton)?.apply {
                    strokeColor = if (active) accentStateList else defaultStroke
                    backgroundTintList = if (active) subtleFill else transparentFill
                }
            }
        }
        updateIconModeUI()

        iconButtons.forEach { (mode, btn) ->
            btn.setOnClickListener {
                selectedIconMode = mode
                updateIconModeUI()
            }
        }

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
            .setView(dialogView)
            .create()

        btnChangeTileApp.setOnClickListener {
            dialog.dismiss()
            openTileAppPicker(slot, reopenCustomizer = true)
        }

        btnResetTile.setOnClickListener {
            val def = prefs.resetTileConfig(slot)
            renderCustomTiles()
            dialog.dismiss()
            Toast.makeText(this, "Tile #$slot reset to ${def.label}", Toast.LENGTH_SHORT).show()
        }

        btnCancelTile.setOnClickListener {
            dialog.dismiss()
        }

        btnSaveTile.setOnClickListener {
            val newLabel = editTileLabel.text.toString().trim().ifEmpty {
                PreferenceHelper.defaultTileConfig(slot).label
            }
            prefs.saveTileConfig(slot, TileConfig(selectedPkg, newLabel, selectedIconMode))
            renderCustomTiles()
            dialog.dismiss()
            Toast.makeText(this, "Tile updated: $newLabel", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
        val maxDialogWidth = (resources.displayMetrics.density * 560).toInt()
        val targetWidth = minOf((resources.displayMetrics.widthPixels * 0.80f).toInt(), maxDialogWidth)
        val maxHeight = (resources.displayMetrics.heightPixels * 0.90f).toInt()
        dialog.window?.setLayout(targetWidth, maxHeight)
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

    private fun syncFloatingMuteOverlay(promptIfMissing: Boolean) {
        FloatingMuteService.sync(this)
        if (prefs.floatingMuteEnabled &&
            !Settings.canDrawOverlays(this) &&
            promptIfMissing
        ) {
            prefs.floatingMutePrompted = true
            val builder = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Enable Floating Mute Pill?")
                .setMessage(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        "Allow 'Display over other apps' so a draggable Mute button stays accessible over Android Auto, CarPlay, and YouTube.\n\nOn Android 13+, if blocked as a restricted setting: open App Info → ⋮ → Allow restricted settings first."
                    } else {
                        "Allow 'Display over other apps' so a small draggable Mute button stays accessible over Android Auto, CarPlay, and YouTube."
                    }
                )
                .setPositiveButton("Allow") { _, _ -> openOverlayPermissionSettings() }
                .setNegativeButton("Not Now", null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                builder.setNeutralButton("App Info") { _, _ -> openAppInfoSettings() }
            }
            builder.show()
        }
    }

    private fun openAppInfoSettings() {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: Exception) {}
    }

    private fun openOverlayPermissionSettings() {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Enable 'Display over other apps' in Settings", Toast.LENGTH_LONG).show()
        }
    }

    private fun toggleVolumeMute() {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (current > 0) {
            lastNonZeroVolume = current
            prefs.lastMediaVolume = current
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
            Toast.makeText(this, "Muted", Toast.LENGTH_SHORT).show()
        } else {
            val saved = if (prefs.lastMediaVolume > 0) prefs.lastMediaVolume else lastNonZeroVolume
            val target = if (saved > 0) saved else (audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * 0.6).toInt()
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
            val accentColor = parseAccentColor()
            if (isEnabled) {
                binding.btnWifi.background = createTintedPillDrawable(accentColor)
                binding.iconWifi.setColorFilter(accentColor)
            } else {
                binding.btnWifi.setBackgroundResource(R.drawable.bg_toggle_pill)
                binding.iconWifi.setColorFilter(ContextCompat.getColor(this, R.color.text_primary))
            }
        } catch (_: Exception) {}
    }

    private fun loadWeather() {
        // Paint cached reading immediately for instant offline-first startup
        binding.textWeather.text = prefs.cachedWeather
        updateWeatherIcon(prefs.weatherCode)

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

    private fun launchTileOrPrompt(slot: Int, config: TileConfig) {
        val launchIntent = packageManager.getLaunchIntentForPackage(config.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        } else {
            Toast.makeText(
                this,
                "${config.label} not installed — choose an app for this tile.",
                Toast.LENGTH_LONG
            ).show()
            openTileAppPicker(slot, reopenCustomizer = false)
        }
    }

    private fun openTileAppPicker(slot: Int, reopenCustomizer: Boolean) {
        targetRebindKey = null
        targetTileSlot = slot
        reopenTileCustomizerAfterPick = reopenCustomizer
        val intent = Intent(this, AppDrawerActivity::class.java).apply {
            action = AppDrawerActivity.ACTION_PICK_APP
        }
        appPickerLauncher.launch(intent)
    }

    private fun openAppPicker(rebindKey: String) {
        targetTileSlot = null
        reopenTileCustomizerAfterPick = false
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
