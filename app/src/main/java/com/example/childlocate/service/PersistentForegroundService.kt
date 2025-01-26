package com.example.childlocate.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.childlocate.R
import com.example.childlocate.ui.parent.MainActivity
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.storage.FirebaseStorage
import java.io.File

// PersistentForegroundService.kt
class PersistentForegroundService : Service() {
    private val RECORDING_DURATION_MS = 60000
    private val NOTIFICATION_ID = 107
    private val CHANNEL_ID = "persistent_service_channel"
    private var isRecording = false
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private val database = FirebaseDatabase.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private var isReceiverRegistered = false
    private val SAMPLE_RATE = 44100

     val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val command = intent?.getStringExtra("command")
            val deviceId = intent?.getStringExtra("deviceId")

            Log.d("SERVICE_DEBUG", "2. Command: $command, DeviceId: $deviceId")

            when (command) {
                "START_RECORDING" -> {
                    if (deviceId != null) {
                        Log.d("SERVICE_DEBUG", "3. Starting recording for device: $deviceId")
                        startStreaming(deviceId)
                    } else {
                        Log.e("SERVICE_DEBUG", "Device ID is null")
                    }
                }
            }
        }

    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        Log.d("SERVICE_DEBUG", "4. Service onCreate called")
        try {
            createNotificationChannel()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }

            // Đăng ký BroadcastReceiver
            if (!isReceiverRegistered) {
                val filter = IntentFilter("PERSISTENT_SERVICE_COMMAND")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(broadcastReceiver, filter, RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(broadcastReceiver, filter)
                }
                isReceiverRegistered = true
                Log.d("SERVICE_DEBUG", "5. BroadcastReceiver registered")
            }

            Log.d("SERVICE_DEBUG", "6. Service successfully created")
        } catch (e: Exception) {
            Log.e("SERVICE_DEBUG", "Error in onCreate: ${e.message}")
            e.printStackTrace()
        }

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("SERVICE_DEBUG", "7. Service onStartCommand called with action: ${intent?.action}")
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Persistent Service",
                NotificationManager.IMPORTANCE_LOW // Đặt độ ưu tiên thấp
            ).apply {
                description = "Maintains connection and handles background tasks"
                setSound(null, null) // Không có âm thanh
                enableLights(false)
                enableVibration(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Service Active")
            .setContentText(if (isRecording) "Recording in progress..." else "Monitoring...")
            .setSmallIcon(R.drawable.baseline_campaign_24)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startStreaming(childId: String) {
        try {
            val timestamp = System.currentTimeMillis()
            /*val fileName = "audio_${timestamp}.mp3"
            recordingFile = File(cacheDir, fileName)*/
            // Thay thế dấu chấm trong tên tệp bằng dấu gạch dưới
            val fileName = "audio_${timestamp}_mp3" // Chuyển ".mp3" thành "_mp3"
            recordingFile = File(cacheDir, "$fileName.mp3") // Tên file lưu trữ vẫn có ".mp3"

            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(recordingFile?.absolutePath)
                setAudioSamplingRate(SAMPLE_RATE)
                prepare()
                start()
            }

            // Stop recording after 1 minute
            Handler(Looper.getMainLooper()).postDelayed({
                stopAndUpload(childId, fileName)
            }, RECORDING_DURATION_MS.toLong())

        } catch (e: Exception) {
            Log.e("AudioRecording", "Error starting recording: ${e.message}")
            stopSelf()
        }
    }

    private fun stopAndUpload(childId: String, fileName: String) {
        try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null

            recordingFile?.let { file ->
                uploadRecording(childId, file, fileName)
            }
        } catch (e: Exception) {
            Log.e("AudioRecording", "Error stopping recording: ${e.message}")
        }
    }

    private fun uploadRecording(childId: String, file: File, fileName: String) {
        val storageRef = storage.reference.child("audio_recordings/$childId/$fileName.mp3")

        storageRef.putFile(Uri.fromFile(file))
            .addOnSuccessListener { taskSnapshot ->
                taskSnapshot.storage.downloadUrl.addOnSuccessListener { downloadUrl ->
                    // Save metadata to Realtime Database
                    val audioMetadata = mapOf(
                        "url" to downloadUrl.toString(),
                        "timestamp" to ServerValue.TIMESTAMP,
                        "duration" to RECORDING_DURATION_MS,
                        "status" to "completed"
                    )

                    database.reference
                        .child("audio_recordings")
                        .child(childId)
                        .child(fileName)
                        .setValue(audioMetadata)
                        .addOnCompleteListener {
                            file.delete() // Clean up local file
                            stopSelf()
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("AudioUpload", "Failed to upload: ${e.message}")
                stopSelf()
            }
    }
    override fun onDestroy() {
        super.onDestroy()
        try {
            if (isReceiverRegistered) {
                unregisterReceiver(broadcastReceiver)
                isReceiverRegistered = false
                Log.d("SERVICE_DEBUG", "8. BroadcastReceiver unregistered")
            }
        } catch (e: Exception) {
            Log.e("SERVICE_DEBUG", "Error in onDestroy: ${e.message}")
            e.printStackTrace()
        }
    }


    override fun onBind(intent: Intent?) = null
}