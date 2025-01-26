package com.example.childlocate.repository


import android.content.Context
import android.util.Log
import com.example.childlocate.data.api.RetrofitInstance
import com.example.childlocate.data.model.Data
import com.example.childlocate.data.model.FcmRequest
import com.example.childlocate.data.model.Message
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

class TaskRepository(private val context: Context) {

    private val database = FirebaseDatabase.getInstance().reference

    suspend fun sendTaskRequest(childId: String, taskName: String, taskTime: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val token = getToken(childId) ?: return@withContext false
                Log.d("FCM", token)

                val serviceAccount: InputStream = context.assets.open("childlocatedemo.json")
                val credentials = GoogleCredentials.fromStream(serviceAccount)
                    .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))

                val accessToken = credentials.refreshAccessToken().tokenValue
                val authHeader = "Bearer $accessToken"


                val requestType = "send_task_request"
                val additionalInfo = "$taskName|$taskTime"


                val request = FcmRequest(
                    message = Message(
                        token = token,
                        data = Data(request_type = "$requestType|$additionalInfo")
                    )
                )

                Log.d("FCMTask","$request")

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
                database.child("users").child(childId).child("deviceToken").get().await().getValue(String::class.java)

            } catch (e: Exception) {
                Log.e("FCM", "Failed to get FCM token: ${e.message}")
                null
            }
        }
    }





}
