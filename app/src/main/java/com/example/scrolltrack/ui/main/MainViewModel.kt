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

// Data class for cached app metadata
private data class CachedAppMetadata(val appName: String, val icon: Drawable?)

enum class ChartPeriodType {
    DAILY,
    WEEKLY,
    MONTHLY
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: ScrollDataRepository,
    private val settingsRepository: SettingsRepository,
    private val application: Application
) : ViewModel() {

    // --- App Metadata Cache ---
    private val metadataCache = LruCache<String, CachedAppMetadata>(100) // Cache up to 100 items

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

    private val _selectedDateForHistory = MutableStateFlow(_todayDateString) // Default to today
    val selectedDateForHistory: StateFlow<String> = _selectedDateForHistory.asStateFlow()

    val isTodaySelectedForHistory: StateFlow<Boolean> = _selectedDateForHistory.map { it == _todayDateString }.stateIn(viewModelScope, SharingStarted.Lazily, true)

    init {
        // Load saved theme or use default
        Log.d("MainViewModel", "Initial theme loaded: ${settingsRepository.getSelectedTheme()}")
        // Check initial permission state
        checkAllPermissions()
        // Initial data refresh
        refreshDataForToday()
    }

    fun updateThemeVariant(newVariant: String) {
        if (newVariant != _selectedThemeVariant.value) {
            _selectedThemeVariant.value = newVariant
            settingsRepository.setSelectedTheme(newVariant)
            Log.d("MainViewModel", "Theme updated and saved: $newVariant")
        }
    }

    /**
     * Checks the status of all required permissions and updates the public StateFlows.
     * This should be called from the Activity's onResume.
     */
    fun checkAllPermissions() {
        val accessibilityStatus = PermissionUtils.isAccessibilityServiceEnabled(application, ScrollTrackService::class.java)
        val usageStatus = isUsageStatsPermissionGrantedByAppOps()
        val notificationListenerStatus = PermissionUtils.isNotificationListenerEnabled(application, com.example.scrolltrack.services.NotificationListener::class.java)

        if (_isAccessibilityServiceEnabled.value != accessibilityStatus) {
            _isAccessibilityServiceEnabled.value = accessibilityStatus
            Log.i("MainViewModel", "Accessibility Service status updated: $accessibilityStatus")
        }

        if (_isUsagePermissionGranted.value != usageStatus) {
            _isUsagePermissionGranted.value = usageStatus
            Log.i("MainViewModel", "Usage Stats permission status updated: $usageStatus")
        }

        if (_isNotificationListenerEnabled.value != notificationListenerStatus) {
            _isNotificationListenerEnabled.value = notificationListenerStatus
            Log.i("MainViewModel", "Notification Listener status updated: $notificationListenerStatus")
        }
    }

    // --- App Detail Screen Specific States ---
    private val _appDetailPackageName = MutableStateFlow<String?>(null)

    private val _appDetailAppName = MutableStateFlow<String?>(null)
    val appDetailAppName: StateFlow<String?> = _appDetailAppName.asStateFlow()

    private val _appDetailAppIcon = MutableStateFlow<Drawable?>(null)
    val appDetailAppIcon: StateFlow<Drawable?> = _appDetailAppIcon.asStateFlow()

    private val _appDetailChartData = MutableStateFlow<List<AppDailyDetailData>>(emptyList())
    val appDetailChartData: StateFlow<List<AppDailyDetailData>> = _appDetailChartData.asStateFlow()

    private val _currentChartPeriodType = MutableStateFlow(ChartPeriodType.WEEKLY)
    val currentChartPeriodType: StateFlow<ChartPeriodType> = _currentChartPeriodType.asStateFlow()

    // Represents the anchor date for the current chart view.
    private val _currentChartReferenceDate = MutableStateFlow(DateUtil.getCurrentLocalDateString())
    val currentChartReferenceDate: StateFlow<String> = _currentChartReferenceDate.asStateFlow()

    // --- New StateFlows for App Detail Summary ---
    private val _appDetailFocusedUsageDisplay = MutableStateFlow("0m")
    val appDetailFocusedUsageDisplay: StateFlow<String> = _appDetailFocusedUsageDisplay.asStateFlow()

    private val _appDetailFocusedActiveUsageDisplay = MutableStateFlow("0m")
    val appDetailFocusedActiveUsageDisplay: StateFlow<String> = _appDetailFocusedActiveUsageDisplay.asStateFlow()

    private val _appDetailPeriodDescriptionText = MutableStateFlow<String?>(null)
    val appDetailPeriodDescriptionText: StateFlow<String?> = _appDetailPeriodDescriptionText.asStateFlow()

    private val _appDetailFocusedPeriodDisplay = MutableStateFlow("")
    val appDetailFocusedPeriodDisplay: StateFlow<String> = _appDetailFocusedPeriodDisplay.asStateFlow()

    private val _appDetailComparisonText = MutableStateFlow<String?>(null)
    val appDetailComparisonText: StateFlow<String?> = _appDetailComparisonText.asStateFlow()

    private val _appDetailComparisonIconType = MutableStateFlow(ComparisonIconType.NONE)
    val appDetailComparisonIconType: StateFlow<ComparisonIconType> = _appDetailComparisonIconType.asStateFlow()

    private val _appDetailComparisonColorType = MutableStateFlow(ComparisonColorType.GREY)
    val appDetailComparisonColorType: StateFlow<ComparisonColorType> = _appDetailComparisonColorType.asStateFlow()

    private val _appDetailWeekNumberDisplay = MutableStateFlow<String?>(null)
    val appDetailWeekNumberDisplay: StateFlow<String?> = _appDetailWeekNumberDisplay.asStateFlow()

    private val _appDetailFocusedScrollDisplay = MutableStateFlow("0 m")
    val appDetailFocusedScrollDisplay: StateFlow<String> = _appDetailFocusedScrollDisplay.asStateFlow()

    private val _appDetailFocusedOpenCount = MutableStateFlow(0)
    val appDetailFocusedOpenCount: StateFlow<Int> = _appDetailFocusedOpenCount.asStateFlow()

    private val _appDetailFocusedDate = MutableStateFlow(DateUtil.getCurrentLocalDateString())
    val appDetailFocusedDate: StateFlow<String> = _appDetailFocusedDate.asStateFlow()

    val canNavigateChartForward: StateFlow<Boolean> = _currentChartReferenceDate.map { refDateStr ->
        val today = Calendar.getInstance()
        val refDateCal = Calendar.getInstance().apply {
            time = DateUtil.parseLocalDateString(refDateStr) ?: Date()
        }
        !AndroidDateUtils.isToday(refDateCal.timeInMillis) && refDateCal.before(today)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // --- Greeting ---
    val greeting: StateFlow<String> = flow { emit(GreetingUtil.getGreeting()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Hello! 👋")

    // --- Helper function to filter and map DailyAppUsageRecords to AppUsageUiItems ---
    private suspend fun processSingleUsageRecordToUiItem(record: DailyAppUsageRecord): AppUsageUiItem? {
        return withContext(Dispatchers.IO) {
            val cachedData = metadataCache.get(record.packageName)
            if (cachedData != null) {
                AppUsageUiItem(
                    id = record.packageName,
                    appName = cachedData.appName,
                    icon = cachedData.icon,
                    usageTimeMillis = record.usageTimeMillis,
                    packageName = record.packageName
                )
            } else {
                try {
                    val pm = application.packageManager
                    val appInfo = pm.getApplicationInfo(record.packageName, 0)
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    val icon = pm.getApplicationIcon(record.packageName)
                    metadataCache.put(record.packageName, CachedAppMetadata(appName, icon))
                    AppUsageUiItem(
                        id = record.packageName,
                        appName = appName,
                        icon = icon,
                        usageTimeMillis = record.usageTimeMillis,
                        packageName = record.packageName
                    )
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.w("MainViewModel", "Package info not found for usage item ${record.packageName}, creating fallback UI item.")
                    val fallbackAppName = record.packageName.substringAfterLast('.', record.packageName)
                    metadataCache.put(record.packageName, CachedAppMetadata(fallbackAppName, null))
                    AppUsageUiItem(record.packageName, fallbackAppName, null, record.usageTimeMillis, record.packageName)
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Error processing usage app data for ${record.packageName}", e)
                    null
                }
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
            ConversionUtil.formatScrollDistance(it, application.applicationContext)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "0 m" to "0.00 miles")

    val totalUnlocksToday: StateFlow<Int> =
        repository.getTotalUnlockCountForDate(_todayDateString)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val totalNotificationsToday: StateFlow<Int> =
        repository.getTotalNotificationCountForDate(_todayDateString)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    private val _totalScrollTodayFormatted = MutableStateFlow("0m")
    val totalScrollTodayFormatted: StateFlow<String> = _totalScrollTodayFormatted


    // --- Date Picker Availability States ---
    @OptIn(ExperimentalCoroutinesApi::class)
    val selectableDatesForScrollDetail: StateFlow<Set<Long>> =
        repository.getAllDistinctScrollDateStrings()
            .map { dateStrings ->
                dateStrings.mapNotNull { DateUtil.parseLocalDateString(it)?.time }.toSet()
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectableDatesForHistoricalUsage: StateFlow<Set<Long>> =
        repository.getAllDistinctUsageDateStrings()
            .map { dateStrings ->
                dateStrings.mapNotNull { DateUtil.parseLocalDateString(it)?.time }.toSet()
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())


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

    // --- Data for SCROLL DETAIL SCREEN ---
    private val _selectedDateForScrollDetail = MutableStateFlow(DateUtil.getCurrentLocalDateString())
    val selectedDateForScrollDetail: StateFlow<String> = _selectedDateForScrollDetail.asStateFlow()

    val aggregatedScrollDataForSelectedDate: StateFlow<List<AppScrollUiItem>> =
        _selectedDateForScrollDetail.flatMapLatest { dateString ->
            Log.d("MainViewModel", "ScrollDetail: Loading aggregated scroll data for date: $dateString")
            repository.getAggregatedScrollDataForDate(dateString)
                .map { appScrollDataList ->
                    Log.d("MainViewModel", "ScrollDetail: Received ${appScrollDataList.size} raw scroll items for $dateString")
                    val uiItems = mapToAppScrollUiItems(appScrollDataList)
                    Log.d("MainViewModel", "ScrollDetail: Mapped to ${uiItems.size} UI scroll items for $dateString")
                    uiItems
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun updateSelectedDateForScrollDetail(dateMillis: Long) {
        _selectedDateForScrollDetail.value = DateUtil.formatDateToYyyyMmDdString(Date(dateMillis))
    }
    fun resetSelectedDateForScrollDetailToToday() {
        _selectedDateForScrollDetail.value = DateUtil.getCurrentLocalDateString()
    }

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
                Log.e("MainViewModel", "Error fetching or processing top used app for last 7 days", e)
                emit(null)
            }.collect()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)


    // Helper to map ScrollData to ScrollUiItem
    private suspend fun mapToAppScrollUiItems(appScrollDataList: List<AppScrollData>): List<AppScrollUiItem> {
        return withContext(Dispatchers.IO) {
            appScrollDataList.mapNotNull { appData ->
                val cachedData = metadataCache.get(appData.packageName)
                if (cachedData != null) {
                    AppScrollUiItem(
                        id = appData.packageName,
                        appName = cachedData.appName,
                        icon = cachedData.icon,
                        totalScroll = appData.totalScroll,
                        packageName = appData.packageName
                    )
                } else {
                    try {
                        val pm = application.packageManager
                        val appInfo = pm.getApplicationInfo(appData.packageName, 0)
                        val appName = pm.getApplicationLabel(appInfo).toString()
                        val icon = pm.getApplicationIcon(appData.packageName)
                        metadataCache.put(appData.packageName, CachedAppMetadata(appName, icon))
                        AppScrollUiItem(
                            id = appData.packageName,
                            appName = appName,
                            icon = icon,
                            totalScroll = appData.totalScroll,
                            packageName = appData.packageName
                        )
                    } catch (e: PackageManager.NameNotFoundException) {
                        Log.w("MainViewModel", "Package info not found for scroll item ${appData.packageName}, creating fallback UI item.")
                        val fallbackAppName = appData.packageName.substringAfterLast('.', appData.packageName)
                        metadataCache.put(appData.packageName, CachedAppMetadata(fallbackAppName, null))
                        AppScrollUiItem(appData.packageName, fallbackAppName, null, appData.totalScroll, appData.packageName)
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Error processing scroll app data for ${appData.packageName}", e)
                        null
                    }
                }
            }
        }
    }

    // Corrected helper to check permission
    @Suppress("DEPRECATION")
    private fun isUsageStatsPermissionGrantedByAppOps(): Boolean {
        val appOpsManager = application.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        return if (appOpsManager != null) {
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), application.packageName)
            } else {
                appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), application.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } else {
            Log.w("MainViewModel", "AppOpsManager was null, cannot check usage stats permission.")
            false
        }
    }

    fun updateSelectedDateForHistory(dateMillis: Long) {
        val newDateString = DateUtil.formatDateToYyyyMmDdString(Date(dateMillis))
        if (_selectedDateForHistory.value != newDateString) {
            _selectedDateForHistory.value = newDateString
        }
    }

    fun resetSelectedDateToToday() {
        Log.d("MainViewModel", "Resetting selectedDateForHistory to today: $_todayDateString")
        _selectedDateForHistory.value = _todayDateString
    }

    fun refreshDataForToday() {
        viewModelScope.launch {
            Log.d("MainViewModel", "Explicit refresh for today's data triggered. Attempting to update today's stats in DB.")
            val success = repository.updateTodayAppUsageStats()
            if (success) {
                Log.d("MainViewModel", "Successfully updated today's app usage stats in the database.")
            } else {
                Log.w("MainViewModel", "Failed to update today's app usage stats in the database.")
            }
        }
    }

    fun performHistoricalUsageDataBackfill(days: Int = 10, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            Log.i("MainViewModel", "Starting historical usage data backfill for $days days.")
            val success = repository.backfillHistoricalAppUsageData(days)
            if (success) {
                Log.i("MainViewModel", "Historical data backfill completed successfully.")
                // Refresh today's data and potentially the historical screen if it's open
                withContext(Dispatchers.Main) {
                    val currentHistoryDate = _selectedDateForHistory.value
                    if (currentHistoryDate != _todayDateString) {
                        // Force a reload of historical data by changing the state value
                        _selectedDateForHistory.value = ""
                        _selectedDateForHistory.value = currentHistoryDate
                    }
                    refreshDataForToday()
                }
            } else {
                Log.w("MainViewModel", "Historical data backfill failed or had issues.")
            }
            onComplete(success)
        }
    }

    // Methods for App Detail Screen
    fun loadAppDetailsInfo(packageName: String) {
        if (packageName != _appDetailPackageName.value) {
            _currentChartPeriodType.value = ChartPeriodType.DAILY
            _currentChartReferenceDate.value = DateUtil.getCurrentLocalDateString()
            _appDetailPackageName.value = packageName

            viewModelScope.launch(Dispatchers.IO) {
                var appIconDrawable: Drawable? = null
                val cachedAppInfo = metadataCache.get(packageName)

                if (cachedAppInfo != null) {
                    _appDetailAppName.value = cachedAppInfo.appName
                    appIconDrawable = cachedAppInfo.icon
                    _appDetailAppIcon.value = appIconDrawable
                } else {
                    try {
                        val pm = application.packageManager
                        val appInfo = pm.getApplicationInfo(packageName, 0)
                        val appNameString = pm.getApplicationLabel(appInfo).toString()
                        _appDetailAppName.value = appNameString
                        appIconDrawable = pm.getApplicationIcon(packageName)
                        _appDetailAppIcon.value = appIconDrawable
                        metadataCache.put(packageName, CachedAppMetadata(appNameString, appIconDrawable))
                    } catch (e: PackageManager.NameNotFoundException) {
                        Log.w("MainViewModel", "App info not found for $packageName in AppDetailScreen")
                        val fallbackAppName = packageName.substringAfterLast('.', packageName)
                        _appDetailAppName.value = fallbackAppName
                        _appDetailAppIcon.value = null
                        metadataCache.put(packageName, CachedAppMetadata(fallbackAppName, null))
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Error loading app info for $packageName", e)
                        _appDetailAppName.value = packageName
                        _appDetailAppIcon.value = null
                    }
                }
            }
            loadAppDetailChartData(packageName, _currentChartPeriodType.value, _currentChartReferenceDate.value)
        }
    }

    fun loadAppDetailChartData(packageName: String, period: ChartPeriodType, referenceDate: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _appDetailChartData.value = emptyList()
            _appDetailFocusedUsageDisplay.value = "..."
            _appDetailFocusedPeriodDisplay.value = "..."
            _appDetailFocusedScrollDisplay.value = "..."
            _appDetailFocusedActiveUsageDisplay.value = "..."
            _appDetailComparisonText.value = null
            _appDetailWeekNumberDisplay.value = null
            _appDetailPeriodDescriptionText.value = null
            _appDetailFocusedOpenCount.value = 0

            Log.d("MainViewModel", "Loading chart data for $packageName, Period: $period, RefDate: $referenceDate")

            val dateStringsForCurrentPeriod = calculateDateStringsForPeriod(period, referenceDate)
            if (dateStringsForCurrentPeriod.isEmpty()) {
                Log.w("MainViewModel", "Date strings for current period resulted in empty list.")
                _appDetailChartData.value = emptyList()
                _appDetailFocusedUsageDisplay.value = DateUtil.formatDuration(0L)
                _appDetailFocusedActiveUsageDisplay.value = DateUtil.formatDuration(0L)
                _appDetailFocusedPeriodDisplay.value = ""
                _appDetailFocusedScrollDisplay.value = ConversionUtil.formatScrollDistance(0L, application.applicationContext).first
                return@launch
            }

            val usageDataDeferred = async { repository.getUsageForPackageAndDates(packageName, dateStringsForCurrentPeriod) }
            val scrollDataDeferred = async { repository.getAggregatedScrollForPackageAndDates(packageName, dateStringsForCurrentPeriod) }

            val currentPeriodUsageRecords = usageDataDeferred.await()
            val currentPeriodScrollRecords = scrollDataDeferred.await()

            val totalOpens = currentPeriodUsageRecords.sumOf { it.appOpenCount }
            _appDetailFocusedOpenCount.value = totalOpens

            val currentUsageMap = currentPeriodUsageRecords.associateBy { it.dateString }
            val currentScrollMap = currentPeriodScrollRecords.associateBy { it.date }

            val currentCombinedData = dateStringsForCurrentPeriod.map { dateStr ->
                AppDailyDetailData(
                    date = dateStr,
                    usageTimeMillis = currentUsageMap[dateStr]?.usageTimeMillis ?: 0L,
                    activeTimeMillis = currentUsageMap[dateStr]?.activeTimeMillis ?: 0L,
                    scrollUnits = currentScrollMap[dateStr]?.totalScroll ?: 0L
                )
            }
            _appDetailChartData.value = currentCombinedData
            Log.d("MainViewModel", "Chart data loaded: ${currentCombinedData.size} points for current period.")

            val calendar = Calendar.getInstance()
            DateUtil.parseLocalDateString(referenceDate)?.let { calendar.time = it }
            val sdfDisplay = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
            val sdfMonth = SimpleDateFormat("MMMM yyyy", Locale.getDefault())


            when (period) {
                ChartPeriodType.DAILY -> {
                    val focusedDayData = currentCombinedData.firstOrNull { it.date == referenceDate }
                    _appDetailFocusedUsageDisplay.value = DateUtil.formatDuration(focusedDayData?.usageTimeMillis ?: 0L)
                    _appDetailFocusedActiveUsageDisplay.value = DateUtil.formatDuration(focusedDayData?.activeTimeMillis ?: 0L)
                    _appDetailFocusedScrollDisplay.value = ConversionUtil.formatScrollDistance(focusedDayData?.scrollUnits ?: 0L, application.applicationContext).first
                    _appDetailPeriodDescriptionText.value = "Daily Summary"
                    _appDetailFocusedPeriodDisplay.value = sdfDisplay.format(calendar.time)
                    _appDetailWeekNumberDisplay.value = null
                    _appDetailComparisonText.value = null
                }
                ChartPeriodType.WEEKLY -> {
                    val currentPeriodAverageUsage = calculateAverageUsage(currentCombinedData)
                    _appDetailFocusedUsageDisplay.value = DateUtil.formatDuration(currentPeriodAverageUsage)
                    val currentPeriodAverageActiveUsage = calculateAverageActiveUsage(currentCombinedData)
                    _appDetailFocusedActiveUsageDisplay.value = DateUtil.formatDuration(currentPeriodAverageActiveUsage)
                    val currentPeriodAverageScroll = calculateAverageScroll(currentCombinedData)
                    _appDetailFocusedScrollDisplay.value = ConversionUtil.formatScrollDistance(currentPeriodAverageScroll, application.applicationContext).first
                    _appDetailPeriodDescriptionText.value = "Weekly Average"
                    val (startOfWeekStr, endOfWeekStr) = getPeriodDisplayStrings(period, referenceDate)
                    _appDetailFocusedPeriodDisplay.value = "$startOfWeekStr - $endOfWeekStr"
                    _appDetailWeekNumberDisplay.value = "Week ${getWeekOfYear(calendar)}"

                    val (prevWeekDateStrings, _) = calculatePreviousPeriodDateStrings(period, referenceDate)
                    if (prevWeekDateStrings.isNotEmpty()) {
                        val prevWeekUsageRecords = repository.getUsageForPackageAndDates(packageName, prevWeekDateStrings)
                        val prevWeekAverageUsage = calculateAverageUsageFromRecords(prevWeekUsageRecords)
                        updateComparisonText(currentPeriodAverageUsage, prevWeekAverageUsage, "week")
                    } else {
                        _appDetailComparisonText.value = "No data for comparison"
                    }
                }
                ChartPeriodType.MONTHLY -> {
                    val currentPeriodAverageUsage = calculateAverageUsage(currentCombinedData)
                    _appDetailFocusedUsageDisplay.value = DateUtil.formatDuration(currentPeriodAverageUsage)
                    val currentPeriodAverageActiveUsage = calculateAverageActiveUsage(currentCombinedData)
                    _appDetailFocusedActiveUsageDisplay.value = DateUtil.formatDuration(currentPeriodAverageActiveUsage)
                    val currentPeriodAverageScroll = calculateAverageScroll(currentCombinedData)
                    _appDetailFocusedScrollDisplay.value = ConversionUtil.formatScrollDistance(currentPeriodAverageScroll, application.applicationContext).first

                    _appDetailPeriodDescriptionText.value = "Monthly Average"
                    _appDetailFocusedPeriodDisplay.value = sdfMonth.format(calendar.time)
                    _appDetailWeekNumberDisplay.value = null

                    val (prevMonthDateStrings, _) = calculatePreviousPeriodDateStrings(period, referenceDate)
                    if (prevMonthDateStrings.isNotEmpty()) {
                        val prevMonthUsageRecords = repository.getUsageForPackageAndDates(packageName, prevMonthDateStrings)
                        val prevMonthAverageUsage = calculateAverageUsageFromRecords(prevMonthUsageRecords)
                        updateComparisonText(currentPeriodAverageUsage, prevMonthAverageUsage, "month")
                    } else {
                        _appDetailComparisonText.value = "No data for comparison"
                    }
                }
            }
        }
    }

    private fun getWeekOfYear(calendar: Calendar): Int {
        return calendar.get(Calendar.WEEK_OF_YEAR)
    }

    private fun calculateAverageUsage(data: List<AppDailyDetailData>): Long {
        if (data.isEmpty()) return 0L
        return data.sumOf { it.usageTimeMillis } / data.size
    }

    private fun calculateAverageActiveUsage(data: List<AppDailyDetailData>): Long {
        if (data.isEmpty()) return 0L
        return data.sumOf { it.activeTimeMillis } / data.size
    }

    private fun calculateAverageScroll(data: List<AppDailyDetailData>): Long {
        if (data.isEmpty()) return 0L
        return data.sumOf { it.scrollUnits } / data.size
    }

    private fun calculateAverageUsageFromRecords(data: List<DailyAppUsageRecord>): Long {
        if (data.isEmpty()) return 0L
        return data.sumOf { it.usageTimeMillis } / data.size
    }

    private fun updateComparisonText(currentAvg: Long, previousAvg: Long, periodName: String) {
        val currentAvgFormatted = DateUtil.formatDuration(currentAvg)

        if (previousAvg == 0L) {
            if (currentAvg == 0L) {
                _appDetailComparisonText.value = "Still no usage"
                _appDetailComparisonIconType.value = ComparisonIconType.NEUTRAL
                _appDetailComparisonColorType.value = ComparisonColorType.GREY
            } else {
                _appDetailComparisonText.value = "$currentAvgFormatted from no usage"
                _appDetailComparisonIconType.value = ComparisonIconType.UP
                _appDetailComparisonColorType.value = ComparisonColorType.RED
            }
            return
        }

        if (currentAvg == previousAvg) {
            _appDetailComparisonText.value = "Same as last $periodName"
            _appDetailComparisonIconType.value = ComparisonIconType.NEUTRAL
            _appDetailComparisonColorType.value = ComparisonColorType.GREY
            return
        }

        val difference = currentAvg - previousAvg
        val percentageChange = abs((difference.toDouble() / previousAvg.toDouble()) * 100)
        val percentageString = String.format(Locale.US, "%.0f%%", percentageChange)

        if (currentAvg < previousAvg) {
            _appDetailComparisonText.value = "${percentageString} less vs last ${periodName}"
            _appDetailComparisonIconType.value = ComparisonIconType.DOWN
            _appDetailComparisonColorType.value = ComparisonColorType.GREEN
        } else {
            _appDetailComparisonText.value = "${percentageString} more vs last ${periodName}"
            _appDetailComparisonIconType.value = ComparisonIconType.UP
            _appDetailComparisonColorType.value = ComparisonColorType.RED
        }
    }

    private fun calculatePreviousPeriodDateStrings(period: ChartPeriodType, currentReferenceDateStr: String): Pair<List<String>, String> {
        val calendar = Calendar.getInstance()
        DateUtil.parseLocalDateString(currentReferenceDateStr)?.let { calendar.time = it }
        lateinit var newReferenceDateStr: String

        val previousPeriodDates = when (period) {
            ChartPeriodType.DAILY -> {
                calendar.add(Calendar.WEEK_OF_YEAR, -1)
                newReferenceDateStr = DateUtil.formatDateToYyyyMmDdString(calendar.time)
                calculateDateStringsForPeriod(ChartPeriodType.WEEKLY, newReferenceDateStr)
            }
            ChartPeriodType.WEEKLY -> {
                calendar.add(Calendar.WEEK_OF_YEAR, -1)
                newReferenceDateStr = DateUtil.formatDateToYyyyMmDdString(calendar.time)
                calculateDateStringsForPeriod(ChartPeriodType.WEEKLY, newReferenceDateStr)
            }
            ChartPeriodType.MONTHLY -> {
                calendar.add(Calendar.MONTH, -1)
                newReferenceDateStr = DateUtil.formatDateToYyyyMmDdString(calendar.time)
                calculateDateStringsForPeriod(ChartPeriodType.MONTHLY, newReferenceDateStr)
            }
        }
        return Pair(previousPeriodDates, newReferenceDateStr)
    }

    private fun getPeriodDisplayStrings(period: ChartPeriodType, referenceDateStr: String, includeYear: Boolean = true): Pair<String, String> {
        val calendar = Calendar.getInstance()
        DateUtil.parseLocalDateString(referenceDateStr)?.let { calendar.time = it }

        val monthDayFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        val fullDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

        return when (period) {
            ChartPeriodType.DAILY -> {
                val sdf = if (includeYear) SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()) else SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                Pair(sdf.format(calendar.time), "")
            }
            ChartPeriodType.WEEKLY -> {
                DateUtil.parseLocalDateString(referenceDateStr)?.let { calendar.time = it }
                val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                val daysToSubtract = if (currentDayOfWeek == Calendar.SUNDAY) 6 else currentDayOfWeek - Calendar.MONDAY
                calendar.add(Calendar.DAY_OF_YEAR, -daysToSubtract)
                val startOfWeek = calendar.time

                calendar.add(Calendar.DAY_OF_YEAR, 6)
                val endOfWeek = calendar.time

                val startCal = Calendar.getInstance().apply { time = startOfWeek }
                val endCal = Calendar.getInstance().apply { time = endOfWeek }

                val startFormat = monthDayFormat
                val endFormat = if (startCal.get(Calendar.YEAR) != endCal.get(Calendar.YEAR) || !includeYear) {
                    if (includeYear) fullDateFormat else monthDayFormat
                } else if (startCal.get(Calendar.MONTH) != endCal.get(Calendar.MONTH)) {
                    monthDayFormat
                } else {
                    SimpleDateFormat("d", Locale.getDefault())
                }
                val yearSuffix = if (includeYear && startCal.get(Calendar.YEAR) == endCal.get(Calendar.YEAR)) ", ${startCal.get(Calendar.YEAR)}" else ""

                if (!includeYear){
                    return Pair(startFormat.format(startOfWeek), SimpleDateFormat("MMM d", Locale.getDefault()).format(endOfWeek))
                }

                Pair(startFormat.format(startOfWeek), "${endFormat.format(endOfWeek)}$yearSuffix")
            }
            ChartPeriodType.MONTHLY -> {
                val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                Pair(sdf.format(calendar.time), "")
            }
        }
    }


    private fun calculateDateStringsForPeriod(period: ChartPeriodType, referenceDateStr: String): List<String> {
        val calendar = Calendar.getInstance()
        DateUtil.parseLocalDateString(referenceDateStr)?.let { calendar.time = it }

        return when (period) {
            ChartPeriodType.DAILY, ChartPeriodType.WEEKLY -> {
                DateUtil.parseLocalDateString(referenceDateStr)?.let { calendar.time = it }

                val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                val daysToSubtract = if (currentDayOfWeek == Calendar.SUNDAY) 6 else currentDayOfWeek - Calendar.MONDAY
                calendar.add(Calendar.DAY_OF_YEAR, -daysToSubtract)

                (0..6).map {
                    val dayCal = Calendar.getInstance().apply { time = calendar.time }
                    dayCal.add(Calendar.DAY_OF_YEAR, it)
                    DateUtil.formatDateToYyyyMmDdString(dayCal.time)
                }
            }
            ChartPeriodType.MONTHLY -> {
                DateUtil.parseLocalDateString(referenceDateStr)?.let { calendar.time = it }
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                (0 until daysInMonth).map {
                    val dayCal = Calendar.getInstance().apply { time = calendar.time }
                    dayCal.add(Calendar.DAY_OF_MONTH, it)
                    DateUtil.formatDateToYyyyMmDdString(dayCal.time)
                }
            }
        }
    }

    fun changeChartPeriod(packageName: String, newPeriod: ChartPeriodType) {
        if (newPeriod != _currentChartPeriodType.value) {
            _currentChartPeriodType.value = newPeriod
            // When period changes, we reload the data for the same reference date
            loadAppDetailChartData(packageName, newPeriod, _currentChartReferenceDate.value)
        }
    }

    fun navigateChartDate(packageName: String, direction: Int) {
        val cal = Calendar.getInstance().apply {
            time = DateUtil.parseLocalDateString(_currentChartReferenceDate.value) ?: Date()
        }
        when (_currentChartPeriodType.value) {
            ChartPeriodType.DAILY -> cal.add(Calendar.DAY_OF_YEAR, direction)
            ChartPeriodType.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, direction)
            ChartPeriodType.MONTHLY -> cal.add(Calendar.MONTH, direction)
        }
        val newDateStr = DateUtil.formatDateToYyyyMmDdString(cal.time)
        _currentChartReferenceDate.value = newDateStr
        loadAppDetailChartData(packageName, _currentChartPeriodType.value, newDateStr)
    }

    fun setFocusedDate(packageName: String, newDate: String) {
        if (_currentChartReferenceDate.value != newDate) {
            _currentChartReferenceDate.value = newDate
            if (_currentChartPeriodType.value == ChartPeriodType.DAILY) {
                loadAppDetailChartData(packageName, ChartPeriodType.DAILY, newDate)
            } else {
                Log.w("MainViewModel", "setFocusedDate called with period type ${_currentChartPeriodType.value}. Expected DAILY.")
                loadAppDetailChartData(packageName, _currentChartPeriodType.value, newDate)
            }
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