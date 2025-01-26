package com.example.childlocate.service

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class AudioRecordingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Lấy childId từ intent
        val childId = intent.getStringExtra("child_id")

        // Tạo intent để start service với đầy đủ thông tin
        val serviceIntent = Intent(this, AudioStreamingForegroundService::class.java).apply {
            action = "START_STREAMING"
            putExtra("child_id", childId) // Truyền childId sang service
        }

        // Start service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        finish()
    }
}