package com.example.scrolltrack.data

import com.example.scrolltrack.db.DailyAppUsageRecord
import com.example.scrolltrack.db.AppScrollDataPerDate
import com.example.scrolltrack.db.DailyDeviceSummary
import kotlinx.coroutines.flow.Flow
import com.example.scrolltrack.data.AppScrollData
import com.example.scrolltrack.data.NotificationCountPerApp
import com.example.scrolltrack.data.NotificationSummary
import com.example.scrolltrack.db.RawAppEvent
import com.example.scrolltrack.db.PackageCount
import com.example.scrolltrack.db.UnlockSessionRecord
import com.example.scrolltrack.db.DailyInsight

interface ScrollDataRepository {
    // --- Core Processing Triggers ---
    suspend fun syncSystemEvents(): Boolean
    suspend fun processAndSummarizeDate(dateString: String)
    suspend fun backfillHistoricalAppUsageData(numberOfDays: Int): Boolean
    suspend fun refreshDataOnAppOpen()

    // --- Live / Real-time Data Flows ---
    fun getRawEventsForDateFlow(dateString: String): Flow<List<RawAppEvent>>

    // --- Daily Summary Data Access ---
    fun getTotalScrollForDate(dateString: String): Flow<Long?>
    fun getTotalUsageTimeMillisForDate(dateString: String): Flow<Long?>
    fun getTotalUnlockCountForDate(dateString: String): Flow<Int>
    fun getTotalNotificationCountForDate(dateString: String): Flow<Int>
    fun getDeviceSummaryForDate(dateString: String): Flow<DailyDeviceSummary?>
    fun getScrollDataForDate(dateString: String): Flow<List<AppScrollData>>

    // --- App-Specific Data Access ---
    fun getAppUsageForDate(dateString: String): Flow<List<DailyAppUsageRecord>>
    suspend fun getUsageForPackageAndDates(packageName: String, dateStrings: List<String>): List<DailyAppUsageRecord>
    suspend fun getAggregatedScrollForPackageAndDates(packageName: String, dateStrings: List<String>): List<AppScrollDataPerDate>

    // --- Historical / Date-based Data Access ---
    fun getAllDistinctUsageDateStrings(): Flow<List<String>>
    fun getUsageRecordsForDateRange(startDateString: String, endDateString: String): Flow<List<DailyAppUsageRecord>>
    fun getAllDeviceSummaries(): Flow<List<DailyDeviceSummary>>
    fun getTotalUsageTimePerDay(): Flow<Map<java.time.LocalDate, Int>>
    fun getAppUsageForDateRange(startDate: java.time.LocalDate, endDate: java.time.LocalDate): Flow<List<DailyAppUsageRecord>>
    fun getTotalScrollPerDay(): Flow<Map<java.time.LocalDate, Int>>
    fun getScrollDataForDateRange(startDate: java.time.LocalDate, endDate: java.time.LocalDate): Flow<List<AppScrollData>>

    // --- Notification Data Access ---
    fun getNotificationSummaryForPeriod(startDateString: String, endDateString: String): Flow<List<NotificationSummary>>
    fun getNotificationCountPerAppForPeriod(startDateString: String, endDateString: String): Flow<List<NotificationCountPerApp>>
    fun getAllNotificationSummaries(): Flow<List<NotificationSummary>>

    // --- Insight-Specific Data Access ---
    fun getInsightsForDate(dateString: String): Flow<List<DailyInsight>>
    suspend fun getFirstAppUsedAfter(timestamp: Long): RawAppEvent?
    suspend fun getLastAppUsedOn(dateString: String): RawAppEvent?
    suspend fun getLastAppUsedBetween(startTime: Long, endTime: Long): RawAppEvent?
    fun getCompulsiveCheckCountsByPackage(startDate: String, endDate: String): Flow<List<PackageCount>>
    fun getNotificationDrivenUnlockCountsByPackage(startDate: String, endDate: String): Flow<List<PackageCount>>
    fun getUnlockSessionsForDateRange(startDate: String, endDate: String): Flow<List<UnlockSessionRecord>>
}
