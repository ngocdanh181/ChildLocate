package com.example.childlocate.ui.parent.timelimit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.childlocate.data.model.AppLimit
import com.example.childlocate.data.model.AppLimitDialogState
import com.example.childlocate.repository.TimeLimitRepository
import kotlinx.coroutines.launch

class TimeLimitViewModel(application: Application): AndroidViewModel(application)
{
    private val _state = MutableLiveData<AppLimitDialogState>()
    val state: LiveData<AppLimitDialogState> = _state
    private val repository = TimeLimitRepository(application)

    fun loadAppLimits(packageName: String) {
        viewModelScope.launch {
            _state.value = AppLimitDialogState.Loading
            try {
                val appLimit = repository.getAppLimit(packageName)
                _state.value = AppLimitDialogState.Success(appLimit)
            } catch (e: Exception) {
                _state.value = AppLimitDialogState.Error(e.message ?: "Unknown error")
            }
        }
    }
    // Thêm hàm để set childId
    fun setChildId(childId: String) {
        repository.setChildId(childId)
    }

    fun setAppLimit(appLimit: AppLimit) {
        viewModelScope.launch {
            _state.value = AppLimitDialogState.Loading
            try {
                repository.setAppLimit(appLimit)
                _state.value = AppLimitDialogState.Success(appLimit)
            } catch (e: Exception) {
                _state.value = AppLimitDialogState.Error(e.message ?: "Unknown error")
            }
        }
    }
}