package com.automotive.swiftlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

data class MediaUiState(
    val hasActiveSession: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false
)

class MediaSessionHelper(
    private val context: Context,
    private val onStateChanged: (MediaUiState) -> Unit
) {
    private val mediaSessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    private val listenerComponent = ComponentName(context, MediaNotificationListener::class.java)

    private var activeController: MediaController? = null
    private var isListening = false

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            emitState()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            emitState()
        }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        attachPrimaryController(controllers)
    }

    fun hasNotificationAccess(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

    fun openNotificationAccessSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun start() {
        if (isListening || !hasNotificationAccess() || mediaSessionManager == null) {
            if (!hasNotificationAccess()) {
                onStateChanged(MediaUiState(hasActiveSession = false))
            }
            return
        }
        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent)
            attachPrimaryController(mediaSessionManager.getActiveSessions(listenerComponent))
            isListening = true
        } catch (_: SecurityException) {
            onStateChanged(MediaUiState(hasActiveSession = false))
        } catch (_: Exception) {
            onStateChanged(MediaUiState(hasActiveSession = false))
        }
    }

    fun stop() {
        if (isListening) {
            try {
                mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
            } catch (_: Exception) {}
            isListening = false
        }
        detachController()
    }

    fun togglePlayPause() {
        val controls = activeController?.transportControls ?: return
        val state = activeController?.playbackState?.state
        if (state == PlaybackState.STATE_PLAYING) {
            controls.pause()
        } else {
            controls.play()
        }
    }

    fun skipToNext() {
        activeController?.transportControls?.skipToNext()
    }

    fun skipToPrevious() {
        activeController?.transportControls?.skipToPrevious()
    }

    fun openPlayerApp() {
        val pkg = activeController?.packageName ?: return
        try {
            context.packageManager.getLaunchIntentForPackage(pkg)?.let { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        } catch (_: Exception) {}
    }

    private fun attachPrimaryController(controllers: List<MediaController>?) {
        val chosen = controllers?.firstOrNull {
            val st = it.playbackState?.state
            st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_PAUSED || st == PlaybackState.STATE_BUFFERING
        } ?: controllers?.firstOrNull()

        if (chosen?.sessionToken != activeController?.sessionToken) {
            detachController()
            activeController = chosen
            activeController?.registerCallback(controllerCallback)
        }
        emitState()
    }

    private fun detachController() {
        try {
            activeController?.unregisterCallback(controllerCallback)
        } catch (_: Exception) {}
        activeController = null
    }

    private fun emitState() {
        val controller = activeController
        val metadata = controller?.metadata
        val state = controller?.playbackState?.state
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty().trim()
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty()

        val hasTrack = controller != null && title.isNotEmpty() &&
            state != PlaybackState.STATE_NONE && state != PlaybackState.STATE_STOPPED

        onStateChanged(
            MediaUiState(
                hasActiveSession = hasTrack,
                title = title,
                artist = artist.trim(),
                isPlaying = state == PlaybackState.STATE_PLAYING
            )
        )
    }
}
