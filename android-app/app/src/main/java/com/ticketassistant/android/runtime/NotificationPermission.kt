package com.ticketassistant.android.runtime

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object NotificationPermission {
    fun state(context: Context): NotificationPermissionState {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return NotificationPermissionState.RUNTIME_PERMISSION_REQUIRED
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) {
            return NotificationPermissionState.SETTINGS_REQUIRED
        }
        val channel = manager.getNotificationChannel(TaskForegroundService.CHANNEL_ID)
        if (channel?.importance == NotificationManager.IMPORTANCE_NONE) {
            return NotificationPermissionState.SETTINGS_REQUIRED
        }
        return NotificationPermissionState.GRANTED
    }
}

enum class NotificationPermissionState {
    GRANTED,
    RUNTIME_PERMISSION_REQUIRED,
    SETTINGS_REQUIRED,
}
