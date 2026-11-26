package com.automotive.swiftlauncher

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Ultra-lightweight persistent floating Mute Volume pill that stays accessible
 * over Android Auto, CarPlay (ZLink), YouTube, and navigation apps once DashFlow
 * is active as the Default Home App (or enabled in settings).
 */
class FloatingMuteService : Service() {

    companion object {
        const val ACTION_REFRESH = "com.automotive.swiftlauncher.ACTION_REFRESH_FLOATING_MUTE"
        private const val CHANNEL_ID = "dashflow_floating_mute"
        private const val NOTIFICATION_ID = 4041

        fun isDefaultHomeApp(context: Context): Boolean {
            return try {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                }
                val resolveInfo = context.packageManager.resolveActivity(homeIntent, 0)
                resolveInfo?.activityInfo?.packageName == context.packageName
            } catch (_: Exception) {
                false
            }
        }

        fun sync(context: Context) {
            val prefs = PreferenceHelper(context)
            val shouldRun = prefs.floatingMuteEnabled &&
                Settings.canDrawOverlays(context) &&
                isDefaultHomeApp(context)

            val serviceIntent = Intent(context, FloatingMuteService::class.java)
            if (shouldRun) {
                serviceIntent.action = ACTION_REFRESH
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } else {
                context.stopService(serviceIntent)
            }
        }
    }

    private lateinit var prefs: PreferenceHelper
    private lateinit var audioManager: AudioManager
    private lateinit var windowManager: WindowManager

    private var pillContainer: FrameLayout? = null
    private var pillIcon: ImageView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                updatePillAppearance()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = PreferenceHelper(this)
        audioManager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        startForegroundNotification()
        createOverlayPill()

        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(volumeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(volumeReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this) || !prefs.floatingMuteEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        updatePillAppearance()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(volumeReceiver)
        } catch (_: Exception) {}
        try {
            pillContainer?.let { windowManager.removeViewImmediate(it) }
        } catch (_: Exception) {}
        pillContainer = null
        pillIcon = null
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Mute Control",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
                setSound(null, null)
            }
            nm.createNotificationChannel(channel)
        }

        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DashFlow Quick Mute Active")
            .setContentText("Tap floating pill anytime to mute/unmute media")
            .setSmallIcon(R.drawable.ic_volume_up)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createOverlayPill() {
        val density = resources.displayMetrics.density
        val sizePx = (48 * density).roundToInt()
        val iconSizePx = (24 * density).roundToInt()
        val marginPx = (16 * density).roundToInt()

        val initialX = if (prefs.floatingPillX >= 0) {
            prefs.floatingPillX
        } else {
            (resources.displayMetrics.widthPixels - sizePx - marginPx).coerceAtLeast(0)
        }
        val initialY = if (prefs.floatingPillY >= 0) {
            prefs.floatingPillY
        } else {
            (resources.displayMetrics.heightPixels / 2 - sizePx / 2).coerceAtLeast(0)
        }

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }

        val container = FrameLayout(this).apply {
            elevation = 8 * density
        }
        val icon = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(iconSizePx, iconSizePx, Gravity.CENTER)
        }
        container.addView(icon)

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var startRawX = 0f
        var startRawY = 0f
        var startX = 0
        var startY = 0
        var isDragging = false

        container.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startRawX).roundToInt()
                    val dy = (event.rawY - startRawY).roundToInt()
                    if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        val maxX = (resources.displayMetrics.widthPixels - sizePx).coerceAtLeast(0)
                        val maxY = (resources.displayMetrics.heightPixels - sizePx).coerceAtLeast(0)
                        params.x = (startX + dx).coerceIn(0, maxX)
                        params.y = (startY + dy).coerceIn(0, maxY)
                        try {
                            windowManager.updateViewLayout(container, params)
                        } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        prefs.floatingPillX = params.x
                        prefs.floatingPillY = params.y
                    } else {
                        toggleMute()
                    }
                    true
                }
                else -> false
            }
        }

        layoutParams = params
        pillContainer = container
        pillIcon = icon
        updatePillAppearance()

        try {
            windowManager.addView(container, params)
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun toggleMute() {
        try {
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (current > 0) {
                prefs.lastMediaVolume = current
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
            } else {
                val saved = prefs.lastMediaVolume
                val target = if (saved > 0) {
                    saved
                } else {
                    (audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * 0.6).toInt().coerceAtLeast(1)
                }
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    target,
                    AudioManager.FLAG_SHOW_UI or AudioManager.FLAG_PLAY_SOUND
                )
            }
            updatePillAppearance()
        } catch (_: Exception) {}
    }

    private fun updatePillAppearance() {
        val container = pillContainer ?: return
        val icon = pillIcon ?: return

        val isMuted = try {
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
        } catch (_: Exception) {
            false
        }

        val accentColor = try {
            Color.parseColor(prefs.accentColor)
        } catch (_: Exception) {
            Color.parseColor(PreferenceHelper.DEFAULT_ACCENT_COLOR)
        }

        val borderColor = if (isMuted) Color.parseColor("#EF4444") else accentColor
        val fillColor = if (isMuted) {
            Color.argb(225, 42, 16, 22)
        } else {
            Color.argb(220, 16, 22, 34)
        }

        val density = resources.displayMetrics.density
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fillColor)
            setStroke((1.75f * density).roundToInt(), borderColor)
        }
        container.background = bg
        icon.setImageResource(if (isMuted) R.drawable.ic_volume_mute else R.drawable.ic_volume_up)
        icon.setColorFilter(if (isMuted) Color.parseColor("#F87171") else Color.WHITE)
    }
}
