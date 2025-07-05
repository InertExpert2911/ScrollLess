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
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.scrolltrack.db.DailyAppUsageRecord
import com.example.scrolltrack.util.PermissionUtils.isAccessibilityServiceEnabledFlow
import com.example.scrolltrack.util.PermissionUtils.isNotificationListenerEnabledFlow
import com.example.scrolltrack.util.PermissionUtils.isUsageStatsPermissionGrantedFlow

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

    private val _selectedDate = MutableStateFlow(DateUtil.getCurrentLocalDateString())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val selectedThemePalette: StateFlow<AppTheme> = settingsRepository.selectedTheme
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppTheme.CalmLavender)

    val isDarkMode: StateFlow<Boolean> = settingsRepository.isDarkMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val isAccessibilityServiceEnabled = isAccessibilityServiceEnabledFlow(context)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)
    val isUsagePermissionGranted = isUsageStatsPermissionGrantedFlow(context)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)
    val isNotificationListenerEnabled = isNotificationListenerEnabledFlow(context)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val greeting: StateFlow<String> = flow { emit(greetingUtil.getGreeting()) }
        .stateIn(viewModelScope, SharingStarted.Lazily, "Welcome")

    private val liveSummary: Flow<DailyDeviceSummary> = _selectedDate.flatMapLatest { date ->
        repository.getLiveSummaryForDate(date)
    }

    val totalPhoneUsageTodayFormatted: StateFlow<String> =
        liveSummary.map { DateUtil.formatDuration(it.totalUsageTimeMillis) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "...")

    val totalPhoneUsageTodayMillis: StateFlow<Long> =
        liveSummary.map { it.totalUsageTimeMillis }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0L)

    val todaysAppUsageUiList: StateFlow<List<AppUsageUiItem>> =
        _selectedDate.flatMapLatest { date ->
            repository.getAppUsageForDate(date)
                .map { records -> records.map { mapper.mapToAppUsageUiItem(it) } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val aggregatedScrollDataToday: StateFlow<List<AppScrollUiItem>> =
        _selectedDate.flatMapLatest { date ->
            repository.getScrollDataForDate(date)
                .map { data -> data.map { mapper.mapToAppScrollUiItem(it) } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val totalScrollToday: StateFlow<Long> =
        aggregatedScrollDataToday.map { list -> list.sumOf { it.totalScroll } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0L)

    val scrollDistanceTodayFormatted: StateFlow<Pair<String, String>> =
        totalScrollToday.map { totalUnits ->
            conversionUtil.formatScrollDistance(totalUnits, context)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "0" to "m")

    val totalUnlocksToday: StateFlow<Int> = liveSummary.map { it.totalUnlockCount }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val totalNotificationsToday: StateFlow<Int> = liveSummary.map { it.totalNotificationCount }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

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

    fun onPullToRefresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.syncSystemEvents()
            repository.processAndSummarizeDate(DateUtil.getCurrentLocalDateString())
            _isRefreshing.value = false
        }
    }

    fun onAppResumed() {
        Log.d("TodaySummaryViewModel", "onAppResumed called, re-checking permissions.")
        checkAllPermissions()
    }

    private fun checkAllPermissions() {
        // This function body can be expanded if we need to trigger logic based on permission changes
        // For now, the flows will automatically update the UI state.
    }

    fun updateThemePalette(theme: AppTheme) {
        viewModelScope.launch {
            settingsRepository.setSelectedTheme(theme)
        }
    }

    fun performHistoricalUsageDataBackfill(days: Int, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.backfillHistoricalAppUsageData(days)
            onComplete(success)
        }
    }
} 