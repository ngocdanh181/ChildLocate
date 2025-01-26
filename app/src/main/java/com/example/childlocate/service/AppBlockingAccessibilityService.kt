package com.example.childlocate.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Collections

class AppBlockingAccessibilityService : AccessibilityService() {
    companion object {
        private var instance: AppBlockingAccessibilityService? = null
        fun getInstance(): AppBlockingAccessibilityService? = instance
    }

    private lateinit var appLimitManager: AppLimitManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val blockedPackagesCache = Collections.synchronizedSet(mutableSetOf<String>())



    override fun onCreate() {
        super.onCreate()
        instance = this
        appLimitManager = AppLimitManager(this)

        // Initialize cache from database
        scope.launch {
            val appLimits = appLimitManager.getAppLimits()
            blockedPackagesCache.addAll(appLimits.map { it.packageName })
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        instance = null
        scope.cancel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            if (shouldBlockPackage(packageName)) {
                Handler(Looper.getMainLooper()).postDelayed({
                    performGlobalAction(GLOBAL_ACTION_HOME)

                    sendBroadcast(Intent("APP_BLOCKED").apply {
                        putExtra("package_name", packageName)
                    })
                }, 200)
            }
        }
    }

    private fun shouldBlockPackage(packageName: String?): Boolean {
        return packageName in blockedPackagesCache
    }

    override fun onInterrupt() {}

    fun forceCloseApp(packageName: String) {
        blockedPackagesCache.add(packageName)
        performGlobalAction(GLOBAL_ACTION_HOME)

        // Update database asynchronously
        scope.launch {
            try {
                val appLimit = appLimitManager.getAppLimits()
                    .find { it.packageName == packageName }
                if (appLimit != null) {
                    appLimitManager.saveAppLimit(appLimit)
                }
            } catch (e: Exception) {
                Log.e("AccessibilityService", "Error updating database: ${e.message}")
            }
        }
    }

    fun clearBlockedPackage(packageName:String) {
        blockedPackagesCache.remove(packageName)

        // Update database asynchronously
        scope.launch {
            try {
                appLimitManager.removeAppLimit(packageName)
            } catch (e: Exception) {
                Log.e("AccessibilityService", "Error removing from database: ${e.message}")
            }
        }
    }

    fun clearAllBlockedPackages() {
        blockedPackagesCache.clear()

        // Update database asynchronously
        scope.launch {
            try {
                val appLimits = appLimitManager.getAppLimits()
                for (appLimit in appLimits) {
                    appLimitManager.removeAppLimit(appLimit.packageName)
                }
            } catch (e: Exception) {
                Log.e("AccessibilityService", "Error clearing database: ${e.message}")
            }
        }
    }
}