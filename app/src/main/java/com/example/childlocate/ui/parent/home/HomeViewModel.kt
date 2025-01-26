package com.example.childlocate.ui.parent.home

import android.app.Application
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.childlocate.data.model.Child
import com.example.childlocate.repository.SecondRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.Locale

// LocationState.kt
sealed class LocationState {
    data object Idle : LocationState()
    data object Loading : LocationState()
    data class Success(
        val address: Address,
        val timestamp: Long,
        val accuracy: Float
    ) : LocationState()
    data class Error(val message: String, val isRetryable: Boolean = true) : LocationState()
    data object GpsDisabled : LocationState()
    data object Timeout : LocationState()
    data class StaleData(val lastUpdateTime: Long) : LocationState()
}

class HomeViewModel (application: Application) : AndroidViewModel(application) {
    private val database = FirebaseDatabase.getInstance().reference
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val locationRef: DatabaseReference = database.child("locations")
    private val repository = SecondRepository(application)
    private val timeout = 30000L // 30 seconds
    private val _child = MutableLiveData<Child?>()
    val child: LiveData<Child?> get() = _child

    private val _children = MutableLiveData<List<Child>>()
    val children: LiveData<List<Child>> get() = _children

    private val _selectedChild = MutableLiveData<Child?>()
    val selectedChild: LiveData<Child?> get() = _selectedChild

    private val _locationState = MutableStateFlow<LocationState>(LocationState.Idle)
    val locationState: StateFlow<LocationState> = _locationState.asStateFlow()

    private val _gpsStatus = MutableStateFlow(false)
    val gpsStatus: StateFlow<Boolean> = _gpsStatus.asStateFlow()

    private var locationUpdateJob: Job? = null

    private val _parentId = MutableLiveData<String?>()
    val parentId: LiveData<String?> get() = _parentId

    private val _locationInfo = MutableLiveData<Address?>()
    val locationInfo: MutableLiveData<Address?>
        get() = _locationInfo

    private val _avatarUrl = MutableLiveData<String?>()
    val avatarUrl: LiveData<String?> get() = _avatarUrl

    private val _locationRequestStatus = MutableLiveData<Boolean>()

    private val _familyId = MutableLiveData<String?>()
    val familyId: LiveData<String?> get() = _familyId


    init {
        fetchAvatarUrl()
        fetchFamilyId()
        checkGpsStatus()
    }

    private fun checkGpsStatus(){
        val locationManager = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        _gpsStatus.value = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

        // Monitor GPS status changes
        viewModelScope.launch {
            while(isActive) {
                _gpsStatus.value = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                delay(5000) // Check every 5 seconds
            }
        }
    }

    fun sendLocationRequest(projectId: String, childId: String) {
        locationUpdateJob?.cancel()
        locationUpdateJob = viewModelScope.launch {
            try {
                if (!_gpsStatus.value) {
                    _locationState.value = LocationState.GpsDisabled
                    return@launch
                }

                _locationState.value = LocationState.Loading
                val success = repository.sendLocationRequest(projectId, childId)

                if (success) {
                    withTimeout(timeout) {
                        getSharedLocation(childId)
                    }
                } else {
                    _locationState.value = LocationState.Error(
                        "Không thể gửi yêu cầu vị trí",
                        isRetryable = true
                    )
                }
            } catch (e: TimeoutCancellationException) {
                _locationState.value = LocationState.Timeout
            } catch (e: Exception) {
                _locationState.value = LocationState.Error(
                    "Lỗi: ${e.message}",
                    isRetryable = true
                )
            }
        }
    }

    fun sendStopLocationRequest(projectId: String, childId:String){
        viewModelScope.launch {
            val success = repository.sendStopLocationRequest(projectId,childId)
            _locationRequestStatus.postValue(success)
        }
    }

    fun getSharedLocation(userId: String) {
        try {
            _locationState.value = LocationState.Loading

            locationRef.child(userId).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        _locationState.value = LocationState.Error(
                            "Không tìm thấy dữ liệu vị trí của trẻ",
                            isRetryable = false
                        )
                        return
                    }

                    val latitude = snapshot.child("latitude").value
                    val longitude = snapshot.child("longitude").value
                    val timestamp = snapshot.child("timestamp").value as? Long
                    val accuracy = snapshot.child("accuracy").value as? Float

                    // Validate data
                    if (latitude == null || longitude == null) {
                        _locationState.value = LocationState.Error(
                            "Trẻ chưa chia sẻ vị trí",
                            isRetryable = true
                        )
                        return
                    }

                    // Check timestamp
                    if (timestamp != null) {
                        val timeDiff = System.currentTimeMillis() - timestamp
                        if (timeDiff > 5 * 60 * 1000) { // 5 minutes
                            _locationState.value = LocationState.StaleData(timestamp)
                            return
                        }
                    }

                    try {
                        val lat = latitude.toString().toDouble()
                        val lon = longitude.toString().toDouble()

                        // Validate coordinates
                        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                            _locationState.value = LocationState.Error(
                                "Tọa độ không hợp lệ",
                                isRetryable = false
                            )
                            return
                        }

                        // Check accuracy if available
                        if (accuracy != null && accuracy > 100) { // 100 meters threshold
                            _locationState.value = LocationState.Error(
                                "Độ chính xác vị trí thấp: ${accuracy.toInt()}m",
                                isRetryable = true
                            )
                            return
                        }

                        getLocationInfo(lat, lon, timestamp ?: System.currentTimeMillis(), accuracy ?: 0f)
                    } catch (e: NumberFormatException) {
                        _locationState.value = LocationState.Error(
                            "Dữ liệu vị trí không hợp lệ",
                            isRetryable = false
                        )
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    _locationState.value = LocationState.Error(
                        "Lỗi kết nối: ${error.message}",
                        isRetryable = true
                    )
                }
            })
        } catch (e: Exception) {
            _locationState.value = LocationState.Error(
                "Lỗi không xác định: ${e.message}",
                isRetryable = true
            )
        }
    }

    private fun getLocationInfo(latitude: Double, longitude: Double, timestamp: Long, accuracy: Float) {
        val geocoder = Geocoder(getApplication(), Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                _locationState.value = LocationState.Success(address, timestamp, accuracy)
            } else {
                _locationState.value = LocationState.Error(
                    "Không thể xác định địa chỉ từ tọa độ",
                    isRetryable = true
                )
            }
        } catch (e: IOException) {
            _locationState.value = LocationState.Error(
                "Lỗi khi lấy thông tin địa chỉ",
                isRetryable = true
            )
        }
    }

    private fun fetchFamilyId() {
        viewModelScope.launch{
            val uid = firebaseAuth.currentUser?.uid ?: throw Exception("User ID not found")
            val userId = getUserIdFromUid(uid) ?: throw Exception("User ID not found in the database")
            _parentId.value = userId
            database.child("users").child(userId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            val familyId = snapshot.child("familyId").getValue(String::class.java)
                            _familyId.value = familyId
                            if (familyId != null) {
                                fetchChildren(familyId)
                                Log.d("familyId","($familyId)")
                            }
                        }
                    }

                    override fun onCancelled(databaseError: DatabaseError) {

                    }
                })
        }
    }
    private suspend fun getUserIdFromUid(uid: String): String? {
        // Lấy userId từ nhánh "userIds" theo uid
        return database.child("userIds").child(uid).get().await().getValue(String::class.java)
    }

    fun fetchChildren(familyId: String) {
        database.child("families").child(familyId).child("members")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val childrenList = mutableListOf<Child>()
                    for (childSnapshot in snapshot.children) {
                        val role = childSnapshot.child("role").getValue(String::class.java)
                        if (role == "child") {
                            val childId = childSnapshot.key // Lấy childId từ nhánh "members"
                            val childName = childSnapshot.child("name").getValue(String::class.java) ?: ""

                            val child = childId?.let { Child(childId = it, childName = childName) }
                            Log.d("HomeViewModel","$child")
                            child?.let { childrenList.add(it) }
                        }
                    }
                    _children.value = childrenList
                    if (childrenList.isNotEmpty()) {
                        _selectedChild.value = childrenList[0]
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Handle error
                }
            })

    }




    fun selectChild(child: Child) {
        _selectedChild.value = child
    }


    fun fetchAvatarUrl() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val userRef = database.child("users").child(user.uid).child("avatarUrl")

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val avatarUrl = snapshot.value as? String
                _avatarUrl.value = avatarUrl
                Log.d("MapsViewModel", "Avatar URL: $avatarUrl")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("MapsViewModel", "Error fetching avatar URL: ${error.message}")
                _avatarUrl.value = null // hoặc bạn có thể để là "" nếu muốn
            }
        })
    }



}
