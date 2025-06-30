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

class FakeSettingsRepository : SettingsRepository {
    private val _theme = MutableStateFlow("oled_dark")

    override val selectedTheme: Flow<String> = _theme

    override suspend fun setSelectedTheme(theme: String) {
        _theme.value = theme
    }
}

class FakeScrollDataRepository : ScrollDataRepository {
    override fun getAggregatedScrollDataForDate(dateString: String): Flow<List<AppScrollData>> =
        flowOf(emptyList())

    override fun getTotalScrollForDate(dateString: String): Flow<Long?> = flowOf(13106L)

    override fun getAllSessions(): Flow<List<ScrollSessionRecord>> = flowOf(emptyList())

    override fun getTotalUsageTimeMillisForDate(dateString: String): Flow<Long?> =
        flowOf((2.75 * 60 * 60 * 1000).toLong())

    override suspend fun backfillHistoricalAppUsageData(numberOfDays: Int): Boolean = true

    override fun getDailyUsageRecordsForDate(dateString: String): Flow<List<DailyAppUsageRecord>> =
        flowOf(emptyList())

    override fun getUsageRecordsForDateRange(
        startDateString: String,
        endDateString: String
    ): Flow<List<DailyAppUsageRecord>> = flowOf(emptyList())

    override suspend fun updateTodayAppUsageStats(): Boolean = true

    override suspend fun getUsageForPackageAndDates(
        packageName: String,
        dateStrings: List<String>
    ): List<DailyAppUsageRecord> = emptyList()

    override suspend fun getAggregatedScrollForPackageAndDates(
        packageName: String,
        dateStrings: List<String>
    ): List<AppScrollDataPerDate> = emptyList()

    override suspend fun insertScrollSession(session: ScrollSessionRecord) {}

    override fun getTotalUnlockCountForDate(dateString: String): Flow<Int> = flowOf(24)

    override fun getTotalNotificationCountForDate(dateString: String): Flow<Int> = flowOf(128)

    override fun getAllDeviceSummaries(): Flow<List<DailyDeviceSummary>> = flowOf(emptyList())

    override fun getAllDistinctUsageDateStrings(): Flow<List<String>> = flowOf(emptyList())

    override fun getAllDistinctScrollDateStrings(): Flow<List<String>> = flowOf(emptyList())

    override fun getNotificationSummaryForPeriod(
        startDateString: String,
        endDateString: String
    ): Flow<List<NotificationSummary>> = flowOf(emptyList())

    override fun getNotificationCountPerAppForPeriod(
        startDateString: String,
        endDateString: String
    ): Flow<List<NotificationCountPerApp>> = flowOf(emptyList())
} 