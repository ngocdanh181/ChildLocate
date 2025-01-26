package com.example.childlocate.ui.parent.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.childlocate.data.model.LocationHistory
import com.example.childlocate.data.model.LocationHistoryRepository
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LocationHistoryRepository(application)

    private val _locationHistory = MutableLiveData<List<LocationHistory>>()
    val locationHistory: LiveData<List<LocationHistory>> get() = _locationHistory

    fun loadLocationHistory(userId: String) {
        viewModelScope.launch {
            val history = repository.getLocationHistory(userId)
            _locationHistory.value = history
        }
    }
}
