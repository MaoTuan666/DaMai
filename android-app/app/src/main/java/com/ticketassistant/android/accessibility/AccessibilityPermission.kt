package com.ticketassistant.android.accessibility

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils

object AccessibilityPermission {
    fun isServiceEnabled(context: Context): Boolean {
        if (Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0,
            ) != 1
        ) {
            return false
        }

        val expectedComponent = ComponentName(
            context,
            TicketAccessibilityService::class.java,
        ).flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()

        return enabledServices
            .split(':')
            .any { TextUtils.equals(it, expectedComponent) }
    }
}
