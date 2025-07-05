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
import com.example.scrolltrack.ui.mappers.AppUiModelMapper
import com.example.scrolltrack.ui.theme.AppTheme
import com.example.scrolltrack.util.AppConstants
import com.example.scrolltrack.db.DailyDeviceSummary
import kotlinx.coroutines.delay

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodaySummaryViewModel @Inject constructor(
    private val repository: ScrollDataRepository,
    private val settingsRepository: SettingsRepository,
    private val appMetadataRepository: AppMetadataRepository,
    private val mapper: AppUiModelMapper,
    private val conversionUtil: ConversionUtil,
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
    private val _selectedThemePalette = MutableStateFlow(AppTheme.CalmLavender)
    val selectedThemePalette: StateFlow<AppTheme> = _selectedThemePalette.asStateFlow()

    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _selectedDate = MutableStateFlow(DateUtil.getCurrentLocalDateString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    init {
        Log.d("TodaySummaryViewModel", "ViewModel created. Initializing flows.")
        checkAllPermissions()
        
        viewModelScope.launch {
            settingsRepository.selectedTheme.collect { theme ->
                _selectedThemePalette.value = theme
            }
        }
        viewModelScope.launch {
            settingsRepository.isDarkMode.collect { isDark ->
                _isDarkMode.value = isDark
            }
        }

        // Launch the date ticker
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val millisToMidnight = DateUtil.getMillisUntilNextMidnight()
                Log.d("TodaySummaryViewModel", "Waiting ${millisToMidnight / 1000}s until next date check.")
                delay(millisToMidnight + 1000) // Add a slight buffer
                val newDate = DateUtil.getCurrentLocalDateString()
                if (newDate != _selectedDate.value) {
                    Log.i("TodaySummaryViewModel", "Midnight passed. Updating selected date to $newDate.")
                    _selectedDate.value = newDate
                }
            }
        }
    }

    /**
     * This should be called from the Activity's onResume() to ensure permissions are checked
     * whenever the user returns to the app.
     */
    fun onAppResumed() {
        Log.d("TodaySummaryViewModel", "onAppResumed called, re-checking permissions.")
        checkAllPermissions()
        // After checking permissions, if usage stats are now granted,
        // we should trigger a fetch of the latest data.
        viewModelScope.launch {
            if (isUsagePermissionGranted.value) {
                repository.fetchAndStoreNewUsageEvents()
            }
        }
    }

    fun updateThemePalette(theme: AppTheme) {
        viewModelScope.launch {
            settingsRepository.setSelectedTheme(theme)
        }
    }

    fun setIsDarkMode(isDark: Boolean) {
        viewModelScope.launch {
            settingsRepository.setIsDarkMode(isDark)
        }
    }

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

    val greeting: StateFlow<String> = flow { emit(GreetingUtil.getGreeting()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Hello! 👋")

    private suspend fun processUsageRecords(records: List<DailyAppUsageRecord>): List<AppUsageUiItem> {
        return withContext(Dispatchers.IO) {
            records.map { mapper.mapToAppUsageUiItem(it) }
                .sortedByDescending { it.usageTimeMillis }
        }
    }

    private fun formatTotalUsageTime(totalMillis: Long): String {
        return if (totalMillis <= 0L && isUsageStatsPermissionGrantedByAppOps()) "0m"
        else if (totalMillis <= 0L) "N/A"
        else DateUtil.formatDuration(totalMillis)
    }

    // --- NEW: Live Data Source for Today's Summary ---
    @OptIn(ExperimentalCoroutinesApi::class)
    private val liveSummary: Flow<DailyDeviceSummary> = selectedDate.flatMapLatest { date ->
        repository.getLiveSummaryForDate(date)
    }

    val totalPhoneUsageTodayFormatted: StateFlow<String> =
        liveSummary.map { formatTotalUsageTime(it.totalUsageTimeMillis) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "Loading...")

    val totalUnlocksToday: StateFlow<Int> =
        liveSummary.map { it.totalUnlockCount }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val totalNotificationsToday: StateFlow<Int> =
        liveSummary.map { it.totalNotificationCount }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val totalPhoneUsageTodayMillis: StateFlow<Long> =
        liveSummary.map { it.totalUsageTimeMillis }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0L)

    // --- Flows for data that is independent of the live summary ---
    @OptIn(ExperimentalCoroutinesApi::class)
    val todaysAppUsageUiList: StateFlow<List<AppUsageUiItem>> =
        selectedDate.flatMapLatest { date ->
            repository.getDailyUsageRecordsForDate(date)
        }
            .map { records -> processUsageRecords(records) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val aggregatedScrollDataToday: StateFlow<List<AppScrollUiItem>> =
        selectedDate.flatMapLatest { date ->
            repository.getAggregatedScrollDataForDate(date)
        }
            .map { appScrollDataList -> appScrollDataList.map { mapper.mapToAppScrollUiItem(it) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val totalScrollToday: StateFlow<Long> =
        selectedDate.flatMapLatest { date ->
            repository.getTotalScrollForDate(date)
        }
        .map { it ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0L)

    val scrollDistanceTodayFormatted: StateFlow<Pair<String, String>> =
        totalScrollToday.map { scrollUnits -> conversionUtil.formatScrollDistance(scrollUnits, context) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "0 m" to "0.0 miles")

    val topWeeklyApp: StateFlow<AppUsageUiItem?> = flow {
        val today = DateUtil.getCurrentLocalDateString()
        val sevenDaysAgo = DateUtil.getPastDateString(6)

        val weeklyUsageRecords = repository.getUsageRecordsForDateRange(sevenDaysAgo, today).first()

        if (weeklyUsageRecords.isEmpty()) {
            emit(null)
            return@flow
        }

        val topAppRecord = weeklyUsageRecords
            .groupBy { it.packageName }
            .mapValues { (_, records) -> records.sumOf { it.usageTimeMillis } }
            .maxByOrNull { it.value }
            ?.let { (packageName, totalUsage) ->
                // To get the app name and icon, we need to map it.
                // We'll create a temporary full record for the mapper.
                // The other values aren't as important as they aren't displayed for the weekly top app.
                val representativeRecord = weeklyUsageRecords.first { it.packageName == packageName }
                DailyAppUsageRecord(
                    id = representativeRecord.id,
                    packageName = packageName,
                    dateString = today, // Not critical which date we use here
                    usageTimeMillis = totalUsage,
                    activeTimeMillis = weeklyUsageRecords.filter { it.packageName == packageName }.sumOf { it.activeTimeMillis },
                    lastUpdatedTimestamp = System.currentTimeMillis()
                )
            }

        if (topAppRecord != null) {
            emit(mapper.mapToAppUsageUiItem(topAppRecord))
        } else {
            emit(null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)


    private fun isUsageStatsPermissionGrantedByAppOps(): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun performHistoricalUsageDataBackfill(days: Int, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            Log.i("TodaySummaryViewModel", "Starting historical usage data backfill for $days days.")
            val success = repository.backfillHistoricalAppUsageData(days)
            if (success) {
                Log.i("TodaySummaryViewModel", "Historical backfill successful.")
                withContext(Dispatchers.Main) {
                    // No explicit refresh needed here as liveTodaySummary will update
                }
            } else {
                Log.e("TodaySummaryViewModel", "Historical backfill failed.")
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