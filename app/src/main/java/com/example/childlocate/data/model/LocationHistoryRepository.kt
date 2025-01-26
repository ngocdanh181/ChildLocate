package com.example.childlocate.data.model

import android.content.Context
import android.location.Geocoder
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import java.util.Locale

class LocationHistoryRepository(private val context: Context) {

    private val database = FirebaseDatabase.getInstance().reference

    suspend fun getLocationHistory(userId: String): List<LocationHistory> {
        val locationListRef = database.child("locations/$userId/locationsList")
        val snapshot = locationListRef.get().await()

        val locationList = mutableListOf<LocationHistory>()
        for (dataSnapshot in snapshot.children) {
            val timestamp = dataSnapshot.key ?: ""
            val locationData = dataSnapshot.getValue(LocationHistory::class.java)
            if (locationData != null) {
                val address = getAddress(locationData.latitude, locationData.longitude)
                locationList.add(LocationHistory(timestamp, locationData.latitude, locationData.longitude, address))
            }
        }
        return locationList
    }

    private fun getAddress(latitude: Double, longitude: Double): String {
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses = geocoder.getFromLocation(latitude, longitude, 1)
        return addresses?.get(0)?.getAddressLine(0) ?: "Unknown Location"
    }
}
