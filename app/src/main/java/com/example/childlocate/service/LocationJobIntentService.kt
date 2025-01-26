package com.example.childlocate.service

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.JobIntentService

class LocationJobIntentService : JobIntentService() {
    companion object {
        private const val JOB_ID = 1000

        fun enqueueWork(context: Context, work: Intent) {
            enqueueWork(context, LocationJobIntentService::class.java, JOB_ID, work)
        }
    }

    override fun onHandleWork(intent: Intent) {
        // Khởi động LocationForegroundService từ đây
        val serviceIntent = Intent(applicationContext, LocationForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}