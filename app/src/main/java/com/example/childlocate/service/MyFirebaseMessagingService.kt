package com.example.childlocate.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.example.childlocate.R
import com.example.childlocate.data.model.AppLimit
import com.example.childlocate.repository.UsageTrackingManager
import com.example.childlocate.ui.parent.MainActivity
import com.example.childlocate.utils.ServiceUtils
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale


class MyFirebaseMessagingService : FirebaseMessagingService() {
    private val channelId = "WarningForegroundServiceChannel"
    private var ringtone: Ringtone? = null // Declare ringtone as a class variable
    private var vibrator: Vibrator? = null
    private lateinit var appLimitManager: AppLimitManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())


    override fun onCreate() {
        super.onCreate()
        appLimitManager = AppLimitManager(this)
    }


    @RequiresApi(Build.VERSION_CODES.P)
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d("FCM", "Received message from FCM: ${remoteMessage.data}")

        val requestTypeFull = remoteMessage.data["request_type"] ?: return
        val requestParts = requestTypeFull.split("|", limit = 2)
        val appRequestParts = requestTypeFull.split("|")
        val requestType = requestParts[0]
        val additionalInfo = if (requestParts.size > 1) requestParts[1] else ""
        ensurePersistentServiceRunning()
        Log.d("FCM","$requestType")
        // Check if the message contains data
        if (remoteMessage.data.isNotEmpty()) {
            // Check if the message is a location request
            when (requestType) {
                "location_request" -> {
                    Log.d("FCM", "Received location request from FCM")
                    val intent = Intent(this, LocationJobIntentService::class.java)
                    LocationJobIntentService.enqueueWork(this, intent)
                }
                "stop_location_request" -> {
                    // Stop location foreground service
                    val stopIntent = Intent(this, LocationForegroundService::class.java)
                    stopService(stopIntent)
                }
                "warning_request" -> {
                    showWarningNotification()
                }
                "stop_warning_request" -> {
                    showStopWarningNotification()
                    // Stop the ringtone
                    ringtone?.stop()
                }
                "audio_request" -> {
                    val deviceId = additionalInfo
                    //sendCommandToPersistentService("START_RECORDING", deviceId)
                    sendCommandToAudioService("START_RECORDING", deviceId)
                    Log.d("FCM_DEBUG", "4. Command sent to service")

                }
                "stop_audio_request" -> {
                    val deviceId = additionalInfo
                    sendCommandToAudioService("STOP_RECORDING", deviceId)

                }
                "send_task_request" ->{
                    if (additionalInfo.isNotEmpty()) {
                        val taskParts = additionalInfo.split("|")
                        if (taskParts.size == 2) {
                            val (taskName, taskTime) = taskParts
                            Log.d("Task", "taskName: $taskName, taskTime: $taskTime")
                            showTaskNotification(taskName, taskTime)
                            scheduleTaskReminder(taskName, taskTime)
                        } else {
                            Log.e("FCM", "Invalid task information format")
                        }
                    } else {
                        Log.e("FCM", "No additional task information provided")
                    }

                }
                "usage_stats_request" ->{
                    val usageTrackingManager = UsageTrackingManager(this)
                    usageTrackingManager.requestImmediateSync()
                    //usageTrackingManager.stopPeriodicTracking()
                }
                "app_limit_request" -> {
                    if (appRequestParts.size >= 5) {
                        val packageName = appRequestParts[1]
                        val dailyLimitMinutes = appRequestParts[2].toIntOrNull() ?: 0
                        val startTime = appRequestParts[3]
                        val endTime = appRequestParts[4]

                        val restartIntent = Intent(this, ServiceRestartReceiver::class.java).apply {
                            action = "RESTART_APP_LIMIT_SERVICE"
                        }
                        sendBroadcast(restartIntent)

                        handleAppLimitRequest(
                            AppLimit(
                                packageName = packageName,
                                dailyLimitMinutes = dailyLimitMinutes,
                                startTime = startTime,
                                endTime = endTime
                            )
                        )
                    }
                }
                "app_limit_stop" -> {
                    if (appRequestParts.size >= 2) {
                        val packageName = appRequestParts[1]
                        handleAppLimitStop(packageName)
                    }
                }
            }
        }
    }

    private fun handleAppLimitStop(packageName: String) {
        scope.launch {
            // Remove app limit
            appLimitManager.removeAppLimit(packageName)

            // 2. Kiểm tra còn app limits nào không
            val remainingLimits = appLimitManager.getAppLimits()

            if (remainingLimits.isEmpty()) {
                // Nếu không còn app limits nào, dừng service và worker
                stopService(Intent(this@MyFirebaseMessagingService, AppLimitService::class.java))
                manageAppLimitWorker(false)
            } else {
                // Nếu còn app limits khác, chỉ cần update notification
                val updateIntent = Intent(this@MyFirebaseMessagingService, AppLimitService::class.java).apply {
                    action = "UPDATE_NOTIFICATION"
                }
                startService(updateIntent)
            }

            // 3. Clear notifications của app bị xóa
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(packageName.hashCode())
        }
    }

    private fun manageAppLimitWorker(shouldRun: Boolean) {
        val workManager = WorkManager.getInstance(this)
        if (shouldRun) {
            AppLimitWorkerManager.scheduleWorker(
                this@MyFirebaseMessagingService,
            )
        } else {
            // Cancel worker
            workManager.cancelUniqueWork("app_monitoring_work")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleAppLimitRequest(appLimit: AppLimit) {
        Log.d("FCM", appLimit.toString())
        //kiem tra quyen
        if (!Settings.canDrawOverlays(this)) {
            // Nếu chưa có quyền, gửi notification yêu cầu người dùng cấp quyền
            showOverlayPermissionNotification()
            return
        }
        scope.launch{
            try {
                // Lưu vào database trước
                appLimitManager.saveAppLimit(appLimit)
                Log.d("FCM", "Saved app limit to database")

                // Verify database save
                val savedLimits = appLimitManager.getAppLimits()
                Log.d("FCM", "Current app limits in database: $savedLimits")

                // 2. Kiểm tra service có đang chạy không
                val serviceIntent = Intent(this@MyFirebaseMessagingService, AppLimitService::class.java)
                if (!isForegroundServiceRunning()) {
                    // Nếu chưa chạy, start mới
                    serviceIntent.action = "START_MONITORING"
                    startForegroundService(serviceIntent)
                    Log.d("FCM-Start","Start thành công rồi")
                } else {
                    // Nếu đang chạy, chỉ cần update notification
                    serviceIntent.action = "UPDATE_NOTIFICATION"
                    startService(serviceIntent)
                }

                // Start worker
                manageAppLimitWorker(true)
                Log.d("FCM", "Started app limit worker")
            } catch (e: Exception) {
                Log.e("FCM", "Error handling app limit request: ${e.message}", e)
            }
        }


    }

    private fun isForegroundServiceRunning(): Boolean {
        return ServiceUtils.isServiceRunning(
            this@MyFirebaseMessagingService,
            AppLimitService::class.java,
            checkForeground = true
        )
    }

    private fun showOverlayPermissionNotification() {
        val notificationIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = Uri.parse("package:$packageName")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, "app_limit_channel")
            .setContentTitle("Cần cấp quyền")
            .setContentText("Nhấn để cấp quyền hiển thị trên ứng dụng khác")
            .setSmallIcon(R.drawable.notification_icon)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(15, notification)
    }

    private fun sendCommandToAudioService(command: String, deviceId: String) {
        try {
            val intent = Intent("AUDIO_SERVICE_COMMAND")
            intent.setPackage(packageName)  // Add this line
            intent.putExtra("command", command)
            intent.putExtra("deviceId", deviceId)
            Log.d("FCM_DEBUG", "7. Broadcasting command: $command with deviceId: $deviceId")
            sendBroadcast(intent)
            Log.d("FCM_DEBUG", "8. Broadcast sent successfully")
        } catch (e: Exception) {
            Log.e("FCM_DEBUG", "Error sending broadcast: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun sendCommandToPersistentService(command: String, deviceId: String) {
        try {
            val intent = Intent("PERSISTENT_SERVICE_COMMAND")
            intent.setPackage(packageName)  // Add this line
            intent.putExtra("command", command)
            intent.putExtra("deviceId", deviceId)
            Log.d("FCM_DEBUG", "7. Broadcasting command: $command with deviceId: $deviceId")
            sendBroadcast(intent)
            Log.d("FCM_DEBUG", "8. Broadcast sent successfully")
        } catch (e: Exception) {
            Log.e("FCM_DEBUG", "Error sending broadcast: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun ensurePersistentServiceRunning() {
        try {
            val serviceIntent = Intent(this, AudioStreamingForegroundService::class.java)
            serviceIntent.action = "ENSURE_RUNNING"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d("FCM_DEBUG", "5a. Starting foreground service")
                startForegroundService(serviceIntent)
            } else {
                Log.d("FCM_DEBUG", "5b. Starting normal service")
                startService(serviceIntent)
            }
            Log.d("FCM_DEBUG", "6. Service start attempt completed")
        } catch (e: Exception) {
            Log.e("FCM_DEBUG", "Error starting service: ${e.message}")
            e.printStackTrace()
        }
    }


    private fun scheduleTaskReminder(taskName: String, taskTime: String) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, TaskReminderReceiver::class.java).apply {
            putExtra("taskName", taskName)
            putExtra("taskTime", taskTime)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            taskName.hashCode(), // Sử dụng hashCode của taskName làm requestCode
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        Log.d("task","$taskTime")

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
        val taskDateTime = sdf.parse(taskTime)?.time ?: return
        val reminderTime = taskDateTime - (5 * 60 * 1000) // 5 phút trước thời gian nhiệm vụ

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTime, pendingIntent)
    }

    private fun showTaskNotification(taskName: String, taskTime: String) {
        Log.d("ShowTask","$taskTime")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "task_notification_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Task Notifications", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        // Convert taskTime to more readable format for notification
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
        val outputFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val formattedTime = try {
            val date = inputFormat.parse(taskTime)
            outputFormat.format(date)
        } catch (e: Exception) {
            taskTime // fallback to original format if parsing fails
        }


        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("New Task Assigned")
            .setContentText("Task: $taskName at $formattedTime")
            .setSmallIcon(R.drawable.baseline_add_task_24) // Thay thế bằng icon thích hợp
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @SuppressLint("MissingPermission")
    private fun showWarningNotification() {
        // Create notification channel (only needed once)
        createNotificationChannel()
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Tăng âm lượng hệ thống lên mức tối đa
        audioManager.setStreamVolume(
            AudioManager.STREAM_ALARM,
            audioManager.getStreamMaxVolume(
                AudioManager.STREAM_ALARM),
            0)

        // Phát âm thanh cảnh báo ở mức to nhất
        val alarmSound: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ringtone = RingtoneManager.getRingtone(applicationContext, alarmSound).apply{
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            isLooping = true // Âm thanh sẽ lặp liên tục
            play()
        }

        // Rung điện thoại ở mức cao nhất
        vibrator = (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).apply{

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = longArrayOf(0, 1000, 500)
                vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                val pattern = longArrayOf(0, 1000, 500)
                vibrate(pattern, 0) // 0 means repeat indefinitely
            }
        }

        // Create intent to open MainActivity when notification is clicked
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE)

        // Build the notification
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle("Cảnh báo nguy hiểm")
            .setContentText("Con của bạn đang gặp nguy hiểm")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Con của bạn đang gặp nguy hiểm - Yêu cầu kiểm tra ngay lập tức!"))
            .setSound(null) // Set sound to null since we are handling it manually with ringtone
            .setAutoCancel(false)
            .setOngoing(true)


        // Show the notification
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.notify(1, notificationBuilder.build())

    }

    @SuppressLint("MissingPermission")
    private fun showStopWarningNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle("Cảnh báo nguy hiểm")
            .setContentText("Có vẻ như vấn đề đã được xử lí")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)

        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.notify(2, notificationBuilder.build())

        notificationManager.cancel(1)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Warning Request Channel"
            val descriptionText = "Channel for warning request notifications"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
