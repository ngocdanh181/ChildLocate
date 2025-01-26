package com.example.childlocate.repository

import android.content.Context
import android.util.Log
import com.example.childlocate.data.api.RetrofitInstance
import com.example.childlocate.data.model.AppLimit
import com.example.childlocate.data.model.Data
import com.example.childlocate.data.model.FcmRequest
import com.example.childlocate.data.model.Message
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.InputStream

class TimeLimitRepository(private val context: Context) {
    private val database = FirebaseDatabase.getInstance()
    private var childId: String? = null

    fun setChildId(id: String) {
        this.childId = id
    }

    suspend fun getAppLimit(packageName: String): AppLimit? {
        return withContext(Dispatchers.IO) {
            try {

                val encodedPackageName = packageName.replace(".", "_")


                val snapshot = childId?.let {
                    database.getReference("app_limits")
                        .child(it)
                        .child(encodedPackageName)
                        .get()
                        .await()
                }


                if (snapshot!!.exists()) {
                    AppLimit(
                        packageName = packageName,
                        dailyLimitMinutes = snapshot.child("dailyLimitMinutes").getValue(Int::class.java) ?: 0,
                        startTime = snapshot.child("startTime").getValue(String::class.java) ?: "08:00",
                        endTime = snapshot.child("endTime").getValue(String::class.java) ?: "21:00",
                        isEnabled = snapshot.child("isEnabled").getValue(Boolean::class.java) ?: true
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }

    suspend fun setAppLimit(appLimit: AppLimit): Boolean {
        return withContext(Dispatchers.IO) {
            try {

                val encodedPackageName = appLimit.packageName.replace(".", "_")
                // 1. Lưu giới hạn vào Firebase
                childId?.let {
                    database.getReference("app_limits")
                        .child(it)
                        .child(encodedPackageName)
                        .setValue(appLimit)
                        .await()
                }

                // 2. Gửi FCM request đến thiết bị của trẻ
                childId?.let { sendAppLimitRequest(it, appLimit) }
                true
            } catch (e: Exception) {
                throw e
            }
        }
    }

    private suspend fun sendAppLimitRequest(childId: String, appLimit: AppLimit): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Lấy token của thiết bị
                val token = getDeviceToken(childId) ?: throw Exception("Device token not found")
                Log.d("FCM",token)

                // 2. Lấy credentials để gửi FCM
                val serviceAccount: InputStream = context.assets.open("childlocatedemo.json")
                val credentials = GoogleCredentials.fromStream(serviceAccount)
                    .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))
                val accessToken = credentials.refreshAccessToken().tokenValue

                // Tạo request type khác nhau dựa vào trạng thái enabled
                val requestType = if (appLimit.isEnabled) {
                    "app_limit_request|${appLimit.packageName}|" +
                            "${appLimit.dailyLimitMinutes}|${appLimit.startTime}|${appLimit.endTime}"
                } else {
                    "app_limit_stop|${appLimit.packageName}"
                }


                // 3. Tạo request
                val request = FcmRequest(
                    message = Message(
                        token = token,
                        data = Data(
                            request_type = requestType
                        )
                    )
                )

                // 4. Gửi request
                val response = RetrofitInstance.api.sendLocationRequest(
                    "Bearer $accessToken",
                    request
                )

                response.isSuccessful
            } catch (e: Exception) {
                throw e
            }
        }
    }

    private suspend fun getDeviceToken(childId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                database.getReference("users")
                    .child(childId)
                    .child("deviceToken")
                    .get()
                    .await()
                    .getValue(String::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}