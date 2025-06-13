package com.example.scrolltrack.ui.main

import com.example.scrolltrack.data.AppScrollData
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.data.SettingsRepository
import com.example.scrolltrack.db.AppScrollDataPerDate
import com.example.scrolltrack.db.DailyAppUsageRecord
import com.example.scrolltrack.db.ScrollSessionRecord
import com.example.scrolltrack.ui.model.AppScrollUiItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeSettingsRepository : SettingsRepository {
    private var theme = "oled_dark"
    private var backfillDone = true

    override fun isHistoricalBackfillDone(): Boolean = backfillDone
    override fun setHistoricalBackfillDone(value: Boolean) {
        backfillDone = value
    }

    override fun getSelectedTheme(): String = theme
    override fun setSelectedTheme(theme: String) {
        this.theme = theme
    }
}

class FakeScrollDataRepository : ScrollDataRepository {
    override fun getAggregatedScrollDataForDate(dateString: String): Flow<List<AppScrollData>> =
        flowOf(emptyList())

    override fun getTotalScrollForDate(dateString: String): Flow<Long?> = flowOf(13106L)

    override fun getAllSessions(): Flow<List<ScrollSessionRecord>> = flowOf(emptyList())

    override suspend fun getTotalUsageTimeMillisForDate(dateString: String): Long? =
        (2.75 * 60 * 60 * 1000).toLong()

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

    override suspend fun getAggregatedScrollForDateUi(dateString: String): List<AppScrollUiItem> =
        listOf(
            AppScrollUiItem("settings", "Settings", null, 7294, "com.android.settings")
        )

    override suspend fun insertScrollSession(session: ScrollSessionRecord) {}
} 