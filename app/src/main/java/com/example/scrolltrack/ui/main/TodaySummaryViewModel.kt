package com.example.scrolltrack.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrolltrack.data.*
import com.example.scrolltrack.db.DailyAppUsageRecord
import com.example.scrolltrack.db.DailyDeviceSummary
import com.example.scrolltrack.ui.mappers.AppUiModelMapper
import com.example.scrolltrack.ui.model.AppUsageUiItem
import com.example.scrolltrack.ui.theme.AppTheme
import com.example.scrolltrack.util.Clock
import com.example.scrolltrack.util.ConversionUtil
import com.example.scrolltrack.util.DateUtil
import com.example.scrolltrack.util.GreetingUtil
import com.example.scrolltrack.util.PermissionUtils.isAccessibilityServiceEnabledFlow
import com.example.scrolltrack.util.PermissionUtils.isNotificationListenerEnabledFlow
import com.example.scrolltrack.util.PermissionUtils.isUsageStatsPermissionGrantedFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed class UiState {
    object InitialLoading : UiState()
    object Ready : UiState()
    object Refreshing : UiState()
    data class Error(val message: String) : UiState()
}

data class StatComparison(
    val percentageChange: Float,
    val isIncrease: Boolean
)

private data class PermissionState(
    val accessibility: Boolean,
    val usage: Boolean,
    val notification: Boolean
)

private data class TodayData(
    val greeting: String,
    val summary: DailyDeviceSummary?,
    val yesterdaySummary: DailyDeviceSummary?,
    val topApp: AppUsageUiItem?,
    val isRefreshing: Boolean,
    val snackbarMessage: String?,
    val selectedTheme: AppTheme,
    val isDarkMode: Boolean,
    val dailyAppUsage: List<DailyAppUsageRecord>,
    val scrollData: List<AppScrollData>,
    val limitsCount: Int,
    val setLimitSheetState: SetLimitSheetState?
)

data class TodaySummaryUiState(
    val greeting: String = "Welcome",
    val totalUsageTimeFormatted: String = "...",
    val totalUsageTimeMillis: Long = 0L,
    val todaysAppUsageUiList: List<AppUsageUiItem> = emptyList(),
    val topWeeklyApp: AppUsageUiItem? = null,
    val totalScrollToday: Long = 0L,
    val scrollDistanceTodayFormatted: Pair<String, String> = "0" to "m",
    val totalUnlocksToday: Int = 0,
    val totalNotificationsToday: Int = 0,
    val screenTimeComparison: StatComparison? = null,
    val unlocksComparison: StatComparison? = null,
    val notificationsComparison: StatComparison? = null,
    val scrollComparison: StatComparison? = null,
    val limitsCount: Int = 0,
    val isRefreshing: Boolean = false,
    val snackbarMessage: String? = null,
    val selectedTheme: AppTheme = AppTheme.CalmLavender,
    val isDarkMode: Boolean = true,
    val setLimitSheetState: SetLimitSheetState? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodaySummaryViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val scrollDataRepository: ScrollDataRepository,
    private val settingsRepository: SettingsRepository,
    private val limitsRepository: LimitsRepository,
    private val appUiModelMapper: AppUiModelMapper,
    private val greetingUtil: GreetingUtil,
    private val dateUtil: DateUtil,
    private val clock: Clock,
    private val conversionUtil: ConversionUtil
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.InitialLoading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _selectedDate = MutableStateFlow(dateUtil.getCurrentLocalDateString())
    private val _setLimitSheetState = MutableStateFlow<SetLimitSheetState?>(null)

    private var lastPermissionState: PermissionState? = null

    val isRefreshing: StateFlow<Boolean> = _uiState.map { it is UiState.Refreshing }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()


    val selectedThemePalette: StateFlow<AppTheme> = settingsRepository.selectedTheme
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppTheme.CalmLavender)

    val isDarkMode: StateFlow<Boolean> = settingsRepository.isDarkMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    var isAccessibilityServiceEnabled = isAccessibilityServiceEnabledFlow(context)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)
    var isUsagePermissionGranted = isUsageStatsPermissionGrantedFlow(context)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)
    val isNotificationListenerEnabled = isNotificationListenerEnabledFlow(context)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val greeting: StateFlow<String> = flow { emit(greetingUtil.getGreeting()) }
        .stateIn(viewModelScope, SharingStarted.Lazily, "Welcome")

    private val summaryData: StateFlow<DailyDeviceSummary?> = _selectedDate.flatMapLatest { date ->
        scrollDataRepository.getDeviceSummaryForDate(date)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val yesterdaySummaryData: StateFlow<DailyDeviceSummary?> = _selectedDate.flatMapLatest { date ->
        val yesterday = dateUtil.getPastDateString(1, dateUtil.parseLocalDate(date))
        scrollDataRepository.getDeviceSummaryForDate(yesterday)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val screenTimeComparison: StateFlow<StatComparison?> = combine(summaryData, yesterdaySummaryData) { today, yesterday ->
        calculateComparison(today?.totalUsageTimeMillis, yesterday?.totalUsageTimeMillis)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val unlocksComparison: StateFlow<StatComparison?> = combine(summaryData, yesterdaySummaryData) { today, yesterday ->
        calculateComparison(today?.totalUnlockCount?.toLong(), yesterday?.totalUnlockCount?.toLong())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val notificationsComparison: StateFlow<StatComparison?> = combine(summaryData, yesterdaySummaryData) { today, yesterday ->
        calculateComparison(today?.totalNotificationCount?.toLong(), yesterday?.totalNotificationCount?.toLong())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val totalPhoneUsageTodayFormatted: StateFlow<String> =
        summaryData.map { dateUtil.formatDuration(it?.totalUsageTimeMillis ?: 0L) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "...")

    val totalUnlocksToday: StateFlow<Int> = summaryData.map { it?.totalUnlockCount ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val totalNotificationsToday: StateFlow<Int> = summaryData.map { it?.totalNotificationCount ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val firstUnlockTime: StateFlow<String> = summaryData.map {
        it?.firstUnlockTimestampUtc?.let { ts -> dateUtil.formatUtcTimestampToTimeString(ts) } ?: "N/A"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "N/A")

    val lastUnlockTime: StateFlow<String> = summaryData.map {
        it?.lastUnlockTimestampUtc?.let { ts -> dateUtil.formatUtcTimestampToTimeString(ts) } ?: "N/A"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "N/A")

    val topWeeklyApp: StateFlow<AppUsageUiItem?> =
        scrollDataRepository.getUsageRecordsForDateRange(
            dateUtil.getPastDateString(6),
            dateUtil.getCurrentLocalDateString()
        ).map { weeklyUsageRecords ->
            if (weeklyUsageRecords.isEmpty()) {
                null
            } else {
                val topAppRecord = weeklyUsageRecords
                    .groupBy { it.packageName }
                    .mapValues { (_, records) -> records.sumOf { it.usageTimeMillis } }
                    .maxByOrNull { it.value }
                    ?.let { (packageName, totalUsage) ->
                        val recordsForPackage =
                            weeklyUsageRecords.filter { it.packageName == packageName }
                        DailyAppUsageRecord(
                            packageName = packageName,
                            dateString = dateUtil.getCurrentLocalDateString(),
                            usageTimeMillis = totalUsage,
                            activeTimeMillis = recordsForPackage.sumOf { it.activeTimeMillis },
                            appOpenCount = recordsForPackage.sumOf { it.appOpenCount },
                            notificationCount = recordsForPackage.sumOf { it.notificationCount }
                        )
                    }
                topAppRecord?.let { appUiModelMapper.mapToAppUsageUiItem(it) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val limitsCount: StateFlow<Int> = limitsRepository.getAllLimitedApps()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val todayDataFlow: Flow<TodayData> = combine(
        listOf(
            greeting,
            summaryData,
            yesterdaySummaryData,
            topWeeklyApp,
            isRefreshing,
            snackbarMessage,
            selectedThemePalette,
            isDarkMode,
            _selectedDate.flatMapLatest { scrollDataRepository.getAppUsageForDate(it) },
            _selectedDate.flatMapLatest { scrollDataRepository.getScrollDataForDate(it) },
            limitsCount,
            _setLimitSheetState
        )
    ) { args: Array<*> ->
        TodayData(
            greeting = args[0] as String,
            summary = args[1] as DailyDeviceSummary?,
            yesterdaySummary = args[2] as DailyDeviceSummary?,
            topApp = args[3] as AppUsageUiItem?,
            isRefreshing = args[4] as Boolean,
            snackbarMessage = args[5] as String?,
            selectedTheme = args[6] as AppTheme,
            isDarkMode = args[7] as Boolean,
            dailyAppUsage = args[8] as? List<DailyAppUsageRecord> ?: emptyList(),
            scrollData = args[9] as? List<AppScrollData> ?: emptyList(),
            limitsCount = args[10] as? Int ?: 0,
            setLimitSheetState = args[11] as? SetLimitSheetState
        )
    }

    val todaySummaryUiState: StateFlow<TodaySummaryUiState> = todayDataFlow.map { data ->
        val totalScroll = data.scrollData.sumOf { it.totalScroll }
        val yesterdayTotalScroll = 0L // Simplified for now
        TodaySummaryUiState(
            greeting = data.greeting,
            totalUsageTimeFormatted = dateUtil.formatDuration(data.summary?.totalUsageTimeMillis ?: 0L),
            totalUsageTimeMillis = data.summary?.totalUsageTimeMillis ?: 0L,
            todaysAppUsageUiList = data.dailyAppUsage.map { appUiModelMapper.mapToAppUsageUiItem(it) },
            topWeeklyApp = data.topApp,
            totalScrollToday = totalScroll,
            scrollDistanceTodayFormatted = conversionUtil.formatScrollDistanceSync(totalScroll),
            totalUnlocksToday = data.summary?.totalUnlockCount ?: 0,
            totalNotificationsToday = data.summary?.totalNotificationCount ?: 0,
            screenTimeComparison = calculateComparison(data.summary?.totalUsageTimeMillis, data.yesterdaySummary?.totalUsageTimeMillis),
            unlocksComparison = calculateComparison(data.summary?.totalUnlockCount?.toLong(), data.yesterdaySummary?.totalUnlockCount?.toLong()),
            notificationsComparison = calculateComparison(data.summary?.totalNotificationCount?.toLong(), data.yesterdaySummary?.totalNotificationCount?.toLong()),
            scrollComparison = calculateComparison(totalScroll, yesterdayTotalScroll),
            limitsCount = data.limitsCount,
            isRefreshing = data.isRefreshing,
            snackbarMessage = data.snackbarMessage,
            selectedTheme = data.selectedTheme,
            isDarkMode = data.isDarkMode,
            setLimitSheetState = data.setLimitSheetState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = TodaySummaryUiState(isRefreshing = true)
    )

    init {
        Timber.d("ViewModel initialized, triggering initial refresh.")
        performRefresh(isInitialLoad = true)
    }

    private fun performRefresh(isInitialLoad: Boolean = false, showSnackbarOnCompletion: Boolean = false) {
        if (_uiState.value is UiState.Refreshing || (isInitialLoad && _uiState.value !is UiState.InitialLoading)) {
            Timber.d("Refresh already in progress or initial load already passed. Skipping.")
            return
        }

        _uiState.value = if (isInitialLoad) UiState.InitialLoading else UiState.Refreshing
        _snackbarMessage.value = null

        viewModelScope.launch {
            try {
                scrollDataRepository.refreshDataOnAppOpen()
                if (showSnackbarOnCompletion) {
                    _snackbarMessage.value = "Data refreshed successfully"
                }
            } catch (e: Exception) {
                Timber.e(e, "A critical error occurred during data refresh.")
                _snackbarMessage.value = "Error refreshing data"
                _uiState.value = UiState.Error("An unexpected error occurred.")
            } finally {
                _uiState.value = UiState.Ready
            }
        }
    }

    fun onPullToRefresh() {
        Timber.d("Pull to refresh triggered.")
        performRefresh(showSnackbarOnCompletion = true)
    }

    fun dismissSnackbar() {
        _snackbarMessage.value = null
    }

    fun onAppResumed() {
        Timber.d("onAppResumed called.")
        checkAllPermissions()
        performRefresh()
    }

    fun performHistoricalUsageDataBackfill(numberOfDays: Int, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            if (isUsagePermissionGranted.value) {
                Timber.i("Starting historical usage data backfill for $numberOfDays days.")
                val success = scrollDataRepository.backfillHistoricalAppUsageData(numberOfDays)
                onComplete(success)
            } else {
                Timber.w("Cannot perform backfill, usage stats permission not granted.")
                onComplete(false)
            }
        }
    }

    private fun checkAllPermissions() {
        val currentState = PermissionState(
            accessibility = isAccessibilityServiceEnabled.value,
            usage = isUsagePermissionGranted.value,
            notification = isNotificationListenerEnabled.value
        )

        lastPermissionState?.let { lastState ->
            val accessibilityJustGranted = !lastState.accessibility && currentState.accessibility
            val usageJustGranted = !lastState.usage && currentState.usage
            val notificationJustGranted = !lastState.notification && currentState.notification

            if (accessibilityJustGranted || usageJustGranted || notificationJustGranted) {
                Timber.i("A permission was granted, triggering data refresh.")
                performRefresh()
            }
        }

        lastPermissionState = currentState
    }

    fun updateThemePalette(theme: AppTheme) {
        viewModelScope.launch {
            settingsRepository.setSelectedTheme(theme)
        }
    }

    fun setLimit(packageName: String, limitInMinutes: Int) {
        viewModelScope.launch {
            scrollDataRepository.setAppLimit(packageName, limitInMinutes)
        }
    }

    fun onQuickLimitIconClicked(packageName: String, appName: String) {
        viewModelScope.launch {
            val sevenDaysAgo = (0..6).map { dateUtil.getPastDateString(it) }
            val averageUsageFlow = scrollDataRepository.getAverageUsageForPackage(packageName, sevenDaysAgo)
            val existingLimitFlow = limitsRepository.getLimitedApp(packageName)

            combine(averageUsageFlow, existingLimitFlow) { averageUsage, existingLimit ->
                val existingLimitMinutes = if (existingLimit != null) {
                    limitsRepository.getGroupWithApps(existingLimit.group_id).firstOrNull()?.group?.time_limit_minutes
                } else {
                    null
                }

               _setLimitSheetState.value = SetLimitSheetState(
                   packageName = packageName,
                   appName = appName,
                   existingLimitMinutes = existingLimitMinutes,
                   averageUsageMillis = averageUsage
               )
           }.first()
       }
   }

   fun dismissSetLimitSheet() {
       _setLimitSheetState.value = null
   }

    fun onSetLimit(packageName: String, limitMinutes: Int) {
        viewModelScope.launch {
            limitsRepository.setAppLimit(packageName, limitMinutes)
            dismissSetLimitSheet()
        }
    }

    fun onDeleteLimit(packageName: String) {
        viewModelScope.launch {
            limitsRepository.removeAppLimit(packageName)
            dismissSetLimitSheet()
        }
    }

    private fun calculateComparison(current: Long?, previous: Long?): StatComparison? {
        if (current == null || previous == null || previous == 0L) return null
        val diff = current - previous
        val percentageChange = (diff.toFloat() / previous.toFloat()) * 100
        return StatComparison(percentageChange, diff > 0)
    }
}
