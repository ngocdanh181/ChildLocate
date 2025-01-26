package com.example.childlocate.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import com.example.childlocate.R
import com.example.childlocate.data.model.BlockedKeyword
import com.example.childlocate.ui.child.main.MainChildActivity
import com.google.firebase.database.FirebaseDatabase

class WebFilterAccessibilityService : AccessibilityService() {
    private lateinit var webFilterManager: ChildWebFilterManager
    private var keyWords: List<BlockedKeyword> =emptyList()
    private val database = FirebaseDatabase.getInstance()
    private val keywordsRef = database.getReference("web_filter")
    private lateinit var childId: String


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        Log.d("WebFilter", "Service onCreate")
        webFilterManager = ChildWebFilterManager(this)

        val sharedPreferences = applicationContext.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        childId = sharedPreferences.getString("childId", null) ?: return

        if (childId.isNotEmpty()) {
            webFilterManager.startMonitoring(childId) { loadedKeywords ->
                keyWords = loadedKeywords
                Log.d("WebFilter", "service keywords loaded: $keyWords")
            }
        } else {
            Log.e("WebFilter", "Child ID is empty, service will not monitor.")
        }


        startForeground()
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun startForeground() {
        val channelId = createNotificationChannel("web_filter", "Web Filter Service")
        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("Web Filter Active")
            .setContentText("Monitoring web content")
            .setSmallIcon(R.drawable.baseline_notifications_24)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(4322, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(4322, notification)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(channelId: String, channelName: String): String {
        val chan = NotificationChannel(
            channelId,
            channelName, NotificationManager.IMPORTANCE_LOW
        )
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_FOCUSED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                handleTextChange(event)
            }
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                handleViewFocus(event)
            }

            AccessibilityEvent.TYPE_ANNOUNCEMENT -> {
                TODO()
            }

            AccessibilityEvent.TYPE_ASSIST_READING_CONTEXT -> {
                TODO()
            }

            AccessibilityEvent.TYPE_GESTURE_DETECTION_END -> {
                TODO()
            }

            AccessibilityEvent.TYPE_GESTURE_DETECTION_START -> {
                TODO()
            }

            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                TODO()
            }

            AccessibilityEvent.TYPE_SPEECH_STATE_CHANGE -> {
                TODO()
            }

            AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END -> {
                TODO()
            }

            AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START -> {
                TODO()
            }

            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> {
                TODO()
            }

            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
                TODO()
            }

            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED -> {
                TODO()
            }

            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED -> {
                TODO()
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                TODO()
            }

            AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED -> {
                TODO()
            }

            AccessibilityEvent.TYPE_VIEW_HOVER_ENTER -> {
                TODO()
            }

            AccessibilityEvent.TYPE_VIEW_HOVER_EXIT -> {
                TODO()
            }

            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                TODO()
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                TODO()
            }

            AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                TODO()
            }

            AccessibilityEvent.TYPE_VIEW_TARGETED_BY_SCROLL -> {
                TODO()
            }

            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                TODO()
            }

            AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY -> {
                TODO()
            }

            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                TODO()
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                TODO()
            }
        }
    }

    private fun handleTextChange(event: AccessibilityEvent) {
        // Extract text more comprehensively
        val extractedTexts = mutableListOf<String>()

        // Try multiple text extraction methods
        event.text.forEach { charSequence ->
            charSequence.toString().takeIf { it.isNotBlank() }?.let { extractedTexts.add(it) }
        }

        event.source?.let { source ->
            source.text?.toString()?.takeIf { it.isNotBlank() }?.let { extractedTexts.add(it) }
        }

        // Log all extracted texts for debugging
        extractedTexts.forEach { text ->
            keyWords.forEach { keyword ->
                if (webFilterManager.isBlocked(text, listOf(keyword))) {
                    // Tăng counter
                    incrementKeywordCounter(keyword.id)
                    // Handle blocking
                    handleBlockedText(event.source)
                    return
                }
            }
        }
    }

    private fun incrementKeywordCounter(keywordId: String) {
        val attemptsRef = keywordsRef
            .child(childId)
            .child("attempts")
            .child(keywordId)

        // Đọc giá trị hiện tại
        attemptsRef.get().addOnSuccessListener { snapshot ->
            val currentCount = snapshot.getValue(Int::class.java) ?: 0
            // Increment và update
            attemptsRef.setValue(currentCount + 1)
                .addOnSuccessListener {
                    Log.d("WebFilter", "Counter incremented successfully")
                }
                .addOnFailureListener { e ->
                    Log.e("WebFilter", "Counter increment failed", e)
                }
        }
    }
    private fun handleBlockedText(source: AccessibilityNodeInfo?) {
        source?.let {
            try {
                // Clear text in editable fields
                if (it.isEditable) {
                    it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            ""
                        )
                    })
                }

                // Go back and show warning
                performGlobalAction(GLOBAL_ACTION_BACK)
                showBlockedContentWarning()
            } catch (e: Exception) {
                Log.e("WebFilter", "Error handling blocked text: ${e.message}")
            }
        }
    }
    private fun handleViewFocus(event: AccessibilityEvent) {
        val source = event.source ?: return
        try {
            when (source.className) {
                "android.webkit.WebView" -> monitorWebViewContent(source)
                //"android.widget.EditText" -> monitorEditTextContent(source)
            }
        } finally {
            source.recycle()
        }
    }

    private fun monitorWebViewContent(source: AccessibilityNodeInfo) {
        val webContent = source.text?.toString() ?: return
        keyWords.forEach { keyword ->
            if (webFilterManager.isBlocked(webContent, keyWords)) {
                // Tăng counter
                incrementKeywordCounter(keyword.id)

                // Go back and show warning
                performGlobalAction(GLOBAL_ACTION_BACK)
                showBlockedContentWarning()
            }
        }
    }

    /*private fun monitorEditTextContent(source: AccessibilityNodeInfo) {
        val text = source.text?.toString() ?: return
        keyWords.forEach {keyword->
            if (webFilterManager.isBlocked(text,keyWords)) {
                source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                })
                showBlockedContentWarning()
                incrementKeywordCounter(keyword.id)
            }
        }

    }*/

    private fun showBlockedContentWarning() {
        val intent = Intent(this, MainChildActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("show_warning", true)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        Log.d("WebFilter", "Service interrupted")
    }
}
