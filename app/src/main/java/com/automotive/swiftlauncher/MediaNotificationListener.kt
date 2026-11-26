package com.automotive.swiftlauncher

import android.service.notification.NotificationListenerService

/**
 * Minimal NotificationListenerService required by Android's MediaSessionManager
 * to read active media playback from Spotify, YouTube Music, Poweramp, etc.
 * Performs no background notification scraping to keep memory overhead near zero.
 */
class MediaNotificationListener : NotificationListenerService()
