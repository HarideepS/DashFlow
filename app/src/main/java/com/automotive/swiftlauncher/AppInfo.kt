package com.automotive.swiftlauncher

import android.graphics.drawable.Drawable

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable? = null
) : Comparable<AppInfo> {
    override fun compareTo(other: AppInfo): Int {
        return label.compareTo(other.label, ignoreCase = true)
    }
}
