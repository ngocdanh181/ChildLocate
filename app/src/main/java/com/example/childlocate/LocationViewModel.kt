package com.example.childlocate
// LocationViewModel.kt
import android.app.Application
import android.location.Address
import android.location.Geocoder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.childlocate.repository.SecondRepository
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.io.IOException
import java.util.Locale

class LocationViewModel(application: Application) : AndroidViewModel(application) {
    private val _locationInfo = MutableLiveData<Address?>()
    val locationInfo: MutableLiveData<Address?>
        get() = _locationInfo
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val locationRef: DatabaseReference = database.getReference("locations")

    private val repository = SecondRepository(application)
    private val sharedPreferences = application.getSharedPreferences("MyPrefs", Application.MODE_PRIVATE)
    private val childId: String? = sharedPreferences.getString("childId", null)


    private val _locationRequestStatus = MutableLiveData<Boolean>()
    val locationRequestStatus: LiveData<Boolean> get() = _locationRequestStatus



    //Ham chia se vi tri
    fun shareLocation(latitude: Double, longitude: Double) {
        val locationData = HashMap<String, Any>()
        locationData["latitude"] = latitude
        locationData["longitude"] = longitude
        Log.d("Location","$latitude")

        val userLocationRef = childId?.let { locationRef.child(it) }

        userLocationRef?.updateChildren(locationData)
    }


    fun getSharedLocation(userId: String) {
        locationRef.child(userId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val latitude = snapshot.child("latitude").value as Double
                val longitude = snapshot.child("longitude").value as Double
                getLocationInfo(latitude, longitude)
            }

            override fun onCancelled(error: DatabaseError) {
                _locationInfo.postValue(null)
            }
        })
    }

    private fun getLocationInfo(latitude: Double, longitude: Double) {
        val geocoder = Geocoder(getApplication<Application>().applicationContext, Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                _locationInfo.postValue(addresses[0])
            } else {
                _locationInfo.postValue(null)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            _locationInfo.postValue(null)
        }
    }
}
