package com.example.scrolltrack.ui.main

import android.app.Application
import android.app.AppOpsManager // Import for AppOpsManager
import android.content.Context // Import for Context.APP_OPS_SERVICE
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Process // Import for Process.myUid()
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrolltrack.data.AppScrollData // Ensured this import if mapToAppScrollUiItems uses it directly
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.db.DailyAppUsageRecord // For type from repository
import com.example.scrolltrack.util.ConversionUtil
import com.example.scrolltrack.util.DateUtil
import com.example.scrolltrack.util.GreetingUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Data class for Scroll UI (remains the same)
data class AppScrollUiItem(
    val id: String,
    val appName: String,
    val icon: Drawable?,
    val totalScroll: Long,
    val packageName: String
)

// Data class for Usage UI (remains the same)
data class AppUsageUiItem(
    val id: String, // packageName
    val appName: String,
    val icon: Drawable?,
    val usageTimeMillis: Long,
    val formattedUsageTime: String,
    val packageName: String
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: ScrollDataRepository,
    private val application: Application
) : ViewModel() {

    private val _todayDateString = DateUtil.getCurrentDateString() // Fixed for "today's" data

    // --- State for Date Picker and Historical View ---
    private val _selectedDateForHistory = MutableStateFlow(_todayDateString) // Default to today
    val selectedDateForHistory: StateFlow<String> = _selectedDateForHistory.asStateFlow()

    // --- Greeting ---
    val greeting: StateFlow<String> = flow { emit(GreetingUtil.getGreeting()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Hello! 👋")

    // --- Helper function to filter and map DailyAppUsageRecords to AppUsageUiItems ---
    private suspend fun processUsageRecords(records: List<DailyAppUsageRecord>): List<AppUsageUiItem> {
        return withContext(Dispatchers.IO) {
            records.mapNotNull { record ->
                try {
                    val pm = application.packageManager
                    val appInfo = pm.getApplicationInfo(record.packageName, 0)
                    // Basic filtering: ignore self and non-launchable system apps (similar to before)
                    if (record.packageName == application.packageName) return@mapNotNull null
                    if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) &&
                        (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0) &&
                        pm.getLaunchIntentForPackage(record.packageName) == null) {
                        return@mapNotNull null
                    }

                    AppUsageUiItem(
                        id = record.packageName,
                        appName = pm.getApplicationLabel(appInfo).toString(),
                        icon = pm.getApplicationIcon(record.packageName),
                        usageTimeMillis = record.usageTimeMillis,
                        formattedUsageTime = DateUtil.formatDuration(record.usageTimeMillis),
                        packageName = record.packageName
                    )
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.w("MainViewModel", "Package info not found for usage item ${record.packageName}, creating fallback UI item.")
                    // Fallback for apps that might have been uninstalled but data exists
                    AppUsageUiItem(record.packageName, record.packageName.substringAfterLast('.', record.packageName), null, record.usageTimeMillis, DateUtil.formatDuration(record.usageTimeMillis), record.packageName)
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Error processing usage app data for ${record.packageName}", e)
                    null
                }
            }.sortedByDescending { it.usageTimeMillis }
        }
    }

    private fun formatTotalUsageTime(totalMillis: Long?): String {
        return when {
            totalMillis == null -> "N/A" // Explicitly handle null, perhaps permission not yet granted or error
            totalMillis <= 0L && isUsageStatsPermissionGrantedByAppOps() -> "0m"
            totalMillis <= 0L -> "N/A" // No permission or no usage
            else -> DateUtil.formatDuration(totalMillis)
        }
    }


    // --- Data for TODAY'S SUMMARY (Main Screen) ---
    val todaysAppUsageUiList: StateFlow<List<AppUsageUiItem>> =
        repository.getDailyUsageRecordsForDate(_todayDateString)
            .map { records -> processUsageRecords(records) } // Simplified from flatMapLatest if processUsageRecords is not returning a Flow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val totalPhoneUsageTodayFormatted: StateFlow<String> =
        todaysAppUsageUiList.map { appUsageList ->
            val totalMillis = appUsageList.sumOf { it.usageTimeMillis }
            formatTotalUsageTime(totalMillis)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "Loading...")

    // Added StateFlow for total phone usage in milliseconds for today
    val totalPhoneUsageTodayMillis: StateFlow<Long> = 
        todaysAppUsageUiList.map { appUsageList ->
            appUsageList.sumOf { it.usageTimeMillis }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0L)

    val aggregatedScrollDataToday: StateFlow<List<AppScrollUiItem>> =
        repository.getAggregatedScrollDataForDate(_todayDateString)
            .map { appScrollDataList -> mapToAppScrollUiItems(appScrollDataList) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val totalScrollToday: StateFlow<Long> =
        repository.getTotalScrollForDate(_todayDateString)
            .map { it ?: 0L }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0L)

    val scrollDistanceTodayFormatted: StateFlow<Pair<String, String>> =
        totalScrollToday.map {
            ConversionUtil.formatScrollDistance(it, application.applicationContext)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "0.00 km" to "0.00 miles")


    // --- Data for HISTORICAL USAGE SCREEN (Reacts to _selectedDateForHistory) ---
    val dailyAppUsageForSelectedDateHistory: StateFlow<List<AppUsageUiItem>> =
        selectedDateForHistory.flatMapLatest { dateString ->
            repository.getDailyUsageRecordsForDate(dateString)
                .map { dailyUsageRecords -> processUsageRecords(dailyUsageRecords) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val totalUsageTimeForSelectedDateHistoryFormatted: StateFlow<String> =
        dailyAppUsageForSelectedDateHistory.map { appUsageList ->
            val totalMillis = appUsageList.sumOf { it.usageTimeMillis }
            formatTotalUsageTime(totalMillis)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "Loading...")


    // Helper to map ScrollData to ScrollUiItem
    private suspend fun mapToAppScrollUiItems(appScrollDataList: List<AppScrollData>): List<AppScrollUiItem> {
        return withContext(Dispatchers.IO) {
            appScrollDataList.mapNotNull { appData ->
                try {
                    val pm = application.packageManager
                    val appInfo = pm.getApplicationInfo(appData.packageName, 0)
                    AppScrollUiItem(
                        id = appData.packageName,
                        appName = pm.getApplicationLabel(appInfo).toString(),
                        icon = pm.getApplicationIcon(appData.packageName),
                        totalScroll = appData.totalScroll,
                        packageName = appData.packageName
                    )
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.w("MainViewModel", "Package info not found for scroll item ${appData.packageName}, creating fallback UI item.")
                    AppScrollUiItem(appData.packageName, appData.packageName.substringAfterLast('.', appData.packageName), null, appData.totalScroll, appData.packageName)
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Error processing scroll app data for ${appData.packageName}", e)
                    null
                }
            }
        }
    }

    // Corrected helper to check permission
    private fun isUsageStatsPermissionGrantedByAppOps(): Boolean {
        val appOpsManager = application.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager // Correct way to get service
        return if (appOpsManager != null) {
            val mode = appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), // Correct: android.os.Process
                application.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } else {
            Log.w("MainViewModel", "AppOpsManager was null, cannot check usage stats permission.")
            false
        }
    }

    fun updateSelectedDateForHistory(dateMillis: Long) {
        val newDateString = DateUtil.formatDate(dateMillis)
        if (_selectedDateForHistory.value != newDateString) {
            _selectedDateForHistory.value = newDateString
        }
    }

    fun resetSelectedDateToToday() {
        Log.d("MainViewModel", "Resetting selectedDateForHistory to today: $_todayDateString")
        _selectedDateForHistory.value = _todayDateString
    }

    fun refreshDataForToday() { // Renamed for clarity
        viewModelScope.launch {
            // This primarily serves to re-trigger the _todayDateString dependent flows if needed,
            // or to explicitly call repository functions that aren't flows.
            // Since our "today" flows are based on a fixed _todayDateString and repository Flows,
            // they will update when the underlying database changes.
            // This log confirms the refresh was called.
            Log.d("MainViewModel", "Explicit refresh for today's data triggered. Attempting to update today's stats in DB.")
            val success = repository.updateTodayAppUsageStats()
            if (success) {
                Log.d("MainViewModel", "Successfully updated today's app usage stats in the database.")
            } else {
                Log.w("MainViewModel", "Failed to update today's app usage stats in the database.")
            }
            // If you had a non-Flow data source for today, you'd re-fetch it here.
            // For example, if getTotalUsageTimeMillisForDate was not a flow:
            // val usageMillis = repository.getTotalUsageTimeMillisForDate(_todayDateString)
            // _totalPhoneUsageTodayFormatted.value = formatTotalUsageTime(usageMillis) // If it was a simple MutableStateFlow
        }
    }

    fun performHistoricalUsageDataBackfill(days: Int = 10) {
        viewModelScope.launch {
            Log.i("MainViewModel", "Starting historical usage data backfill for $days days.")
            val success = repository.backfillHistoricalAppUsageData(days)
            if (success) {
                Log.i("MainViewModel", "Historical data backfill completed successfully.")
                // Refresh current views that might be affected
                val currentHistoryDate = _selectedDateForHistory.value
                if (currentHistoryDate != _todayDateString) { // Avoid double refresh if viewing today
                     _selectedDateForHistory.value = "" // Force re-emission by changing value
                     _selectedDateForHistory.value = currentHistoryDate
                }
                refreshDataForToday() // Always refresh today's summary
            } else {
                Log.w("MainViewModel", "Historical data backfill failed or had issues.")
            }
        }
    }
}
