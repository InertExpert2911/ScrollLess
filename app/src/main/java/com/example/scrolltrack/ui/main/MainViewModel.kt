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
import kotlinx.coroutines.async
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

// Constants for SharedPreferences (can be moved to a companion object or a separate file if preferred)
private const val PREFS_APP_SETTINGS = "ScrollTrackAppSettings"
private const val KEY_SELECTED_THEME = "selected_theme_variant"
private const val THEME_LIGHT = "light"
private const val THEME_DARK = "dark"
private const val THEME_OLED_DARK = "oled_dark"
private const val DEFAULT_THEME = THEME_OLED_DARK

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

// Data class for combined chart data point for AppDetailScreen
data class AppDailyDetailData(
    val date: String, // YYYY-MM-DD
    val usageTimeMillis: Long,
    val scrollUnits: Long // Raw scroll units, conversion to distance can happen in UI or later in VM
)

enum class ChartPeriodType {
    DAILY,
    WEEKLY,
    MONTHLY
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: ScrollDataRepository,
    private val application: Application
) : ViewModel() {

    private val appPrefs = application.getSharedPreferences(PREFS_APP_SETTINGS, Context.MODE_PRIVATE)

    // --- Theme Management ---
    private val _selectedThemeVariant = MutableStateFlow(DEFAULT_THEME)
    val selectedThemeVariant: StateFlow<String> = _selectedThemeVariant.asStateFlow()

    init {
        // Load saved theme or use default
        val savedTheme = appPrefs.getString(KEY_SELECTED_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
        _selectedThemeVariant.value = savedTheme
        Log.d("MainViewModel", "Initial theme loaded: $savedTheme")
    }

    fun updateThemeVariant(newVariant: String) {
        if (newVariant != _selectedThemeVariant.value) {
            _selectedThemeVariant.value = newVariant
            appPrefs.edit().putString(KEY_SELECTED_THEME, newVariant).apply()
            Log.d("MainViewModel", "Theme updated and saved: $newVariant")
        }
    }

    private val _todayDateString = DateUtil.getCurrentDateString() // Fixed for "today's" data

    // --- State for Date Picker and Historical View ---
    private val _selectedDateForHistory = MutableStateFlow(_todayDateString) // Default to today
    val selectedDateForHistory: StateFlow<String> = _selectedDateForHistory.asStateFlow()

    // --- App Detail Screen Specific States ---
    private val _appDetailAppName = MutableStateFlow<String?>(null)
    val appDetailAppName: StateFlow<String?> = _appDetailAppName.asStateFlow()

    private val _appDetailAppIcon = MutableStateFlow<Drawable?>(null)
    val appDetailAppIcon: StateFlow<Drawable?> = _appDetailAppIcon.asStateFlow()

    private val _appDetailChartData = MutableStateFlow<List<AppDailyDetailData>>(emptyList())
    val appDetailChartData: StateFlow<List<AppDailyDetailData>> = _appDetailChartData.asStateFlow()

    private val _currentChartPeriodType = MutableStateFlow(ChartPeriodType.WEEKLY)
    val currentChartPeriodType: StateFlow<ChartPeriodType> = _currentChartPeriodType.asStateFlow()

    // Represents the anchor date for the current chart view.
    // For WEEKLY, it's the last day of the week. For MONTHLY, the first day of the month.
    // For DAILY, it's the specific day being focused on within its week.
    private val _currentChartReferenceDate = MutableStateFlow(DateUtil.getCurrentDateString())
    val currentChartReferenceDate: StateFlow<String> = _currentChartReferenceDate.asStateFlow()

    // --- New StateFlows for App Detail Summary ---
    private val _appDetailFocusedUsageDisplay = MutableStateFlow("0m")
    val appDetailFocusedUsageDisplay: StateFlow<String> = _appDetailFocusedUsageDisplay.asStateFlow()

    private val _appDetailPeriodDescriptionText = MutableStateFlow<String?>(null)
    val appDetailPeriodDescriptionText: StateFlow<String?> = _appDetailPeriodDescriptionText.asStateFlow()

    private val _appDetailFocusedPeriodDisplay = MutableStateFlow("")
    val appDetailFocusedPeriodDisplay: StateFlow<String> = _appDetailFocusedPeriodDisplay.asStateFlow()

    private val _appDetailComparisonText = MutableStateFlow<String?>(null)
    val appDetailComparisonText: StateFlow<String?> = _appDetailComparisonText.asStateFlow()

    private val _appDetailComparisonIsPositive = MutableStateFlow(true) // true for green, false for red
    val appDetailComparisonIsPositive: StateFlow<Boolean> = _appDetailComparisonIsPositive.asStateFlow()

    private val _appDetailWeekNumberDisplay = MutableStateFlow<String?>(null)
    val appDetailWeekNumberDisplay: StateFlow<String?> = _appDetailWeekNumberDisplay.asStateFlow()

    // --- Greeting ---
    val greeting: StateFlow<String> = flow { emit(GreetingUtil.getGreeting()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Hello! 👋")

    // --- Helper function to filter and map DailyAppUsageRecords to AppUsageUiItems ---
    private suspend fun processSingleUsageRecordToUiItem(record: DailyAppUsageRecord): AppUsageUiItem? {
        return withContext(Dispatchers.IO) {
            try {
                val pm = application.packageManager
                val appInfo = pm.getApplicationInfo(record.packageName, 0)
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
                AppUsageUiItem(record.packageName, record.packageName.substringAfterLast('.', record.packageName), null, record.usageTimeMillis, DateUtil.formatDuration(record.usageTimeMillis), record.packageName)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error processing usage app data for ${record.packageName}", e)
                null
            }
        }
    }
    
    private suspend fun processUsageRecords(records: List<DailyAppUsageRecord>): List<AppUsageUiItem> {
        return withContext(Dispatchers.IO) {
            records.mapNotNull { record ->
                processSingleUsageRecordToUiItem(record) // Use the refactored single item processor
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
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "0 m" to "0.00 miles")


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


    // --- Top Used App in Last 7 Days ---
    val topUsedAppLast7Days: StateFlow<AppUsageUiItem?> = flow {
        val calendar = Calendar.getInstance()
        val endDateString = DateUtil.formatDate(calendar.timeInMillis) // Today
        calendar.add(Calendar.DAY_OF_YEAR, -6) // Go back 6 days to make it a 7-day range including today
        val startDateString = DateUtil.formatDate(calendar.timeInMillis)

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
            }.collect() // Collect the inner flow
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)


    // Helper to map ScrollData to ScrollUiItem
    private suspend fun mapToAppScrollUiItems(appScrollDataList: List<AppScrollData>): List<AppScrollUiItem> {
        return withContext(Dispatchers.IO) {
            appScrollDataList.mapNotNull { appData ->
                try {
                    val pm = application.packageManager
                    val appInfo = pm.getApplicationInfo(appData.packageName, 0)
                    // NO EXPLICIT FILTERING HERE
                    AppScrollUiItem(
                        id = appData.packageName,
                        appName = pm.getApplicationLabel(appInfo).toString(),
                        icon = pm.getApplicationIcon(appData.packageName),
                        totalScroll = appData.totalScroll, // This should be mapped to a relevant field in AppScrollUiItem if it's different from AppUsageUiItem
                        packageName = appData.packageName
                        // Note: AppScrollUiItem currently has totalScroll: Long. AppUsageUiItem has usageTimeMillis. This mapping might need adjustment
                        // For now, assuming AppScrollUiItem structure is intended for scroll display and the data source is correct.
                    )
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.w("MainViewModel", "Package info not found for scroll item ${appData.packageName}, creating fallback UI item.")
                    AppScrollUiItem(appData.packageName, appData.packageName.substringAfterLast('.', appData.packageName), null, appData.totalScroll, appData.packageName) // Fallback with totalScroll as a placeholder for a time field if needed
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

    // Methods for App Detail Screen
    fun loadAppDetailsInfo(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pm = application.packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                _appDetailAppName.value = pm.getApplicationLabel(appInfo).toString()
                _appDetailAppIcon.value = pm.getApplicationIcon(packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w("MainViewModel", "App info not found for $packageName in AppDetailScreen")
                _appDetailAppName.value = packageName // Fallback to package name
                _appDetailAppIcon.value = null
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading app info for $packageName", e)
                _appDetailAppName.value = packageName // Fallback
                _appDetailAppIcon.value = null
            }
        }
        // When app details are first loaded, also trigger chart data load for the default period
        loadAppDetailChartData(packageName, _currentChartPeriodType.value, _currentChartReferenceDate.value)
    }

    fun loadAppDetailChartData(packageName: String, period: ChartPeriodType, referenceDate: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _appDetailChartData.value = emptyList() // Clear previous data / show loading
            // Reset summary fields
            _appDetailFocusedUsageDisplay.value = "..."
            _appDetailFocusedPeriodDisplay.value = "Loading..."
            _appDetailComparisonText.value = null
            _appDetailWeekNumberDisplay.value = null
            _appDetailPeriodDescriptionText.value = null

            Log.d("MainViewModel", "Loading chart data for $packageName, Period: $period, RefDate: $referenceDate")

            val dateStringsForCurrentPeriod = calculateDateStringsForPeriod(period, referenceDate)
            if (dateStringsForCurrentPeriod.isEmpty()) {
                Log.w("MainViewModel", "Date strings for current period resulted in empty list.")
                _appDetailChartData.value = emptyList()
                _appDetailFocusedUsageDisplay.value = "Error"
                _appDetailFocusedPeriodDisplay.value = "No dates"
                return@launch
            }

            val usageDataDeferred = async { repository.getUsageForPackageAndDates(packageName, dateStringsForCurrentPeriod) }
            val scrollDataDeferred = async { repository.getAggregatedScrollForPackageAndDates(packageName, dateStringsForCurrentPeriod) }

            val currentPeriodUsageRecords = usageDataDeferred.await()
            val currentPeriodScrollRecords = scrollDataDeferred.await()

            val currentUsageMap = currentPeriodUsageRecords.associateBy { it.dateString }
            val currentScrollMap = currentPeriodScrollRecords.associateBy { it.date }

            val currentCombinedData = dateStringsForCurrentPeriod.map { dateStr ->
                AppDailyDetailData(
                    date = dateStr,
                    usageTimeMillis = currentUsageMap[dateStr]?.usageTimeMillis ?: 0L,
                    scrollUnits = currentScrollMap[dateStr]?.totalScroll ?: 0L
                )
            }
            _appDetailChartData.value = currentCombinedData
            Log.d("MainViewModel", "Chart data loaded: ${currentCombinedData.size} points for current period.")

            // --- Calculate and Update Summary Information ---
            val calendar = Calendar.getInstance()
            DateUtil.parseDateString(referenceDate)?.let { calendar.time = it }

            when (period) {
                ChartPeriodType.DAILY -> {
                    val focusedDayData = currentCombinedData.find { it.date == referenceDate }
                    _appDetailFocusedUsageDisplay.value = DateUtil.formatDuration(focusedDayData?.usageTimeMillis ?: 0L)
                    _appDetailPeriodDescriptionText.value = "USAGE ON SELECTED DAY"
                    
                    val sdf = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                    _appDetailFocusedPeriodDisplay.value = sdf.format(calendar.time)
                    _appDetailWeekNumberDisplay.value = "Week ${getWeekOfYear(calendar)}"
                    _appDetailComparisonText.value = null // No comparison for daily
                }
                ChartPeriodType.WEEKLY -> {
                    val currentPeriodAverage = calculateAverageUsage(currentCombinedData)
                    _appDetailFocusedUsageDisplay.value = DateUtil.formatDuration(currentPeriodAverage)
                    _appDetailPeriodDescriptionText.value = "DAILY AVERAGE THIS WEEK"

                    val (startOfWeekStr, endOfWeekStr) = getPeriodDisplayStrings(period, referenceDate, includeYear = false)
                    _appDetailFocusedPeriodDisplay.value = "$startOfWeekStr - $endOfWeekStr"
                    _appDetailWeekNumberDisplay.value = "Week ${getWeekOfYear(calendar)}"

                    val (prevWeekDateStrings, _) = calculatePreviousPeriodDateStrings(period, referenceDate)
                    if (prevWeekDateStrings.isNotEmpty()) {
                        val prevWeekUsageRecords = repository.getUsageForPackageAndDates(packageName, prevWeekDateStrings)
                        val prevWeekAverage = calculateAverageUsageFromRecords(prevWeekUsageRecords)
                        updateComparisonText(currentPeriodAverage, prevWeekAverage, "last week")
                    } else {
                         _appDetailComparisonText.value = "No data for comparison"
                    }
                }
                ChartPeriodType.MONTHLY -> {
                    val currentPeriodAverage = calculateAverageUsage(currentCombinedData)
                    _appDetailFocusedUsageDisplay.value = DateUtil.formatDuration(currentPeriodAverage)
                    _appDetailPeriodDescriptionText.value = "DAILY AVERAGE THIS MONTH"

                    val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                    _appDetailFocusedPeriodDisplay.value = sdf.format(calendar.time)
                     _appDetailWeekNumberDisplay.value = null // No week number for monthly

                    val (prevMonthDateStrings, _) = calculatePreviousPeriodDateStrings(period, referenceDate)
                     if (prevMonthDateStrings.isNotEmpty()) {
                        val prevMonthUsageRecords = repository.getUsageForPackageAndDates(packageName, prevMonthDateStrings)
                        val prevMonthAverage = calculateAverageUsageFromRecords(prevMonthUsageRecords)
                        updateComparisonText(currentPeriodAverage, prevMonthAverage, "last month")
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

    private fun calculateAverageUsageFromRecords(data: List<DailyAppUsageRecord>): Long {
        if (data.isEmpty()) return 0L
        return data.sumOf { it.usageTimeMillis } / data.size
    }

    private fun updateComparisonText(currentAvg: Long, previousAvg: Long, periodName: String) {
        if (previousAvg == 0L) {
            _appDetailComparisonText.value = if (currentAvg > 0) "Up from no usage $periodName" else "No usage"
            _appDetailComparisonIsPositive.value = currentAvg > 0
            return
        }
        val difference = currentAvg - previousAvg
        val percentageChange = (difference.toDouble() / previousAvg.toDouble()) * 100

        _appDetailComparisonIsPositive.value = percentageChange >= 0
        val sign = if (percentageChange >= 0) "+" else ""
        // Only show decimal if it's not a whole number or to avoid -0%
        val percentageString = if (percentageChange.rem(1).equals(0.0) && percentageChange != 0.0) {
            String.format(Locale.US, "%s%.0f%%", sign, percentageChange)
        } else {
            String.format(Locale.US, "%s%.1f%%", sign, percentageChange)
        }
         _appDetailComparisonText.value = "$percentageString vs $periodName"
    }


    private fun calculatePreviousPeriodDateStrings(period: ChartPeriodType, currentReferenceDateStr: String): Pair<List<String>, String> {
        val calendar = Calendar.getInstance()
        DateUtil.parseDateString(currentReferenceDateStr)?.let { calendar.time = it }
        lateinit var newReferenceDateStr: String

        val previousPeriodDates = when (period) {
            ChartPeriodType.DAILY -> { // For daily, previous period is previous week
                calendar.add(Calendar.WEEK_OF_YEAR, -1)
                newReferenceDateStr = DateUtil.formatDate(calendar.time)
                calculateDateStringsForPeriod(ChartPeriodType.WEEKLY, newReferenceDateStr) // Get full previous week
            }
            ChartPeriodType.WEEKLY -> {
                calendar.add(Calendar.WEEK_OF_YEAR, -1) // Move to previous week
                newReferenceDateStr = DateUtil.formatDate(calendar.time) // End of previous week
                calculateDateStringsForPeriod(ChartPeriodType.WEEKLY, newReferenceDateStr)
            }
            ChartPeriodType.MONTHLY -> {
                calendar.add(Calendar.MONTH, -1) // Move to previous month
                newReferenceDateStr = DateUtil.formatDate(calendar.time) // A day in previous month
                calculateDateStringsForPeriod(ChartPeriodType.MONTHLY, newReferenceDateStr)
            }
        }
        return Pair(previousPeriodDates, newReferenceDateStr)
    }

    private fun getPeriodDisplayStrings(period: ChartPeriodType, referenceDateStr: String, includeYear: Boolean = true): Pair<String, String> {
        val calendar = Calendar.getInstance()
        DateUtil.parseDateString(referenceDateStr)?.let { calendar.time = it }

        val monthDayFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        val fullDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

        return when (period) {
            ChartPeriodType.DAILY -> { 
                val sdf = if (includeYear) SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()) else SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                Pair(sdf.format(calendar.time), "")
            }
            ChartPeriodType.WEEKLY -> {
                val endOfWeek = calendar.time // referenceDateStr is end of week
                val endCal = Calendar.getInstance().apply { time = endOfWeek }
                calendar.add(Calendar.DAY_OF_YEAR, -6)
                val startOfWeek = calendar.time
                val startCal = Calendar.getInstance().apply { time = startOfWeek }

                val startFormat = monthDayFormat
                val endFormat = if (startCal.get(Calendar.YEAR) != endCal.get(Calendar.YEAR) || !includeYear) {
                    if (includeYear) fullDateFormat else monthDayFormat // if year is different show full date for end, or if year not included just month day
                } else if (startCal.get(Calendar.MONTH) != endCal.get(Calendar.MONTH)) {
                    monthDayFormat // if month is different show month day for end
                } else {
                    SimpleDateFormat("d", Locale.getDefault()) // if same month and year, show only day for end
                }
                // if includeYear is false, and years are different, we might have an issue. For now, this assumes week ranges are usually within same year or year is explicitly handled by caller with includeYear
                val yearSuffix = if (includeYear && startCal.get(Calendar.YEAR) == endCal.get(Calendar.YEAR)) ", ${startCal.get(Calendar.YEAR)}" else ""
                
                if (!includeYear){
                     return Pair(startFormat.format(startOfWeek), SimpleDateFormat("MMM d", Locale.getDefault()).format(endOfWeek)) // Simpler format when no year needed for primary display
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
        DateUtil.parseDateString(referenceDateStr)?.let { calendar.time = it }

        return when (period) {
            ChartPeriodType.DAILY -> { // For DAILY, fetch data for the week containing the referenceDateStr
                // Set calendar to the reference date
                DateUtil.parseDateString(referenceDateStr)?.let { calendar.time = it }
                // Find the start of that week (e.g., Monday or Sunday based on Locale)
                // For simplicity, let's assume Monday is the first day of the week.
                // Adjust if your locale or definition of week start differs.
                val firstDayOfWeekConstant = calendar.firstDayOfWeek // Use locale's first day
                while (calendar.get(Calendar.DAY_OF_WEEK) != firstDayOfWeekConstant) {
                    calendar.add(Calendar.DAY_OF_YEAR, -1)
                }
                // Now calendar is at the start of the week containing referenceDateStr
                (0..6).map {
                    val dayCal = Calendar.getInstance().apply { time = calendar.time }
                    dayCal.add(Calendar.DAY_OF_YEAR, it)
                    DateUtil.formatDate(dayCal.time)
                } // This list is already in chronological order
            }
            ChartPeriodType.WEEKLY -> {
                // For WEEKLY, referenceDateStr is considered the end of the week.
                // We want 7 days ending on referenceDateStr.
                DateUtil.parseDateString(referenceDateStr)?.let { calendar.time = it } // Ensure calendar is on referenceDateStr
                (0..6).map {
                    val dayCal = Calendar.getInstance().apply { time = calendar.time }
                    dayCal.add(Calendar.DAY_OF_YEAR, -it)
                    DateUtil.formatDate(dayCal.time)
                }.reversed() // ensures chronological order for the chart
            }
            ChartPeriodType.MONTHLY -> {
                // referenceDateStr is any day in the target month.
                // Set to first day of month for consistency for data fetching range
                DateUtil.parseDateString(referenceDateStr)?.let { calendar.time = it } // Ensure calendar is on referenceDateStr
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                (0 until daysInMonth).map {
                    val dayCal = Calendar.getInstance().apply { time = calendar.time }
                    dayCal.add(Calendar.DAY_OF_MONTH, it)
                    DateUtil.formatDate(dayCal.time)
                }
            }
        }
    }

    fun changeChartPeriod(packageName: String, newPeriod: ChartPeriodType) {
        if (newPeriod != _currentChartPeriodType.value) {
            _currentChartPeriodType.value = newPeriod
            // Reset reference date to today when period changes, for simplicity
            _currentChartReferenceDate.value = DateUtil.getCurrentDateString() 
            loadAppDetailChartData(packageName, _currentChartPeriodType.value, _currentChartReferenceDate.value)
        }
    }

    fun navigateChartDate(packageName: String, direction: Int) { // 1 for next, -1 for previous
        val currentPeriod = _currentChartPeriodType.value
        val currentRefDate = _currentChartReferenceDate.value
        val calendar = Calendar.getInstance()
        DateUtil.parseDateString(currentRefDate)?.let { calendar.time = it }

        when (currentPeriod) {
            // For DAILY, navigation should shift the focused day, but the chart still shows the whole week.
            // The reference date for DAILY needs to be the specific day.
            ChartPeriodType.DAILY -> calendar.add(Calendar.DAY_OF_YEAR, direction) 
            ChartPeriodType.WEEKLY -> calendar.add(Calendar.WEEK_OF_YEAR, direction)
            ChartPeriodType.MONTHLY -> calendar.add(Calendar.MONTH, direction)
        }
        _currentChartReferenceDate.value = DateUtil.formatDate(calendar.time)
        loadAppDetailChartData(packageName, currentPeriod, _currentChartReferenceDate.value)
    }

    fun setFocusedDate(packageName: String, newDate: String) {
        if (_currentChartReferenceDate.value != newDate) {
            _currentChartReferenceDate.value = newDate
            // We only expect this to be called when currentPeriodType is DAILY,
            // where referenceDate is the specific day of focus.
            // For other periods, navigation is by full period steps.
            if (_currentChartPeriodType.value == ChartPeriodType.DAILY) {
                 loadAppDetailChartData(packageName, ChartPeriodType.DAILY, newDate)
            } else {
                // Log a warning or handle unexpected call if necessary, though UI should prevent this.
                Log.w("MainViewModel", "setFocusedDate called with period type ${_currentChartPeriodType.value}. Expected DAILY.")
                // Still, to be safe, reload with current period type if called unexpectedly.
                loadAppDetailChartData(packageName, _currentChartPeriodType.value, newDate)
            }
        }
    }
}
