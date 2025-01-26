package com.example.childlocate.data.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize


sealed class UsageStatsState {
    object Loading : UsageStatsState()
    object Empty : UsageStatsState()
    data class Success(val data: WeeklyUsageStats) : UsageStatsState()
    data class Error(val message: String) : UsageStatsState()
}

data class WeeklyUsageStats(
    val dailyStats: Map<String, DayUsageStats>
)

data class DayUsageStats(
    val date: String,
    val totalTime: Long,
    val appUsageList: List<AppUsageInfo>
)
@Parcelize
data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val usageTime: Long,
    val lastTimeUsed: Long
): Parcelable

data class AppLimit(
    val packageName: String,
    val dailyLimitMinutes: Int,
    val startTime: String,
    val endTime: String,
    val isEnabled: Boolean = true
)


sealed class AppLimitDialogState {
    object Loading : AppLimitDialogState()
    data class Success(val currentLimit: AppLimit?) : AppLimitDialogState()
    data class Error(val message: String) : AppLimitDialogState()
}