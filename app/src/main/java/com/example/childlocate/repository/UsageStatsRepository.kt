package com.example.childlocate.repository

import android.content.Context
import android.util.Log
import com.example.childlocate.data.api.RetrofitInstance
import com.example.childlocate.data.model.AppUsageInfo
import com.example.childlocate.data.model.Data
import com.example.childlocate.data.model.DayUsageStats
import com.example.childlocate.data.model.FcmRequest
import com.example.childlocate.data.model.Message
import com.example.childlocate.data.model.UsageStatsState
import com.example.childlocate.data.model.WeeklyUsageStats
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class UsageStatsRepository(private val context: Context) {

    private val database = FirebaseDatabase.getInstance()

    suspend fun requestUsageUpdate(childId:String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val token = getToken(childId) ?: return@withContext false
                Log.d("UsageStatsRepository", token)
                val serviceAccount: InputStream = context.assets.open("childlocatedemo.json")
                val credentials = GoogleCredentials.fromStream(serviceAccount)
                    .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))

                val accessToken = credentials.refreshAccessToken().tokenValue
                val authHeader = "Bearer $accessToken"

                val request = FcmRequest(
                    message = Message(
                        token = token,
                        data = Data(request_type = "usage_stats_request")
                    )
                )

                val response = RetrofitInstance.api.sendLocationRequest(authHeader, request)
                return@withContext response.isSuccessful
            } catch (e: IOException) {
                Log.d("FCM", "Error: ${e.message}")
                return@withContext false
            }
        }
    }

    private suspend fun getToken(childId:String): String? {
        return withContext(Dispatchers.IO) {
            try {
                database.getReference("users").child(childId).child("deviceToken").get().await().getValue(String::class.java)

            } catch (e: Exception) {
                Log.e("FCM", "Failed to get FCM token: ${e.message}")
                null
            }
        }
    }
    suspend fun getWeeklyUsageStats(childId: String, startDate: String): UsageStatsState {
        return withContext(Dispatchers.IO) {
            try {
                val calendar = Calendar.getInstance()

                /*// Đặt ngày thành Thứ Hai của tuần hiện tại
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfWeek = calendar.timeInMillis

                // Đặt ngày thành Chủ Nhật của tuần hiện tại
                calendar.add(Calendar.DAY_OF_WEEK, 6)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val endOfWeek = calendar.timeInMillis*/

                val snapshot = database.getReference("usage_stats")
                    .child(childId)
                    .get()
                    .await()

                val dailyStats = mutableMapOf<String, DayUsageStats>()
                // Ensure we have entries for all days of the week
                calendar.timeInMillis = startDate.toLong()
                for(i in 0..6){
                    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .format(calendar.time)

                    val daySnapshot = snapshot.child(dateStr)
                    if (daySnapshot.exists()){
                        val totalTime = daySnapshot.child("total_time").getValue(Long::class.java) ?: 0L
                        val appsList = mutableListOf<AppUsageInfo>()

                        daySnapshot.child("apps").children.forEach { appSnapshot ->
                            appsList.add(
                                AppUsageInfo(
                                    packageName = appSnapshot.child("package_name").getValue(String::class.java) ?: "",
                                    appName = appSnapshot.child("app_name").getValue(String::class.java) ?: "",
                                    usageTime = appSnapshot.child("usage_time").getValue(Long::class.java) ?: 0L,
                                    lastTimeUsed = appSnapshot.child("last_time_used").getValue(Long::class.java) ?: 0L
                                )
                            )
                        }

                        dailyStats[dateStr] = DayUsageStats(
                            date = dateStr,
                            totalTime = totalTime,
                            appUsageList = appsList.sortedByDescending { it.usageTime }
                        )
                    } else {
                        // Add empty stats for days with no data
                        dailyStats[dateStr] = DayUsageStats(
                            date = dateStr,
                            totalTime = 0L,
                            appUsageList = emptyList()
                        )
                    }

                    calendar.add(Calendar.DAY_OF_YEAR, 1)


                }

                UsageStatsState.Success(WeeklyUsageStats(dailyStats))
            } catch (e: Exception) {
                UsageStatsState.Error(e.message ?: "Unknown error")
            }
        }
    }

    // Thêm vào UsageStatsRepository


    suspend fun saveAppPin(childId: String, pin: String) {
        withContext(Dispatchers.IO) {
            try {
                database.getReference("app_pins")
                    .child(childId)
                    .setValue(pin)
                    .await()
            } catch (e: Exception) {
                throw e
            }
        }
    }




}