package com.example.scrolltrack.data

import android.app.Application
import com.example.scrolltrack.db.AppScrollDataPerDate
import com.example.scrolltrack.db.DailyAppUsageDao
import com.example.scrolltrack.db.DailyAppUsageRecord
import com.example.scrolltrack.db.ScrollSessionDao
import com.example.scrolltrack.db.ScrollSessionRecord
import com.example.scrolltrack.ui.model.AppScrollUiItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class DefaultScrollDataRepository(
    private val scrollSessionDao: ScrollSessionDao,
    private val dailyAppUsageDao: DailyAppUsageDao,
    private val usageStatsProcessor: UsageStatsProcessor,
    private val application: Application
) : ScrollDataRepository {
    override fun getAggregatedScrollDataForDate(dateString: String): Flow<List<AppScrollData>> {
        return scrollSessionDao.getAggregatedScrollDataForDate(dateString)
    }

    override fun getTotalScrollForDate(dateString: String): Flow<Long?> {
        return scrollSessionDao.getTotalScrollForDate(dateString)
    }

    override fun getAllSessions(): Flow<List<ScrollSessionRecord>> {
        return scrollSessionDao.getAllSessionsFlow()
    }

    override fun getDailyUsageRecordsForDate(dateString: String): Flow<List<DailyAppUsageRecord>> {
        return dailyAppUsageDao.getUsageForDate(dateString)
    }

    override fun getUsageRecordsForDateRange(
        startDateString: String,
        endDateString: String
    ): Flow<List<DailyAppUsageRecord>> {
        return dailyAppUsageDao.getUsageRecordsForDateRange(startDateString, endDateString)
    }

    override suspend fun updateTodayAppUsageStats(): Boolean = withContext(Dispatchers.IO) {
        val aggregatedUsage = usageStatsProcessor.fetchAndProcessUsageStatsForToday()
        val records = aggregatedUsage.map { (key, duration) ->
            DailyAppUsageRecord(
                packageName = key.first,
                dateString = key.second,
                usageTimeMillis = duration,
                activeTimeMillis = 0,
                lastUpdatedTimestamp = System.currentTimeMillis()
            )
        }
        dailyAppUsageDao.insertAllUsage(records)
        true
    }

    override suspend fun getTotalUsageTimeMillisForDate(dateString: String): Long? {
        return dailyAppUsageDao.getTotalUsageTimeMillisForDate(dateString).first()
    }

    override suspend fun backfillHistoricalAppUsageData(numberOfDays: Int): Boolean {
        // This will be implemented later
        return true
    }

    override suspend fun getUsageForPackageAndDates(
        packageName: String,
        dateStrings: List<String>
    ): List<DailyAppUsageRecord> {
        return dailyAppUsageDao.getUsageForPackageAndDates(packageName, dateStrings)
    }

    override suspend fun getAggregatedScrollForPackageAndDates(
        packageName: String,
        dateStrings: List<String>
    ): List<AppScrollDataPerDate> {
        return scrollSessionDao.getAggregatedScrollForPackageAndDates(packageName, dateStrings)
    }

    override suspend fun getAggregatedScrollForDateUi(dateString: String): List<AppScrollUiItem> {
        // This will be implemented later
        return emptyList()
    }

    override suspend fun insertScrollSession(session: ScrollSessionRecord) {
        scrollSessionDao.insertSession(session)
    }
} 