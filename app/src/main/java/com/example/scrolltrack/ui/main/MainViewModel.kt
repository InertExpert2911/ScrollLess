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

// Palette API Imports
import android.graphics.Bitmap
import android.graphics.Canvas // For drawing drawable to bitmap if needed
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette

// Constants for SharedPreferences (can be moved to a companion object or a separate file if preferred)
private const val PREFS_APP_SETTINGS = "ScrollTrackAppSettings"
private const val KEY_SELECTED_THEME = "selected_theme_variant"
private const val THEME_LIGHT = "light"
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

    // Ensure _todayDateString is initialized before _selectedDateForHistory
    private val _todayDateString = DateUtil.getCurrentLocalDateString()
    
    private val _selectedDateForHistory = MutableStateFlow(_todayDateString) // Default to today
    val selectedDateForHistory: StateFlow<String> = _selectedDateForHistory.asStateFlow()

    val isTodaySelectedForHistory: StateFlow<Boolean> = _selectedDateForHistory.map { it == _todayDateString }.stateIn(viewModelScope, SharingStarted.Lazily, true)

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

    // --- App Detail Screen Specific States ---
    private val _appDetailPackageName = MutableStateFlow<String?>(null)

    private val _appDetailAppName = MutableStateFlow<String?>(null)
    val appDetailAppName: StateFlow<String?> = _appDetailAppName.asStateFlow()

    private val _appDetailAppIcon = MutableStateFlow<Drawable?>(null)
    val appDetailAppIcon: StateFlow<Drawable?> = _appDetailAppIcon.asStateFlow()

    // Palette API related states
    private val _appDetailAppBarColor = MutableStateFlow<Color?>(null)
    val appDetailAppBarColor: StateFlow<Color?> = _appDetailAppBarColor.asStateFlow()

    private val _appDetailAppBarContentColor = MutableStateFlow<Color?>(null)
    val appDetailAppBarContentColor: StateFlow<Color?> = _appDetailAppBarContentColor.asStateFlow()

    private val _appDetailBackgroundColor = MutableStateFlow<Color?>(null)
    val appDetailBackgroundColor: StateFlow<Color?> = _appDetailBackgroundColor.asStateFlow()

    private val _appDetailChartData = MutableStateFlow<List<AppDailyDetailData>>(emptyList())
    val appDetailChartData: StateFlow<List<AppDailyDetailData>> = _appDetailChartData.asStateFlow()

    private val _currentChartPeriodType = MutableStateFlow(ChartPeriodType.WEEKLY)
    val currentChartPeriodType: StateFlow<ChartPeriodType> = _currentChartPeriodType.asStateFlow()

    // Represents the anchor date for the current chart view.
    // For WEEKLY, it's the last day of the week. For MONTHLY, the first day of the month.
    // For DAILY, it's the specific day being focused on within its week.
    private val _currentChartReferenceDate = MutableStateFlow(DateUtil.getCurrentLocalDateString()) // Changed
    val currentChartReferenceDate: StateFlow<String> = _currentChartReferenceDate.asStateFlow()

    // --- New StateFlows for App Detail Summary ---
    private val _appDetailFocusedUsageDisplay = MutableStateFlow("0m") // Reverted initial value
    val appDetailFocusedUsageDisplay: StateFlow<String> = _appDetailFocusedUsageDisplay.asStateFlow()

    private val _appDetailPeriodDescriptionText = MutableStateFlow<String?>(null)
    val appDetailPeriodDescriptionText: StateFlow<String?> = _appDetailPeriodDescriptionText.asStateFlow()

    private val _appDetailFocusedPeriodDisplay = MutableStateFlow("") // Reverted initial value (or to original if known)
    val appDetailFocusedPeriodDisplay: StateFlow<String> = _appDetailFocusedPeriodDisplay.asStateFlow()

    private val _appDetailComparisonText = MutableStateFlow<String?>(null)
    val appDetailComparisonText: StateFlow<String?> = _appDetailComparisonText.asStateFlow()

    // private val _appDetailComparisonIsPositive = MutableStateFlow(true) // true for green, false for red
    // val appDetailComparisonIsPositive: StateFlow<Boolean> = _appDetailComparisonIsPositive.asStateFlow()
    // Replaced by ComparisonColorType and ComparisonIconType

    private val _appDetailComparisonIconType = MutableStateFlow(ComparisonIconType.NONE)
    val appDetailComparisonIconType: StateFlow<ComparisonIconType> = _appDetailComparisonIconType.asStateFlow()

    private val _appDetailComparisonColorType = MutableStateFlow(ComparisonColorType.GREY) // Default to Grey or a suitable default
    val appDetailComparisonColorType: StateFlow<ComparisonColorType> = _appDetailComparisonColorType.asStateFlow()

    private val _appDetailWeekNumberDisplay = MutableStateFlow<String?>(null)
    val appDetailWeekNumberDisplay: StateFlow<String?> = _appDetailWeekNumberDisplay.asStateFlow()

    private val _appDetailFocusedScrollDisplay = MutableStateFlow("0 m") // Reverted initial value
    val appDetailFocusedScrollDisplay: StateFlow<String> = _appDetailFocusedScrollDisplay.asStateFlow()

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
            .map { records -> processUsageRecords(records) } 
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // New version using repository.getTotalUsageTimeMillisForDate directly for consistency
    val totalPhoneUsageTodayMillis: StateFlow<Long> = flow {
        // Emit initial loading state or a sensible default if desired, though repository flow handles nulls.
        // This will emit whenever the underlying repository call would emit if it were a direct flow.
        // However, getTotalUsageTimeMillisForDate is a suspend fun. We need to call it appropriately.
        // For a one-shot fetch that updates a StateFlow, or if it were a Flow from repo:
        emit(repository.getTotalUsageTimeMillisForDate(_todayDateString) ?: 0L)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0L)

    val totalPhoneUsageTodayFormatted: StateFlow<String> =
        totalPhoneUsageTodayMillis.map {
            formatTotalUsageTime(it) // formatTotalUsageTime already handles null and 0L
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
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "0 m" to "0.00 miles") // Reverted


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
    private val _selectedDateForScrollDetail = MutableStateFlow(DateUtil.getCurrentLocalDateString()) // Default to today, changed
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

    // Function to update the selected date for the scroll detail screen
    fun updateSelectedDateForScrollDetail(dateMillis: Long) {
        _selectedDateForScrollDetail.value = DateUtil.formatDateToYyyyMmDdString(Date(dateMillis))
    }
    fun resetSelectedDateForScrollDetailToToday() {
        _selectedDateForScrollDetail.value = DateUtil.getCurrentLocalDateString() // Changed
    }

    // --- Top Used App in Last 7 Days ---
    val topUsedAppLast7Days: StateFlow<AppUsageUiItem?> = flow {
        val calendar = Calendar.getInstance()
        val endDateString = DateUtil.formatDateToYyyyMmDdString(calendar.time) // Today, Changed
        calendar.add(Calendar.DAY_OF_YEAR, -6) // Go back 6 days to make it a 7-day range including today
        val startDateString = DateUtil.formatDateToYyyyMmDdString(calendar.time) // Changed

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
        val newDateString = DateUtil.formatDateToYyyyMmDdString(Date(dateMillis))
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
        // Reset to default period and date every time this screen is loaded
        _currentChartPeriodType.value = ChartPeriodType.DAILY // Default to Daily view
        _currentChartReferenceDate.value = DateUtil.getCurrentLocalDateString() // Changed
        _appDetailPackageName.value = packageName // This line IS needed to load app-specific data

        // Reset palette colors for the new app
        _appDetailAppBarColor.value = null
        _appDetailAppBarContentColor.value = null
        _appDetailBackgroundColor.value = null

        viewModelScope.launch(Dispatchers.IO) {
            var appIconDrawable: Drawable? = null
            try {
                val pm = application.packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                _appDetailAppName.value = pm.getApplicationLabel(appInfo).toString()
                appIconDrawable = pm.getApplicationIcon(packageName)
                _appDetailAppIcon.value = appIconDrawable
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w("MainViewModel", "App info not found for $packageName in AppDetailScreen")
                _appDetailAppName.value = packageName // Fallback to package name
                _appDetailAppIcon.value = null
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading app info for $packageName", e)
                _appDetailAppName.value = packageName // Fallback
                _appDetailAppIcon.value = null
            }

            // Generate Palette from icon
            appIconDrawable?.let { iconDrawable ->
                val bitmap = if (iconDrawable is BitmapDrawable) {
                    iconDrawable.bitmap
                } else {
                    // Fallback for other drawable types: draw to a new Bitmap
                    // Ensure width and height are positive
                    val width = if (iconDrawable.intrinsicWidth > 0) iconDrawable.intrinsicWidth else 1
                    val height = if (iconDrawable.intrinsicHeight > 0) iconDrawable.intrinsicHeight else 1
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    iconDrawable.setBounds(0, 0, canvas.width, canvas.height)
                    iconDrawable.draw(canvas)
                    bmp
                }

                if (bitmap != null) {
                    Palette.from(bitmap).generate { palette ->
                        val dominantSwatch = palette?.dominantSwatch
                        val vibrantSwatch = palette?.vibrantSwatch

                        // Prefer vibrant, fallback to dominant for AppBar background
                        val appBarSwatch = vibrantSwatch ?: dominantSwatch
                        // For background, try a lighter muted or light vibrant, or light dominant
                        val backgroundSwatch = palette?.lightMutedSwatch ?: palette?.lightVibrantSwatch ?: palette?.dominantSwatch?.let { swatch ->
                            // Create a custom lighter version of dominant if others fail
                            val lightDominantRgb = Color(swatch.rgb).copy(alpha = 0.1f).toArgb() // Use toArgb()
                            Palette.Swatch(lightDominantRgb, swatch.population)
                        }

                        _appDetailAppBarColor.value = appBarSwatch?.rgb?.let { Color(it) }
                        _appDetailAppBarContentColor.value = appBarSwatch?.titleTextColor?.let { Color(it) } 
                                                          ?: appBarSwatch?.bodyTextColor?.let { Color(it) } // Fallback for content color
                        
                        _appDetailBackgroundColor.value = backgroundSwatch?.rgb?.let { Color(it) } 
                                                        ?: _appDetailAppBarColor.value?.copy(alpha = 0.05f) // Fallback to very light app bar color

                        // Log extracted colors
                        Log.d("MainViewModel", "Palette: AppBarColor=${_appDetailAppBarColor.value}, ContentColor=${_appDetailAppBarContentColor.value}, BGColor=${_appDetailBackgroundColor.value}")
                    }
                }
            } // If appIconDrawable is null, colors remain null (handled by UI fallbacks)
        }
        // When app details are first loaded, also trigger chart data load for the default period
        loadAppDetailChartData(packageName, _currentChartPeriodType.value, _currentChartReferenceDate.value)
    }

    fun loadAppDetailChartData(packageName: String, period: ChartPeriodType, referenceDate: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _appDetailChartData.value = emptyList() // Clear previous data / show loading
            // Reset summary fields
            _appDetailFocusedUsageDisplay.value = "..." // Or "0m" or original placeholder
            _appDetailFocusedPeriodDisplay.value = "..." // Or empty or original placeholder
            _appDetailFocusedScrollDisplay.value = "..." // Or "0 m" or original placeholder
            _appDetailComparisonText.value = null
            _appDetailWeekNumberDisplay.value = null
            _appDetailPeriodDescriptionText.value = null

            Log.d("MainViewModel", "Loading chart data for $packageName, Period: $period, RefDate: $referenceDate")

            val dateStringsForCurrentPeriod = calculateDateStringsForPeriod(period, referenceDate)
            if (dateStringsForCurrentPeriod.isEmpty()) {
                Log.w("MainViewModel", "Date strings for current period resulted in empty list.")
                _appDetailChartData.value = emptyList()
                _appDetailFocusedUsageDisplay.value = DateUtil.formatDuration(0L)
                _appDetailFocusedPeriodDisplay.value = ""
                _appDetailFocusedScrollDisplay.value = ConversionUtil.formatScrollDistance(0L, application.applicationContext).first
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
            DateUtil.parseLocalDateString(referenceDate)?.let { calendar.time = it }
            val sdfDisplay = SimpleDateFormat("EEE, MMM d", Locale.getDefault()) // Example original formatter
            val sdfMonth = SimpleDateFormat("MMMM yyyy", Locale.getDefault()) // Example original formatter


            when (period) {
                ChartPeriodType.DAILY -> {
                    val focusedDayData = currentCombinedData.firstOrNull { it.date == referenceDate }
                    _appDetailFocusedUsageDisplay.value = DateUtil.formatDuration(focusedDayData?.usageTimeMillis ?: 0L)
                    _appDetailFocusedScrollDisplay.value = ConversionUtil.formatScrollDistance(focusedDayData?.scrollUnits ?: 0L, application.applicationContext).first
                    _appDetailPeriodDescriptionText.value = "Daily Summary" // Or original text
                    _appDetailFocusedPeriodDisplay.value = sdfDisplay.format(calendar.time) // Reverted to example original
                    _appDetailWeekNumberDisplay.value = null 

                    // Original comparison logic for DAILY might have been simpler or non-existent
                    _appDetailComparisonText.value = null // Reverting to simpler no comparison for daily
                }
                ChartPeriodType.WEEKLY -> {
                    val currentPeriodAverageUsage = calculateAverageUsage(currentCombinedData) // Assuming calculateAverageUsage is original
                    _appDetailFocusedUsageDisplay.value = DateUtil.formatDuration(currentPeriodAverageUsage)
                    val currentPeriodAverageScroll = calculateAverageScroll(currentCombinedData) // Assuming calculateAverageScroll is original
                    _appDetailFocusedScrollDisplay.value = ConversionUtil.formatScrollDistance(currentPeriodAverageScroll, application.applicationContext).first
                    
                    _appDetailPeriodDescriptionText.value = "Weekly Average" // Or original text
                    // Example of reverting week range display
                    val (startOfWeekStr, endOfWeekStr) = getPeriodDisplayStrings(period, referenceDate) // Assuming getPeriodDisplayStrings is original or being reverted separately
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
                    val currentPeriodAverageScroll = calculateAverageScroll(currentCombinedData)
                    _appDetailFocusedScrollDisplay.value = ConversionUtil.formatScrollDistance(currentPeriodAverageScroll, application.applicationContext).first

                    _appDetailPeriodDescriptionText.value = "Monthly Average" // Or original text
                    _appDetailFocusedPeriodDisplay.value = sdfMonth.format(calendar.time) // Reverted to example original
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

    private fun calculateAverageScroll(data: List<AppDailyDetailData>): Long { // New function for average scroll
        if (data.isEmpty()) return 0L
        return data.sumOf { it.scrollUnits } / data.size
    }

    private fun calculateAverageUsageFromRecords(data: List<DailyAppUsageRecord>): Long {
        if (data.isEmpty()) return 0L
        return data.sumOf { it.usageTimeMillis } / data.size
    }

    private fun updateComparisonText(currentAvg: Long, previousAvg: Long, periodName: String) {
        val currentAvgFormatted = DateUtil.formatDuration(currentAvg) // Removed short = true for now

        if (previousAvg == 0L) {
            if (currentAvg == 0L) {
                _appDetailComparisonText.value = "Still no usage" // vs $periodName might be redundant if context is clear
                _appDetailComparisonIconType.value = ComparisonIconType.NEUTRAL
                _appDetailComparisonColorType.value = ComparisonColorType.GREY
            } else {
                _appDetailComparisonText.value = "$currentAvgFormatted from no usage" // E.g. "1hr 20min from no usage"
                _appDetailComparisonIconType.value = ComparisonIconType.UP // Indicates increase from zero
                _appDetailComparisonColorType.value = ComparisonColorType.RED // Increase from zero is bad
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
        val percentageChange = abs((difference.toDouble() / previousAvg.toDouble()) * 100) // Use kotlin.math.abs
        val percentageString = String.format(Locale.US, "%.0f%%", percentageChange)

        if (currentAvg < previousAvg) { // Decreased usage - GOOD
            _appDetailComparisonText.value = "${percentageString} less vs last ${periodName}" // Explicitly ensure format
            _appDetailComparisonIconType.value = ComparisonIconType.DOWN
            _appDetailComparisonColorType.value = ComparisonColorType.GREEN
        } else { // Increased usage - BAD
            _appDetailComparisonText.value = "${percentageString} more vs last ${periodName}" // Explicitly ensure format
            _appDetailComparisonIconType.value = ComparisonIconType.UP
            _appDetailComparisonColorType.value = ComparisonColorType.RED
        }
    }

    private fun calculatePreviousPeriodDateStrings(period: ChartPeriodType, currentReferenceDateStr: String): Pair<List<String>, String> {
        val calendar = Calendar.getInstance()
        DateUtil.parseLocalDateString(currentReferenceDateStr)?.let { calendar.time = it } // Changed
        lateinit var newReferenceDateStr: String

        val previousPeriodDates = when (period) {
            ChartPeriodType.DAILY -> { // For daily, previous period is previous week
                calendar.add(Calendar.WEEK_OF_YEAR, -1)
                newReferenceDateStr = DateUtil.formatDateToYyyyMmDdString(calendar.time) // Changed
                calculateDateStringsForPeriod(ChartPeriodType.WEEKLY, newReferenceDateStr) // Get full previous week
            }
            ChartPeriodType.WEEKLY -> {
                calendar.add(Calendar.WEEK_OF_YEAR, -1) // Move to previous week
                newReferenceDateStr = DateUtil.formatDateToYyyyMmDdString(calendar.time) // End of previous week, Changed
                calculateDateStringsForPeriod(ChartPeriodType.WEEKLY, newReferenceDateStr)
            }
            ChartPeriodType.MONTHLY -> {
                calendar.add(Calendar.MONTH, -1) // Move to previous month
                newReferenceDateStr = DateUtil.formatDateToYyyyMmDdString(calendar.time) // A day in previous month, Changed
                calculateDateStringsForPeriod(ChartPeriodType.MONTHLY, newReferenceDateStr)
            }
        }
        return Pair(previousPeriodDates, newReferenceDateStr)
    }

    private fun getPeriodDisplayStrings(period: ChartPeriodType, referenceDateStr: String, includeYear: Boolean = true): Pair<String, String> {
        val calendar = Calendar.getInstance()
        DateUtil.parseLocalDateString(referenceDateStr)?.let { calendar.time = it } // Changed

        val monthDayFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        val fullDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

        return when (period) {
            ChartPeriodType.DAILY -> { 
                val sdf = if (includeYear) SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()) else SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                Pair(sdf.format(calendar.time), "")
            }
            ChartPeriodType.WEEKLY -> {
                // First, ensure the calendar is set to the Monday of the week of referenceDateStr
                DateUtil.parseLocalDateString(referenceDateStr)?.let { calendar.time = it }
                val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                val daysToSubtract = if (currentDayOfWeek == Calendar.SUNDAY) 6 else currentDayOfWeek - Calendar.MONDAY
                calendar.add(Calendar.DAY_OF_YEAR, -daysToSubtract)
                val startOfWeek = calendar.time // This is now Monday

                calendar.add(Calendar.DAY_OF_YEAR, 6) // Go to Sunday
                val endOfWeek = calendar.time // This is now Sunday

                val startCal = Calendar.getInstance().apply { time = startOfWeek }
                val endCal = Calendar.getInstance().apply { time = endOfWeek }

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
        DateUtil.parseLocalDateString(referenceDateStr)?.let { calendar.time = it } // Changed

        return when (period) {
            ChartPeriodType.DAILY, ChartPeriodType.WEEKLY -> {
                // For both DAILY and WEEKLY, we want the week to start on Monday and end on Sunday.
                // Set calendar to the reference date first.
                DateUtil.parseLocalDateString(referenceDateStr)?.let { calendar.time = it } // Changed

                // Adjust to Monday of that week.
                // Note: Calendar.DAY_OF_WEEK is 1 (Sunday) to 7 (Saturday).
                // We want to set it to Monday.
                val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                // Calculate days to subtract to get to Monday.
                // If currentDayOfWeek is Sunday (1), subtract 6 (or add 1 then subtract 7).
                // If currentDayOfWeek is Monday (2), subtract 0.
                // If currentDayOfWeek is Tuesday (3), subtract 1.
                // etc.
                val daysToSubtract = if (currentDayOfWeek == Calendar.SUNDAY) 6 else currentDayOfWeek - Calendar.MONDAY
                calendar.add(Calendar.DAY_OF_YEAR, -daysToSubtract)

                // Now calendar is at the Monday of the week containing referenceDateStr (or the week of referenceDateStr if weekly).
                (0..6).map {
                    val dayCal = Calendar.getInstance().apply { time = calendar.time }
                    dayCal.add(Calendar.DAY_OF_YEAR, it)
                    DateUtil.formatDateToYyyyMmDdString(dayCal.time) // Changed
                } // This list is already in chronological order: M, T, W, T, F, S, S
            }
            ChartPeriodType.MONTHLY -> {
                // referenceDateStr is any day in the target month.
                // Set to first day of month for consistency for data fetching range
                DateUtil.parseLocalDateString(referenceDateStr)?.let { calendar.time = it } // Changed
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                (0 until daysInMonth).map {
                    val dayCal = Calendar.getInstance().apply { time = calendar.time }
                    dayCal.add(Calendar.DAY_OF_MONTH, it)
                    DateUtil.formatDateToYyyyMmDdString(dayCal.time) // Changed
                }
            }
        }
    }

    fun changeChartPeriod(packageName: String, newPeriod: ChartPeriodType) {
        if (newPeriod != _currentChartPeriodType.value) {
            _currentChartPeriodType.value = newPeriod
            // Reset reference date to today when period changes, for simplicity
            _currentChartReferenceDate.value = DateUtil.getCurrentLocalDateString() 
            loadAppDetailChartData(packageName, _currentChartPeriodType.value, _currentChartReferenceDate.value)
        }
    }

    fun navigateChartDate(packageName: String, direction: Int) { // 1 for next, -1 for previous
        val currentPeriod = _currentChartPeriodType.value
        val currentRefDate = _currentChartReferenceDate.value
        val calendar = Calendar.getInstance()
        DateUtil.parseLocalDateString(currentRefDate)?.let { calendar.time = it } // Changed

        when (currentPeriod) {
            // For DAILY, navigation should shift the focused day, but the chart still shows the whole week.
            // The reference date for DAILY needs to be the specific day.
            ChartPeriodType.DAILY -> calendar.add(Calendar.DAY_OF_YEAR, direction) 
            ChartPeriodType.WEEKLY -> calendar.add(Calendar.WEEK_OF_YEAR, direction)
            ChartPeriodType.MONTHLY -> calendar.add(Calendar.MONTH, direction)
        }
        _currentChartReferenceDate.value = DateUtil.formatDateToYyyyMmDdString(calendar.time) // Changed
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
