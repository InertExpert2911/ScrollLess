package com.example.scrolltrack.ui.main

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrolltrack.data.*
import com.example.scrolltrack.db.DailyDeviceSummary
import com.example.scrolltrack.ui.mappers.AppUiModelMapper
import com.example.scrolltrack.ui.model.AppScrollUiItem
import com.example.scrolltrack.ui.model.AppUsageUiItem
import com.example.scrolltrack.ui.theme.AppTheme
import com.example.scrolltrack.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.delay
import com.example.scrolltrack.db.DailyAppUsageRecord
import com.example.scrolltrack.util.PermissionUtils.isAccessibilityServiceEnabledFlow
import com.example.scrolltrack.util.PermissionUtils.isNotificationListenerEnabledFlow
import com.example.scrolltrack.util.PermissionUtils.isUsageStatsPermissionGrantedFlow
import timber.log.Timber

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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodaySummaryViewModel @Inject constructor(
    private val repository: ScrollDataRepository,
    private val settingsRepository: SettingsRepository,
    private val mapper: AppUiModelMapper,
    private val conversionUtil: ConversionUtil,
    private val greetingUtil: GreetingUtil,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private var lastPermissionState: PermissionState? = null

    private val _selectedDate = MutableStateFlow(DateUtil.getCurrentLocalDateString())

    private val _uiState = MutableStateFlow<UiState>(UiState.InitialLoading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val isRefreshing: StateFlow<Boolean> = _uiState.map { it is UiState.Refreshing }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

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
        repository.getDeviceSummaryForDate(date)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val yesterdaySummaryData: StateFlow<DailyDeviceSummary?> = _selectedDate.flatMapLatest { date ->
        val yesterday = DateUtil.getPastDateString(1, DateUtil.parseLocalDate(date))
        repository.getDeviceSummaryForDate(yesterday)
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

    val yesterdayAggregatedScrollData: StateFlow<List<AppScrollUiItem>> =
        _selectedDate.flatMapLatest { date ->
            val yesterday = DateUtil.getPastDateString(1, DateUtil.parseLocalDate(date))
            repository.getScrollDataForDate(yesterday)
                .map { data -> data.map { mapper.mapToAppScrollUiItem(it) } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val totalPhoneUsageTodayFormatted: StateFlow<String> =
        summaryData.map { DateUtil.formatDuration(it?.totalUsageTimeMillis ?: 0L) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "...")

    val totalPhoneUsageTodayMillis: StateFlow<Long> =
        summaryData.map { it?.totalUsageTimeMillis ?: 0L }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0L)

    val todaysAppUsageUiList: StateFlow<List<AppUsageUiItem>> =
        _selectedDate.flatMapLatest { date ->
            repository.getAppUsageForDate(date)
                .map { records -> mapper.mapToAppUsageUiItems(records) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val aggregatedScrollDataToday: StateFlow<List<AppScrollUiItem>> =
        _selectedDate.flatMapLatest { date ->
            repository.getScrollDataForDate(date)
                .map { data -> data.map { mapper.mapToAppScrollUiItem(it) } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val totalScrollDistanceToday: StateFlow<Long> =
        aggregatedScrollDataToday.map { list ->
            list.sumOf { it.totalScroll }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0L)

    val totalScrollDistanceYesterday: StateFlow<Long> =
        yesterdayAggregatedScrollData.map { list ->
            list.sumOf { it.totalScroll }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0L)

    val scrollComparison: StateFlow<StatComparison?> = combine(totalScrollDistanceToday, totalScrollDistanceYesterday) { today, yesterday ->
        calculateComparison(today, yesterday)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val totalScrollToday: StateFlow<Long> =
        aggregatedScrollDataToday.map { list -> list.sumOf { it.totalScroll } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0L)

    val scrollDistanceTodayFormatted: StateFlow<Pair<String, String>> =
        aggregatedScrollDataToday.map { list ->
            val totalScrollX = list.sumOf { it.totalScrollX }
            val totalScrollY = list.sumOf { it.totalScrollY }
            conversionUtil.formatScrollDistance(totalScrollX, totalScrollY)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "0" to "m")

    private val unlockSessionsToday: StateFlow<List<com.example.scrolltrack.db.UnlockSessionRecord>> = _selectedDate.flatMapLatest { date ->
        repository.getUnlockSessionsForDateRange(date, date)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val totalUnlocksToday: StateFlow<Int> = unlockSessionsToday.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val intentionalUnlocksToday: StateFlow<Int> = unlockSessionsToday.map { sessions ->
        sessions.count { it.sessionType == "Intentional" || it.sessionEndReason == "INTERRUPTED" || it.sessionType == null }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val glanceUnlocksToday: StateFlow<Int> = unlockSessionsToday.map { sessions ->
        sessions.count { it.sessionType == "Glance" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val totalNotificationsToday: StateFlow<Int> = summaryData.map { it?.totalNotificationCount ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val firstUnlockTime: StateFlow<String> = summaryData.map {
        it?.firstUnlockTimestampUtc?.let { ts -> DateUtil.formatUtcTimestampToTimeString(ts) } ?: "N/A"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "N/A")

    val lastUnlockTime: StateFlow<String> = summaryData.map {
        it?.lastUnlockTimestampUtc?.let { ts -> DateUtil.formatUtcTimestampToTimeString(ts) } ?: "N/A"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "N/A")

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
                val recordsForPackage = weeklyUsageRecords.filter { it.packageName == packageName }
                // Create a temporary record with the aggregated weekly data to pass to the mapper
                DailyAppUsageRecord(
                    packageName = packageName,
                    dateString = today,
                    usageTimeMillis = totalUsage,
                    activeTimeMillis = recordsForPackage.sumOf { it.activeTimeMillis },
                    appOpenCount = recordsForPackage.sumOf { it.appOpenCount },
                    notificationCount = recordsForPackage.sumOf { it.notificationCount }
                )
            }

        if (topAppRecord != null) {
            emit(mapper.mapToAppUsageUiItem(topAppRecord))
        } else {
            emit(null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    init {
        // Trigger the first refresh immediately on creation. This is non-blocking.
        Timber.d("ViewModel initialized, triggering initial refresh.")
        performRefresh(isInitialLoad = true)
    }

    private fun performRefresh(isInitialLoad: Boolean = false, showSnackbarOnCompletion: Boolean = false) {
        if (uiState.value is UiState.Refreshing || (isInitialLoad && uiState.value !is UiState.InitialLoading)) {
            Timber.d("Refresh already in progress or initial load already passed. Skipping.")
            return
        }

        _uiState.value = if (isInitialLoad) UiState.InitialLoading else UiState.Refreshing
        _snackbarMessage.value = null // Clear previous snackbar messages

        viewModelScope.launch {
            try {
                repository.refreshDataOnAppOpen()
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
                val success = repository.backfillHistoricalAppUsageData(numberOfDays)
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
                Log.i("TodaySummaryViewModel", "A permission was granted, triggering data refresh.")
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

    private fun calculateComparison(current: Long?, previous: Long?): StatComparison? {
        if (current == null || previous == null || previous == 0L) return null
        val diff = current - previous
        val percentageChange = (diff.toFloat() / previous.toFloat()) * 100
        return StatComparison(percentageChange, diff > 0)
    }
}
