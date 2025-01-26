package com.example.childlocate.service
/*
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.childlocate.R
import com.example.childlocate.data.model.AudioChunk
import com.example.childlocate.data.model.StreamMetadata
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID
import java.util.zip.GZIPOutputStream

// AudioRecordingWorker.kt
class AudioRecordingWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {



    private  val SAMPLE_RATE = 44100



    private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private val database = FirebaseDatabase.getInstance()
    private var currentStreamId: String? = null
    private var lastSequence = 0L

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun doWork(): Result {
        val childId = inputData.getString("child_id") ?: return Result.failure()

        // Check permissions first
        if (!hasRequiredPermissions()) {
            // Notify parent device about missing permissions
            notifyPermissionMissing(childId)
            return Result.failure()
        }

        try {
            // Set as foreground service with notification
            setForeground(createForegroundInfo())

            // Start recording
            startRecording(childId)

            // Keep worker running
            while (isRecording) {
                delay(1000) // Check every second if we should continue recording

                // Check if parent has requested to stop
                if (shouldStopRecording(childId)) {
                    stopRecording()
                    break
                }
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e("AudioWorker", "Recording failed", e)
            notifyRecordingError(childId, e.message ?: "Unknown error")
            return Result.failure()
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                        ContextCompat.checkSelfPermission(
                            applicationContext,
                            Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
                        ) == PackageManager.PERMISSION_GRANTED)
    }

    private fun createForegroundInfo(): ForegroundInfo {
        // Tạo notification channel nếu cần
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Audio Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableLights(false)
                enableVibration(false)
            }

            val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        // Tạo một notification "im lặng"
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Service Running")
            .setContentText("Background service is active")
            .setSmallIcon(R.drawable.notification_icon)
            .setSound(null)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun startRecording(childId: String) {
        currentStreamId = UUID.randomUUID().toString()
        setupStreamMetadata(childId)

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )

        if (ActivityCompat.checkSelfPermission(
                applicationContext,
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
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        isRecording = true
        audioRecord?.startRecording()

        // Bắt đầu ghi âm trong coroutine scope
        withContext(Dispatchers.IO) {
            val buffer = ByteArray(CHUNK_SIZE)

            while (isRecording && isActive) {
                val bytesRead = audioRecord?.read(buffer, 0, CHUNK_SIZE) ?: 0
                if (bytesRead > 0) {
                    val compressedData = compressAudioData(buffer.copyOf(bytesRead))
                    val chunk = AudioChunk(
                        sequence = lastSequence++,
                        timestamp = System.currentTimeMillis(),
                        data = Base64.getEncoder().encodeToString(compressedData)
                    )

                    sendChunkToFirebase(childId, chunk)
                    cleanupOldChunks(childId)
                }
            }
        }
    }

    private fun sendChunkToFirebase(childId: String, chunk: AudioChunk) {
        val chunkRef = database.getReference("audio_streams")
            .child(childId)
            .child("streams")
            .child(currentStreamId!!)
            .child("chunks")
            .child(chunk.sequence.toString())

        chunkRef.setValue(chunk)
            .addOnFailureListener { e ->
                Log.e("AudioStream", "Error sending chunk: ${e.message}")
            }
    }

    private fun cleanupOldChunks(childId: String) {
        if (lastSequence > 100) { // Giữ 100 chunk gần nhất
            val obsoleteSequence = lastSequence - 100
            database.getReference("audio_streams")
                .child(childId)
                .child("streams")
                .child(currentStreamId!!)
                .child("chunks")
                .orderByKey()
                .endAt(obsoleteSequence.toString())
                .get()
                .addOnSuccessListener { snapshot ->
                    snapshot.children.forEach { it.ref.removeValue() }
                }
        }
    }

    private fun compressAudioData(audioData: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        val gzipOutputStream = GZIPOutputStream(baos)
        gzipOutputStream.write(audioData)
        gzipOutputStream.close()
        return baos.toByteArray()
    }


    private fun stopRecording() {
        isRecording = false
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null

        // Update status in Firebase
        currentStreamId?.let { streamId ->
            database.getReference("audio_streams")
                .child(streamId)
                .child("metadata")
                .child("status")
                .setValue("stopped")
        }
    }

    private fun notifyPermissionMissing(childId: String) {
        database.getReference("devices")
            .child(childId)
            .child("status")
            .setValue(mapOf(
                "error" to "PERMISSION_MISSING",
                "timestamp" to ServerValue.TIMESTAMP
            ))
    }

    private fun notifyRecordingError(childId: String, error: String) {
        database.getReference("devices")
            .child(childId)
            .child("status")
            .setValue(mapOf(
                "error" to error,
                "timestamp" to ServerValue.TIMESTAMP
            ))
    }

    private suspend fun shouldStopRecording(childId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val snapshot = database.getReference("audio_streams")
                .child(childId)
                .child("metadata")
                .child("status")
                .get()
                .await()

            return@withContext snapshot.getValue(String::class.java) == "stop_requested"
        } catch (e: Exception) {
            Log.e("AudioWorker", "Error checking stop status", e)
            return@withContext false
        }
    }
    private fun setupStreamMetadata(childId: String) {
        val streamRef = database.getReference("audio_streams").child(childId)
        val metadata = StreamMetadata(
            status = "streaming",
            lastUpdate = System.currentTimeMillis(),
            currentStreamId = currentStreamId!!,
            sampleRate = AudioStreamingForegroundService.SAMPLE_RATE,
            format = "PCM_16BIT",
            channelConfig = "MONO"
        )

        streamRef.child("metadata").setValue(metadata)
    }

    companion object {
        private const val CHANNEL_ID = "BackgroundAudioChannel"
        private const val NOTIFICATION_ID = 123
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_SIZE = 1024
    }
}
*/
