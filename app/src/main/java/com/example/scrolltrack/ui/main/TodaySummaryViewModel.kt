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
import java.util.Calendar // For date calculations
import java.util.Date // Added import
import kotlinx.coroutines.async
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import android.util.LruCache // Import LruCache
import com.example.scrolltrack.ui.main.ComparisonColorType
import com.example.scrolltrack.ui.main.ComparisonIconType
import com.example.scrolltrack.ui.model.AppDailyDetailData
import com.example.scrolltrack.ui.model.AppScrollUiItem
import com.example.scrolltrack.ui.model.AppUsageUiItem
import com.example.scrolltrack.data.SettingsRepository
import android.text.format.DateUtils as AndroidDateUtils
import android.os.Build
import com.example.scrolltrack.ScrollTrackService
import com.example.scrolltrack.util.PermissionUtils
import java.util.TimeZone
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import com.example.scrolltrack.data.AppMetadataRepository

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodaySummaryViewModel @Inject constructor(
    private val repository: ScrollDataRepository,
    private val settingsRepository: SettingsRepository,
    private val appMetadataRepository: AppMetadataRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    // --- Permission States ---
    private val _isAccessibilityServiceEnabled = MutableStateFlow(false)
    val isAccessibilityServiceEnabled: StateFlow<Boolean> = _isAccessibilityServiceEnabled.asStateFlow()

    private val _isUsagePermissionGranted = MutableStateFlow(false)
    val isUsagePermissionGranted: StateFlow<Boolean> = _isUsagePermissionGranted.asStateFlow()

    private val _isNotificationListenerEnabled = MutableStateFlow(false)
    val isNotificationListenerEnabled: StateFlow<Boolean> = _isNotificationListenerEnabled.asStateFlow()

    // --- Theme Management ---
    private val _selectedThemeVariant = MutableStateFlow(settingsRepository.getSelectedTheme())
    val selectedThemeVariant: StateFlow<String> = _selectedThemeVariant.asStateFlow()

    // Ensure _todayDateString is initialized before _selectedDateForHistory
    private val _todayDateString = DateUtil.getCurrentLocalDateString()

    init {
        // Load saved theme or use default
        Log.d("TodaySummaryViewModel", "Initial theme loaded: ${settingsRepository.getSelectedTheme()}")
        // Check initial permission state
        checkAllPermissions()
        // Initial data refresh
        refreshDataForToday()
    }

    fun updateThemeVariant(newVariant: String) {
        if (newVariant != _selectedThemeVariant.value) {
            _selectedThemeVariant.value = newVariant
            settingsRepository.setSelectedTheme(newVariant)
            Log.d("TodaySummaryViewModel", "Theme updated and saved: $newVariant")
        }
    }

    /**
     * Checks the status of all required permissions and updates the public StateFlows.
     * This should be called from the Activity's onResume.
     */
    fun checkAllPermissions() {
        val accessibilityStatus = PermissionUtils.isAccessibilityServiceEnabled(context, ScrollTrackService::class.java)
        val usageStatus = isUsageStatsPermissionGrantedByAppOps()
        val notificationListenerStatus = PermissionUtils.isNotificationListenerEnabled(context, com.example.scrolltrack.services.NotificationListener::class.java)

        if (_isAccessibilityServiceEnabled.value != accessibilityStatus) {
            _isAccessibilityServiceEnabled.value = accessibilityStatus
            Log.i("TodaySummaryViewModel", "Accessibility Service status updated: $accessibilityStatus")
        }

        if (_isUsagePermissionGranted.value != usageStatus) {
            _isUsagePermissionGranted.value = usageStatus
            Log.i("TodaySummaryViewModel", "Usage Stats permission status updated: $usageStatus")
        }

        if (_isNotificationListenerEnabled.value != notificationListenerStatus) {
            _isNotificationListenerEnabled.value = notificationListenerStatus
            Log.i("TodaySummaryViewModel", "Notification Listener status updated: $notificationListenerStatus")
        }
    }

    // --- Greeting ---
    val greeting: StateFlow<String> = flow { emit(GreetingUtil.getGreeting()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Hello! 👋")

    // --- Helper function to filter and map DailyAppUsageRecords to AppUsageUiItems ---
    private suspend fun processSingleUsageRecordToUiItem(record: DailyAppUsageRecord): AppUsageUiItem? {
        return withContext(Dispatchers.IO) {
            val metadata = appMetadataRepository.getAppMetadata(record.packageName)
            // The icon is now fetched separately and is a file-based cache.
            val icon = appMetadataRepository.getIconDrawable(record.packageName)

            if (metadata != null) {
                AppUsageUiItem(
                    id = record.packageName,
                    appName = metadata.appName,
                    icon = icon,
                    usageTimeMillis = record.usageTimeMillis,
                    packageName = record.packageName
                )
            } else {
                // This case should be very rare now, but good to have a fallback.
                Log.w("TodaySummaryViewModel", "No metadata found for ${record.packageName}, creating fallback UI item.")
                val fallbackAppName = record.packageName.substringAfterLast('.', record.packageName)
                AppUsageUiItem(record.packageName, fallbackAppName, null, record.usageTimeMillis, record.packageName)
            }
        }
    }

    private suspend fun processUsageRecords(records: List<DailyAppUsageRecord>): List<AppUsageUiItem> {
        return withContext(Dispatchers.IO) {
            records.mapNotNull { record ->
                processSingleUsageRecordToUiItem(record)
            }.sortedByDescending { it.usageTimeMillis }
        }
    }

    private fun formatTotalUsageTime(totalMillis: Long?): String {
        return when {
            totalMillis == null -> "N/A"
            totalMillis <= 0L && isUsageStatsPermissionGrantedByAppOps() -> "0m"
            totalMillis <= 0L -> "N/A"
            else -> DateUtil.formatDuration(totalMillis)
        }
    }


    // --- Data for TODAY'S SUMMARY (Main Screen) ---
    val todaysAppUsageUiList: StateFlow<List<AppUsageUiItem>> =
        repository.getDailyUsageRecordsForDate(_todayDateString)
            .map { records -> processUsageRecords(records) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val totalPhoneUsageTodayMillis: StateFlow<Long> =
        repository.getTotalUsageTimeMillisForDate(_todayDateString)
            .map { it ?: 0L }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0L)

    val totalPhoneUsageTodayFormatted: StateFlow<String> =
        totalPhoneUsageTodayMillis.map {
            formatTotalUsageTime(it)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "Loading...")

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
            ConversionUtil.formatScrollDistance(it, context)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "0 m" to "0.00 miles")

    val totalUnlocksToday: StateFlow<Int> =
        repository.getTotalUnlockCountForDate(_todayDateString)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val totalNotificationsToday: StateFlow<Int> =
        repository.getTotalNotificationCountForDate(_todayDateString)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    private val _totalScrollTodayFormatted = MutableStateFlow("0m")
    val totalScrollTodayFormatted: StateFlow<String> = _totalScrollTodayFormatted


    // --- Top Used App in Last 7 Days ---
    val topUsedAppLast7Days: StateFlow<AppUsageUiItem?> = flow {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val endDateString = DateUtil.formatDateToYyyyMmDdString(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, -6)
        val startDateString = DateUtil.formatDateToYyyyMmDdString(calendar.time)

        repository.getUsageRecordsForDateRange(startDateString, endDateString)
            .map { dailyRecords ->
                if (dailyRecords.isEmpty()) {
                    emit(null)
                    return@map
                }
                val aggregatedUsage = dailyRecords
                    .groupBy { it.packageName }
                    .mapValues { entry -> entry.value.sumOf { it.usageTimeMillis } }
                    .maxByOrNull { it.value }

                if (aggregatedUsage != null) {
                    val topAppPackageName = aggregatedUsage.key
                    val totalTime = aggregatedUsage.value
                    val representativeRecord = DailyAppUsageRecord(
                        packageName = topAppPackageName,
                        dateString = endDateString,
                        usageTimeMillis = totalTime
                    )
                    emit(processSingleUsageRecordToUiItem(representativeRecord))
                } else {
                    emit(null)
                }
            }.catch { e ->
                Log.e("TodaySummaryViewModel", "Error fetching or processing top used app for last 7 days", e)
                emit(null)
            }.collect()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)


    // Helper to map ScrollData to ScrollUiItem
    private suspend fun mapToAppScrollUiItems(appScrollDataList: List<AppScrollData>): List<AppScrollUiItem> {
        return withContext(Dispatchers.IO) {
            appScrollDataList.mapNotNull { appData ->
                val metadata = appMetadataRepository.getAppMetadata(appData.packageName)
                val icon = appMetadataRepository.getIconDrawable(appData.packageName)

                if (metadata != null) {
                    AppScrollUiItem(
                        id = appData.packageName,
                        appName = metadata.appName,
                        icon = icon,
                        totalScroll = appData.totalScroll,
                        packageName = appData.packageName
                    )
                } else {
                    Log.w("TodaySummaryViewModel", "No metadata found for scroll item ${appData.packageName}, creating fallback UI item.")
                    val fallbackAppName = appData.packageName.substringAfterLast('.', appData.packageName)
                    AppScrollUiItem(appData.packageName, fallbackAppName, null, appData.totalScroll, appData.packageName)
                }
            }
        }
    }

    // Corrected helper to check permission
    @Suppress("DEPRECATION")
    private fun isUsageStatsPermissionGrantedByAppOps(): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        return if (appOpsManager != null) {
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } else {
            Log.w("TodaySummaryViewModel", "AppOpsManager was null, cannot check usage stats permission.")
            false
        }
    }

    fun refreshDataForToday() {
        viewModelScope.launch {
            Log.d("TodaySummaryViewModel", "Explicit refresh for today's data triggered. Attempting to update today's stats in DB.")
            val success = repository.updateTodayAppUsageStats()
            if (success) {
                Log.d("TodaySummaryViewModel", "Successfully updated today's app usage stats in the database.")
            } else {
                Log.w("TodaySummaryViewModel", "Failed to update today's app usage stats in the database.")
            }
        }
    }

    fun performHistoricalUsageDataBackfill(days: Int = 10, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            Log.i("TodaySummaryViewModel", "Starting historical usage data backfill for $days days.")
            val success = repository.backfillHistoricalAppUsageData(days)
            if (success) {
                Log.i("TodaySummaryViewModel", "Historical data backfill completed successfully.")
                // Refresh today's data and potentially the historical screen if it's open
                withContext(Dispatchers.Main) {
                    refreshDataForToday()
                }
            } else {
                Log.w("TodaySummaryViewModel", "Historical data backfill failed or had issues.")
            }
            onComplete(success)
        }
    }
}

/**
 * Represents the different theme variants available in the app.
 */
enum class ThemeVariant {
    SYSTEM,
    LIGHT,
    DARK,
    OLED
} 