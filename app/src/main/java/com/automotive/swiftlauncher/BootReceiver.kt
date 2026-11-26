package com.automotive.swiftlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Lightweight cold-boot receiver for aftermarket Android head units.
 * If the user configured a preferred auto-launch app (e.g. ZLink or Open Headunit),
 * launches it cleanly on ignition / boot without keeping any background service alive.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        val prefs = PreferenceHelper(context)
        FloatingMuteService.sync(context)

        val targetPkg = prefs.bootLaunchPackage.trim()
        if (targetPkg.isEmpty()) return

        try {
            context.packageManager.getLaunchIntentForPackage(targetPkg)?.let { launchIntent ->
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            }
        } catch (_: Exception) {}
    }
}
