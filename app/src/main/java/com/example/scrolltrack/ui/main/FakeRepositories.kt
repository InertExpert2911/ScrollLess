package com.example.scrolltrack.ui.main

import com.example.scrolltrack.data.AppScrollData
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.data.SettingsRepository
import com.example.scrolltrack.db.AppScrollDataPerDate
import com.example.scrolltrack.db.DailyAppUsageRecord
import com.example.scrolltrack.db.DailyDeviceSummary
import com.example.scrolltrack.db.ScrollSessionRecord
import com.example.scrolltrack.ui.model.AppScrollUiItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import com.example.scrolltrack.data.NotificationCountPerApp
import com.example.scrolltrack.data.NotificationSummary
import com.example.scrolltrack.ui.theme.AppTheme
import java.time.LocalDate

class FakeSettingsRepository : SettingsRepository {
    private val _theme = MutableStateFlow(AppTheme.CalmLavender)
    override val selectedTheme: Flow<AppTheme> = _theme

    private val _isDarkMode = MutableStateFlow(true)
    override val isDarkMode: Flow<Boolean> = _isDarkMode

    private val _calibrationFactor = MutableStateFlow<Float?>(null)
    override val calibrationFactor: Flow<Float?> = _calibrationFactor

    override suspend fun setSelectedTheme(theme: AppTheme) {
        _theme.value = theme
    }

    override suspend fun setIsDarkMode(isDark: Boolean) {
        _isDarkMode.value = isDark
    }

    override suspend fun setCalibrationFactor(factor: Float?) {
        _calibrationFactor.value = factor
    }
}

class FakeScrollDataRepository : ScrollDataRepository {
    private val appUsageData = mutableListOf<DailyAppUsageRecord>()
    private val scrollData = mutableListOf<AppScrollData>()

    override fun getAggregatedScrollDataForDate(dateString: String): Flow<List<AppScrollData>> {
        return flowOf(scrollData)
    }

    override fun getTotalScrollForDate(dateString: String): Flow<Long?> {
        return flowOf(scrollData.sumOf { it.totalScroll })
    }

    override fun getAllSessions(): Flow<List<ScrollSessionRecord>> {
        return flowOf(emptyList())
    }

    override fun getDailyUsageRecordsForDate(dateString: String): Flow<List<DailyAppUsageRecord>> {
        return flowOf(appUsageData.filter { it.dateString == dateString })
    }

    override fun getUsageRecordsForDateRange(
        startDateString: String,
        endDateString: String
    ): Flow<List<DailyAppUsageRecord>> {
        return flowOf(appUsageData)
    }

    override suspend fun insertScrollSession(session: ScrollSessionRecord) {
        // Not implemented
    }

    override suspend fun updateTodayAppUsageStats(): Boolean {
        return true
    }

    override suspend fun backfillHistoricalAppUsageData(numberOfDays: Int): Boolean {
        return true
    }

    override suspend fun getUsageForPackageAndDates(
        packageName: String,
        dateStrings: List<String>
    ): List<DailyAppUsageRecord> {
        return emptyList()
    }

    override suspend fun getAggregatedScrollForPackageAndDates(
        packageName: String,
        dateStrings: List<String>
    ): List<AppScrollDataPerDate> {
        return emptyList()
    }

    override fun getTotalUsageTimeMillisForDate(dateString: String): Flow<Long?> {
        return flowOf(appUsageData.filter { it.dateString == dateString }.sumOf { it.usageTimeMillis })
    }

    override suspend fun fetchAndStoreNewUsageEvents() {
        // Not implemented
    }

    override fun getLiveSummaryForDate(dateString: String): Flow<DailyDeviceSummary> {
        return flowOf(DailyDeviceSummary(dateString = dateString))
    }

    // --- Implementing missing members ---

    override fun getTotalUnlockCountForDate(dateString: String): Flow<Int> = flowOf(0)
    override fun getTotalNotificationCountForDate(dateString: String): Flow<Int> = flowOf(0)
    override fun getAllDeviceSummaries(): Flow<List<DailyDeviceSummary>> = flowOf(emptyList())
    override fun getAllDistinctUsageDateStrings(): Flow<List<String>> = flowOf(emptyList())
    override fun getAllDistinctScrollDateStrings(): Flow<List<String>> = flowOf(emptyList())
    override fun getNotificationSummaryForPeriod(startDateString: String, endDateString: String): Flow<List<NotificationSummary>> = flowOf(emptyList())
    override fun getNotificationCountPerAppForPeriod(startDateString: String, endDateString: String): Flow<List<NotificationCountPerApp>> = flowOf(emptyList())
    override fun getDeviceSummaryForDate(dateString: String): Flow<DailyDeviceSummary?> = flowOf(null)
} 