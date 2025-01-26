// AudioStreamingForegroundService.kt
package com.example.childlocate.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.childlocate.R
import com.example.childlocate.service.AudioConstants.NOTIFICATION_ID
import com.example.childlocate.ui.parent.MainActivity
import com.google.firebase.database.FirebaseDatabase


// Constants.kt
object AudioConstants {
    const val SAMPLE_RATE = 44100
    const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    const val CHUNK_SIZE = 4096
    const val NOTIFICATION_ID = 1011
    const val CHANNEL_ID = "audio_streaming_channel"
}

// AudioStreamingService.kt
class AudioStreamingForegroundService : Service() {
    private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private val database = FirebaseDatabase.getInstance()
    private val handler = Handler(Looper.getMainLooper())
    private var recordingThread: Thread? = null
    private var isReceiverRegistered = false
    private var bufferSize = AudioRecord.getMinBufferSize(
        AudioConstants.SAMPLE_RATE,
        AudioConstants.CHANNEL_CONFIG,
        AudioConstants.AUDIO_FORMAT
    )

    private val broadcastReceiver = object : BroadcastReceiver() {
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
                "STOP_RECORDING" -> {
                    if (deviceId != null) {
                        stopStreamingOnly(deviceId)
                    }
                }
            }
        }

    }

    override fun onCreate() {
        super.onCreate()
        try {
            setupForegroundService()
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
            registerBroadcastReceiver()
            Log.d("SERVICE_DEBUG", "6. Service successfully created")
        } catch (e: Exception) {
            Log.e("SERVICE_DEBUG", "Error in onCreate: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun registerBroadcastReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter("AUDIO_SERVICE_COMMAND")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(broadcastReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(broadcastReceiver, filter)
            }
            isReceiverRegistered = true
            Log.d("SERVICE_DEBUG", "5. BroadcastReceiver registered")
        }
    }

    private fun setupForegroundService() {
        createNotificationChannel()

        // Kiểm tra quyền FOREGROUND_SERVICE_MICROPHONE cho Android 14+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e("SERVICE_DEBUG", "Missing FOREGROUND_SERVICE_MICROPHONE permission")
                stopSelf()
                return
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AudioConstants.CHANNEL_ID,
                "Audio Streaming Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Used for streaming audio"
                setSound(null, null)
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

        return NotificationCompat.Builder(this, AudioConstants.CHANNEL_ID)
            .setContentTitle("Audio Streaming")
            .setContentText("Streaming audio...")
            .setSmallIcon(R.drawable.baseline_mic_24)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startStreaming(childId: String) {
        if (isRecording) return

        try {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                AudioConstants.SAMPLE_RATE,
                AudioConstants.CHANNEL_CONFIG,
                AudioConstants.AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("AudioStreaming", "Failed to initialize AudioRecord")
                return
            }

            isRecording = true
            audioRecord?.startRecording()

            recordingThread = Thread {
                streamAudio(childId)
            }.apply {
                start()
            }

            // Update status in Firebase
            updateStreamingStatus(childId, true)

        } catch (e: Exception) {
            Log.e("AudioStreaming", "Error starting streaming: ${e.message}")
            stopSelf()
        }
    }

    private fun streamAudio(childId: String) {
        val buffer = ByteArray(AudioConstants.CHUNK_SIZE)
        while (isRecording) {
            val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: -1
            if (readSize > 0) {
                val encodedChunk = Base64.encodeToString(buffer, Base64.DEFAULT)
                sendAudioChunk(childId, encodedChunk)
            }
        }
    }

    private fun sendAudioChunk(childId: String, chunk: String) {
        val timestamp = System.currentTimeMillis()
        val chunkRef = database.reference
            .child("audio_streams")
            .child(childId)
            .child(timestamp.toString())

        chunkRef.setValue(chunk)
            .addOnFailureListener { e ->
                Log.e("AudioStreaming", "Failed to send chunk: ${e.message}")
            }

        // Clean up old chunks after 5 seconds
        handler.postDelayed({
            chunkRef.removeValue()
        }, 5000)
    }

    private fun updateStreamingStatus(childId: String, isStreaming: Boolean) {
        database.reference
            .child("streaming_status")
            .child(childId)
            .setValue(isStreaming)
    }

    private fun stopStreamingOnly(childId: String) {
        if (!isRecording) return

        isRecording = false
        cleanupRecording()
        updateStreamingStatus(childId, false)
    }

    private fun cleanupRecording() {
        try {
            recordingThread?.join(1000)
            audioRecord?.apply {
                stop()
                release()
            }
            audioRecord = null
        } catch (e: Exception) {
            Log.e("AudioStreaming", "Error cleaning up recording: ${e.message}")
        }
    }

    override fun onDestroy() {
        try {
            // Hủy đăng ký BroadcastReceiver
            if (isReceiverRegistered) {
                unregisterReceiver(broadcastReceiver)
                isReceiverRegistered = false
            }

            // Cleanup các resource khác
            handler.removeCallbacksAndMessages(null)
            cleanupRecording()

            super.onDestroy()
        } catch (e: Exception) {
            Log.e("SERVICE_DEBUG", "Error in onDestroy: ${e.message}")
        }
    }


    override fun onBind(intent: Intent?) = null
}