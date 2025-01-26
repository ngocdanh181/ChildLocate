package com.example.childlocate.repository

import android.content.Context
import android.util.Log
import com.example.childlocate.data.api.RetrofitInstance
import com.example.childlocate.data.model.Data
import com.example.childlocate.data.model.FcmRequest
import com.example.childlocate.data.model.Message
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

class ChildRepository(private val context: Context) {
    suspend fun sendWarningToParent(projectId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                //val token = getToken() ?: return@withContext false
                val token= "fmzDzcVART-2-tX823qkcN:APA91bGUd3ehvnJSvt6_QRom0iii2yAbF83A53YU5vAgN8NsvoBc-RrOlBmEJWm3xjX6tJ4oL-8XPHYgiBcXAccnV-3bC6pM2giaISkI2cUaxJ3i_Dae-CBSN708icNUAiI_vu5234Me"
                val serviceAccount: InputStream = context.assets.open("childlocatedemo.json")
                val credentials = GoogleCredentials.fromStream(serviceAccount)
                    .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))

                val accessToken = credentials.refreshAccessToken().tokenValue
                val authHeader = "Bearer $accessToken"

                val request = FcmRequest(
                    message = Message(
                        token = token,
                        data = Data(request_type = "warning_request")
                    )
                )
                val response = RetrofitInstance.api.sendLocationRequest(authHeader, request)
                return@withContext response.isSuccessful
            } catch (e: IOException) {
                Log.d("FCM", "Error is: ${e.message}")
                return@withContext false
            }
        }
    }

    suspend fun stopWarningToParent(projectId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                //val token = getToken() ?: return@withContext false
                val token= "fmzDzcVART-2-tX823qkcN:APA91bGUd3ehvnJSvt6_QRom0iii2yAbF83A53YU5vAgN8NsvoBc-RrOlBmEJWm3xjX6tJ4oL-8XPHYgiBcXAccnV-3bC6pM2giaISkI2cUaxJ3i_Dae-CBSN708icNUAiI_vu5234Me"

                val serviceAccount: InputStream = context.assets.open("childlocatedemo.json")
                val credentials = GoogleCredentials.fromStream(serviceAccount)
                    .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))

                val accessToken = credentials.refreshAccessToken().tokenValue
                val authHeader = "Bearer $accessToken"

                val request = FcmRequest(
                    message = Message(
                        token = token,
                        data = Data(request_type = "stop_warning_request")
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


    private suspend fun getToken(): String? {
        return withContext(Dispatchers.IO) {
            try {
                FirebaseMessaging.getInstance().token.await()
            } catch (e: Exception) {
                Log.e("FCM", "Failed to get FCM token: ${e.message}")
                null
            }
        }
    }
}