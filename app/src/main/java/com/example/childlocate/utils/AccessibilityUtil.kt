package com.example.childlocate.utils

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.example.childlocate.service.WebFilterAccessibilityService

object AccessibilityUtil {
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val serviceComponentName = ComponentName(context, WebFilterAccessibilityService::class.java)

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )

        return enabledServices?.contains(context.packageName + "/" + serviceComponentName.flattenToString())
            ?: false
    }

    fun isOverlayPermissionGranted(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }
}