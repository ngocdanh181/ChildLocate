package com.example.childlocate.ui.parent.usagedetail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.childlocate.data.model.DayUsageStats
import com.example.childlocate.data.model.UsageStatsState
import com.example.childlocate.repository.UsageStatsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

class UsageStatsViewModel(application: Application): AndroidViewModel(application) {

    private val repository = UsageStatsRepository(application)
    private val _uiState = MutableStateFlow<UsageStatsState>(UsageStatsState.Loading)
    val uiState: StateFlow<UsageStatsState> = _uiState.asStateFlow()

    private var currentWeekOffset = 0
    private var currentWeekStartDate: Calendar = Calendar.getInstance()


    private val _selectedDay = MutableStateFlow<DayUsageStats?>(null)
    val selectedDay: StateFlow<DayUsageStats?> = _selectedDay.asStateFlow()

    init {
        // Đặt ngày bắt đầu là thứ 2 của tuần hiện tại
        currentWeekStartDate.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        currentWeekStartDate.set(Calendar.HOUR_OF_DAY, 0)
        currentWeekStartDate.set(Calendar.MINUTE, 0)
        currentWeekStartDate.set(Calendar.SECOND, 0)
        currentWeekStartDate.set(Calendar.MILLISECOND, 0)
    }

    fun loadUsageStats(childId: String) {
        viewModelScope.launch {
            _uiState.value = UsageStatsState.Loading
            try {

                val startDate = currentWeekStartDate.timeInMillis.toString()

                when (val result = repository.getWeeklyUsageStats(childId, startDate.toString())) {
                    is UsageStatsState.Success -> {
                        val weeklyStats = result.data
                        if (weeklyStats.dailyStats.isEmpty()) {
                            _uiState.value = UsageStatsState.Empty
                        } else {
                            _uiState.value = UsageStatsState.Success(weeklyStats)
                            // Select the most recent day by default
                            selectDay(weeklyStats.dailyStats.values.maxByOrNull { it.date }!!)
                            //dayStatsAdapter.setInitialSelection()
                        }
                    }
                    is UsageStatsState.Error -> {
                        _uiState.value = UsageStatsState.Error(result.message)
                    }

                    UsageStatsState.Empty -> TODO()
                    UsageStatsState.Loading -> TODO()
                }
            } catch (e: Exception) {
                _uiState.value = UsageStatsState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun selectDay(dayStats: DayUsageStats) {
        _selectedDay.value = dayStats
    }

    fun loadPreviousWeek(childId: String) {
        currentWeekStartDate.add(Calendar.WEEK_OF_YEAR, -1)
        loadUsageStats(childId)
        viewModelScope.launch {
            _uiState.collect { state ->
                if (state is UsageStatsState.Success) {
                    val sundayOfPreviousWeek = state.data.dailyStats.values.maxByOrNull { it.date }
                    sundayOfPreviousWeek?.let { selectDay(it) }
                }
            }
        }
    }

    fun loadNextWeek(childId: String) {
        // Lưu lại ngày đang chọn trước khi chuyển tuần
        val previousSelectedDay = _selectedDay.value

        currentWeekStartDate.add(Calendar.WEEK_OF_YEAR, 1)
        currentWeekStartDate.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        loadUsageStats(childId)
        // Sau khi load xong, chọn lại ngày thứ Hai của tuần mới
        viewModelScope.launch {
            _uiState.collect { state ->
                if (state is UsageStatsState.Success) {
                    val mondayOfNewWeek = state.data.dailyStats.values.minByOrNull { it.date }
                    mondayOfNewWeek?.let { selectDay(it) }
                }
            }
        }
    }

    // Thêm vào UsageStatsViewModel
    fun setAppPin(childId: String, pin: String) {
        viewModelScope.launch {
            try {
                repository.saveAppPin(childId,  pin)
                // Có thể thêm một state để thông báo thành công
            } catch (e: Exception) {
                // Xử lý lỗi
            }
        }
    }

    fun refreshData(childId: String) {
        viewModelScope.launch {
            repository.requestUsageUpdate(childId)
            loadUsageStats(childId)
        }
    }
}