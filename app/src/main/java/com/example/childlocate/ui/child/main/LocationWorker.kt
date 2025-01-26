package com.example.childlocate.ui.child.main

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.childlocate.MyFirebaseManager
import com.example.childlocate.R
import com.example.childlocate.unuse.CHANNEL_ID
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class LocationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRef: DatabaseReference

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        MyFirebaseManager.initFirebase(applicationContext)
        val firebaseApp = MyFirebaseManager.getFirebaseApp()
        if (firebaseApp == null) {
            return@withContext Result.failure()
        } else {
            val database: FirebaseDatabase = FirebaseDatabase.getInstance()
            locationRef = database.getReference("location_history")
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
            getLastLocation(applicationContext, fusedLocationClient)
            Log.d("Location","Getted 15 minutes Location")
            return@withContext Result.success()
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastLocation(context: Context, fusedLocationClient: FusedLocationProviderClient) {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    Log.d("Location","Thanh cong roi")
                    shareLocation(location.latitude, location.longitude)
                }
            }
    }

    private fun shareLocation(latitude: Double, longitude: Double) {
        val userId = "1"
        val locationData = HashMap<String, Any>()
        locationData["latitude"] = latitude
        locationData["longitude"] = longitude
        Log.d("Location", "Latitude: $latitude, Longitude: $longitude")

        val timeStamp = System.currentTimeMillis()

        val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
        val formattedTime = dateFormat.format(Date(timeStamp))

        val formattedTimeSingleLine = formattedTime.replace("\n", "")

        val locationListRef = FirebaseDatabase.getInstance().getReference("locations/$userId/locationsList")
        val childUpdates = HashMap<String, Any>()
        childUpdates[formattedTimeSingleLine] = locationData
        locationListRef.updateChildren(childUpdates)
        showNotification("Location shared", "Location data has been shared successfully")
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(title: String, content: String){
        createNotificationChannel()

        val notificationBuilder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)

        with(NotificationManagerCompat.from(applicationContext)) {
            notify(notificationId, notificationBuilder.build())
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "Location Service"
            val channelDescription = "Location service notifications"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, channelName, importance).apply {
                description = channelDescription
            }

            val notificationManager: NotificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }



    companion object {
        const val TAG = "LocationWorker"
        const val notificationId= 109
    }


}
