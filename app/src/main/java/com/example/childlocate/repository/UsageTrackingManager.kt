package com.example.childlocate.repository


import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.childlocate.service.UsageStatsWorker

// UsageTrackingManager.kt
class UsageTrackingManager(context: Context) {
    private val workManager = WorkManager.getInstance(context)

    fun requestImmediateSync() {
        val immediateRequest = OneTimeWorkRequestBuilder<UsageStatsWorker>()
            .build()
        workManager.enqueue(immediateRequest)
    }
    fun stopPeriodicTracking() {
        workManager.cancelUniqueWork("usage_tracking")  // "usage_tracking" là unique name đã dùng trước đó
    }

}