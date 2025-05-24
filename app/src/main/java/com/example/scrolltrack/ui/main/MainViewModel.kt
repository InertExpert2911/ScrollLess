package com.example.scrolltrack.ui.main

import android.app.Application
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrolltrack.data.ScrollDataRepository
// import com.example.scrolltrack.data.AppScrollData // Not directly used in this VM if AppScrollUiItem is used
import com.example.scrolltrack.util.ConversionUtil
import com.example.scrolltrack.util.DateUtil
import com.example.scrolltrack.util.GreetingUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Data class AppScrollUiItem (keep as is)
data class AppScrollUiItem(
    val id: String,
    val appName: String,
    val icon: Drawable?,
    val totalScroll: Long,
    val packageName: String
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: ScrollDataRepository,
    private val application: Application
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(DateUtil.getCurrentDateString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    val greeting: StateFlow<String> = flow { emit(GreetingUtil.getGreeting()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Hello! 👋")

    val aggregatedScrollDataForSelectedDate: StateFlow<List<AppScrollUiItem>> =
        selectedDate.flatMapLatest { dateString ->
            repository.getAggregatedScrollDataForDate(dateString)
                .map { appScrollDataList ->
                    withContext(Dispatchers.IO) {
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
                                Log.w("MainViewModel", "Package info not found for ${appData.packageName}, using fallback.")
                                AppScrollUiItem(
                                    id = appData.packageName,
                                    appName = appData.packageName.substringAfterLast('.'),
                                    icon = null,
                                    totalScroll = appData.totalScroll,
                                    packageName = appData.packageName
                                )
                            } catch (e: Exception) {
                                Log.e("MainViewModel", "Error processing app data for ${appData.packageName}", e)
                                null
                            }
                        }
                    }
                }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val totalScrollForSelectedDate: StateFlow<Long> =
        selectedDate.flatMapLatest { dateString ->
            repository.getTotalScrollForDate(dateString).map { it ?: 0L }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = 0L
        )

    val totalUsageTimeFormatted: StateFlow<String> =
        selectedDate.flatMapLatest { dateString ->
            flow {
                val usageMillis = repository.getTotalUsageTimeMillisForDate(dateString)
                if (usageMillis != null && usageMillis > 0) {
                    emit(DateUtil.formatDuration(usageMillis))
                } else if (usageMillis == 0L){
                    emit("0m")
                }
                else {
                    emit("N/A")
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = "Loading..."
        )

    val scrollDistanceFormatted: StateFlow<Pair<String, String>> =
        totalScrollForSelectedDate.map { scrollUnits ->
            ConversionUtil.formatScrollDistance(scrollUnits, application.applicationContext)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = "0.00 km" to "0.00 miles"
        )

    init {
        // Initial fetch for today's usage time is handled by the StateFlow reacting to _selectedDate
        // The explicit refreshUsageTimeForCurrentDate will be called from MainActivity onResume if permission granted
    }

    fun updateSelectedDate(dateMillis: Long) {
        val newDateString = DateUtil.formatDate(dateMillis)
        if (_selectedDate.value != newDateString) {
            _selectedDate.value = newDateString
        }
    }

    fun refreshUsageTimeForCurrentDate() { // This will re-trigger the totalUsageTimeFormatted flow
        viewModelScope.launch {
            // Forcing a re-emission of selectedDate can trigger dependent flows if needed,
            // but flatMapLatest should handle it. This call is more to log or force an update if state isn't changing.
            val currentSelectedDate = _selectedDate.value
            val usageMillis = repository.getTotalUsageTimeMillisForDate(currentSelectedDate)
            // Update the StateFlow directly if not relying solely on selectedDate emission for this specific refresh
            if (usageMillis != null) {
                // _totalUsageTimeTodayFormatted.value = DateUtil.formatDuration(usageMillis) // This would be a direct update, but the flow should react
                Log.d("MainViewModel", "Explicit refresh: Usage time for $currentSelectedDate is ${DateUtil.formatDuration(usageMillis)}")
            } else {
                // _totalUsageTimeTodayFormatted.value = "N/A"
                Log.d("MainViewModel", "Explicit refresh: Usage time for $currentSelectedDate is N/A")
            }
        }
    }

    /**
     * Triggers the backfill of historical app usage data.
     * Typically called once after usage stats permission is granted.
     */
    fun performHistoricalUsageDataBackfill(days: Int = 7) { // Default to 7 days
        viewModelScope.launch {
            Log.i("MainViewModel", "Starting historical usage data backfill for $days days.")
            val success = repository.backfillHistoricalAppUsageData(days)
            if (success) {
                Log.i("MainViewModel", "Historical data backfill completed successfully.")
                // Optionally, trigger a refresh of the current day's view if it might have changed
                // or if the backfill included today.
                // For simplicity, we assume the selectedDate flow will pick up changes if today was part of backfill.
                // To force re-collection of flows that depend on selectedDate:
                // val currentDate = _selectedDate.value
                // _selectedDate.value = "" // Temporarily change to force re-emission
                // _selectedDate.value = currentDate
            } else {
                Log.w("MainViewModel", "Historical data backfill failed or had issues.")
            }
        }
    }
}
