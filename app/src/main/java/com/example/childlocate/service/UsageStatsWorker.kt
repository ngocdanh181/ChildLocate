package com.example.childlocate.service

// UsageStatsWorker.kt
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class UsageStatsWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            collectAndUploadUsageStats()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun collectAndUploadUsageStats() {
        val usageStatsManager = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()

        // Log thời gian hiện tại
        Log.d("UsageStats", "Current time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(calendar.time)}")
        // Lấy thời gian hiện tại
        val currentTime = calendar.timeInMillis
        // Set về 00:00:00 của thứ 2 tuần hiện tại
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        }



        // Lưu startTime là 00:00:00 của thứ 2
        val startTime = calendar.timeInMillis
        Log.d("UsageStats", "Start time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(startTime))}")

        val sharedPreferences = applicationContext.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val childId = sharedPreferences.getString("childId", null) ?: return
        for(i in 0..6){
            val dayStart = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            val dayEnd = calendar.timeInMillis.coerceAtMost(currentTime)
            val dayStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                dayStart,
                dayEnd
            )

            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(Date(dayStart))

            uploadDayStats(childId, dateStr, dayStats)
        }

    }


    private fun uploadDayStats(childId: String, dateStr: String, dayStats: List<UsageStats>) {
        val usageData = mutableMapOf<String, Any>()
        var totalTime = 0L

        dayStats
        .filter { it.totalTimeInForeground > 0 }
        .groupBy { it.packageName } // Group by package name để tổng hợp thời gian cho mỗi app
        .forEach { (packageName, stats) ->
            val totalAppTime = stats.sumOf { it.totalTimeInForeground }
            val lastTimeUsed = stats.maxOf { it.lastTimeUsed }
            val firstTimeUsed = stats.minOf { it.firstTimeStamp }

            val safePackageName = packageName.replace(".", "_")
            val appData = mapOf(
                "package_name" to packageName,
                "app_name" to getAppName(packageName),
                "usage_time" to totalAppTime,
                "last_time_used" to lastTimeUsed,
                "first_time_used" to firstTimeUsed
            )
            usageData[safePackageName] = appData
            totalTime += totalAppTime
        }

        val dayStatsData = mapOf(
            "date" to dateStr,
            "total_time" to totalTime,
            "apps" to usageData,
            "timestamp" to System.currentTimeMillis()
        )

        FirebaseDatabase.getInstance()
            .getReference("usage_stats")
            .child(childId)
            .child(dateStr)
            .setValue(dayStatsData)
            .addOnFailureListener {
                throw it
            }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val packageManager = applicationContext.packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}