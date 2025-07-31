package com.example.scrolltrack.data

import android.content.Context
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.content.SharedPreferences
import androidx.room.withTransaction
import com.example.scrolltrack.db.AppDatabase
import com.example.scrolltrack.db.DailyAppUsageDao
import com.example.scrolltrack.db.DailyDeviceSummaryDao
import com.example.scrolltrack.db.NotificationDao
import com.example.scrolltrack.db.RawAppEventDao
import com.example.scrolltrack.db.ScrollSessionDao
import com.example.scrolltrack.db.DailyAppUsageRecord
import com.example.scrolltrack.db.DailyDeviceSummary
import com.example.scrolltrack.db.RawAppEvent
import com.example.scrolltrack.db.ScrollSessionRecord
import com.example.scrolltrack.db.AppScrollDataPerDate
import com.example.scrolltrack.db.UnlockSessionDao
import com.example.scrolltrack.db.UnlockSessionRecord
import com.example.scrolltrack.db.NotificationRecord
import com.example.scrolltrack.db.DailyInsightDao
import com.example.scrolltrack.di.IoDispatcher
import com.example.scrolltrack.util.AppConstants
import com.example.scrolltrack.util.DateUtil
import com.example.scrolltrack.util.PermissionUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlin.math.abs
import com.example.scrolltrack.db.PackageCount
import com.example.scrolltrack.db.DailyInsight

import com.example.scrolltrack.data.processors.DailyDataProcessor

@Singleton
class ScrollDataRepositoryImpl @Inject constructor(
    private val appDatabase: AppDatabase,
    private val appMetadataRepository: AppMetadataRepository,
    private val scrollSessionDao: ScrollSessionDao,
    private val dailyAppUsageDao: DailyAppUsageDao,
    private val rawAppEventDao: RawAppEventDao,
    private val notificationDao: NotificationDao,
    private val dailyDeviceSummaryDao: DailyDeviceSummaryDao,
    private val unlockSessionDao: UnlockSessionDao,
    private val dailyInsightDao: DailyInsightDao,
    private val dailyDataProcessor: DailyDataProcessor,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ScrollDataRepository {

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    private val filterSet: Flow<Set<String>> = appMetadataRepository.getAllMetadata()
        .map { metadataList ->
            metadataList
                .filter { metadata -> metadata.userHidesOverride ?: !metadata.isUserVisible }
                .map { it.packageName }
                .toMutableSet()
                .apply {
                    add(context.packageName)
                    add("com.android.systemui")
                }
        }.distinctUntilChanged()

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("ScrollTrackRepoPrefs", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_LAST_SYSTEM_EVENT_SYNC_TIMESTAMP = "last_system_event_sync_timestamp"
        private const val KEY_LAST_HISTORICAL_REPROCESS_TIMESTAMP = "last_historical_reprocess_timestamp"
        private const val SYNC_OVERLAP_MS = 10_000L // 10 seconds
        private val HISTORICAL_REPROCESS_INTERVAL_MS = TimeUnit.HOURS.toMillis(6)
    }

    override suspend fun refreshDataOnAppOpen() = withContext(ioDispatcher) {
        Timber.d("Smart refresh triggered on app open.")

        // Step 1: Always sync latest events from the OS to ensure we have the newest raw data.
        syncSystemEvents()

        // Step 2: Always re-process today's data for immediate dashboard freshness.
        // The heavy lifting for historical data is now handled by DailyProcessingWorker.
        val today = DateUtil.getCurrentLocalDateString()
        Timber.d("Processing today's data ($today) for UI freshness.")
        processAndSummarizeDate(today)
        Timber.d("Smart refresh finished.")
    }

    override suspend fun syncSystemEvents(): Boolean = withContext(ioDispatcher) {
        if (!PermissionUtils.hasUsageStatsPermission(context)) {
            Timber.w("Cannot sync system events, Usage Stats permission not granted.")
            return@withContext false
        }

        val lastSyncTimestamp = prefs.getLong(KEY_LAST_SYSTEM_EVENT_SYNC_TIMESTAMP, 0L)
        val currentTime = System.currentTimeMillis()
        val startTime = if (lastSyncTimestamp == 0L) {
            currentTime - TimeUnit.DAYS.toMillis(1)
        } else {
            lastSyncTimestamp - SYNC_OVERLAP_MS
        }

        Timber.d("Syncing system events from $startTime to $currentTime")
        try {
            val usageEvents = usageStatsManager.queryEvents(startTime, currentTime)
            val eventsToInsert = mutableListOf<RawAppEvent>()
            val event = UsageEvents.Event()

            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                mapUsageEventToRawAppEvent(event)?.let { eventsToInsert.add(it) }
            }

            if (eventsToInsert.isNotEmpty()) {
                rawAppEventDao.insertEvents(eventsToInsert)
                Timber.i("Inserted ${eventsToInsert.size} new system events.")
            } else {
                Timber.i("No new system events to insert.")
            }

            prefs.edit().putLong(KEY_LAST_SYSTEM_EVENT_SYNC_TIMESTAMP, currentTime).apply()
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync system events.")
            false
        }
    }

    override suspend fun processAndSummarizeDate(dateString: String, initialForegroundAppOverride: String?) = withContext(ioDispatcher) {
        Timber.d("Starting processing for date: $dateString")
        val startTime = DateUtil.getStartOfDayUtcMillis(dateString)
        val endTime = DateUtil.getEndOfDayUtcMillis(dateString)
        val events = rawAppEventDao.getEventsForPeriod(startTime, endTime)
            .distinctBy { Triple(it.eventTimestamp, it.eventType, it.packageName) }
        val notifications = notificationDao.getNotificationsForDateList(dateString)
        val filterSet = this@ScrollDataRepositoryImpl.filterSet.first()

        if (events.isEmpty()) {
            Timber.i("No new events to process for $dateString. Clearing any existing data.")
            appDatabase.withTransaction {
                unlockSessionDao.deleteSessionsForDate(dateString)
                dailyAppUsageDao.deleteUsageForDate(dateString)
                scrollSessionDao.deleteSessionsForDate(dateString)
                dailyDeviceSummaryDao.deleteSummaryForDate(dateString)
                dailyInsightDao.deleteInsightsForDate(dateString)
            }
            return@withContext
        }

        // --- Step 1: Delegate all calculations to the processor ---
        val notificationsByPackage = notificationDao.getNotificationCountsPerAppForDate(dateString)
            .filter { it.packageName !in filterSet }
            .associate { it.packageName to it.count }
        val initialForegroundApp = initialForegroundAppOverride
            ?: rawAppEventDao.getLastEventBefore(startTime, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED)?.packageName
        val result = dailyDataProcessor(dateString, events, notifications, filterSet, notificationsByPackage, initialForegroundApp)

        // --- Step 2: Perform the atomic write transaction ---
        appDatabase.withTransaction {
            // 2a: Delete old data
            unlockSessionDao.deleteSessionsForDate(dateString)
            dailyAppUsageDao.deleteUsageForDate(dateString)
            scrollSessionDao.deleteSessionsForDate(dateString)
            dailyDeviceSummaryDao.deleteSummaryForDate(dateString)
            dailyInsightDao.deleteInsightsForDate(dateString)

            // 2b: Insert new, pre-calculated data
            if (result.unlockSessions.isNotEmpty()) {
                unlockSessionDao.insertSessions(result.unlockSessions)
            }
            if (result.scrollSessions.isNotEmpty()) {
                scrollSessionDao.insertSessions(result.scrollSessions)
            }
            if (result.usageRecords.isNotEmpty()) {
                dailyAppUsageDao.insertAllUsage(result.usageRecords)
            }
            result.deviceSummary?.let { dailyDeviceSummaryDao.insertOrUpdate(it) }
            if (result.insights.isNotEmpty()) {
                dailyInsightDao.insertInsights(result.insights)
            }
        }

        Timber.i("Finished processing for date: $dateString.")
    }

    override fun getRawEventsForDateFlow(dateString: String): Flow<List<RawAppEvent>> {
        val startOfDayUTC = DateUtil.getStartOfDayUtcMillis(dateString)
        val endOfDayUTC = DateUtil.getEndOfDayUtcMillis(dateString)
        return rawAppEventDao.getEventsForPeriodFlow(startOfDayUTC, endOfDayUTC)
    }

    override fun getTotalScrollForDate(dateString: String): Flow<Long?> = scrollSessionDao.getTotalScrollForDate(dateString)
    override fun getTotalUsageTimeMillisForDate(dateString: String): Flow<Long?> = dailyAppUsageDao.getTotalUsageTimeMillisForDate(dateString)
    override fun getAppUsageForDate(dateString: String): Flow<List<DailyAppUsageRecord>> {
        val usageFlow = dailyAppUsageDao.getUsageForDate(dateString)
        return combine(usageFlow, filterSet) { usage, currentFilterSet ->
            usage.filter { it.packageName !in currentFilterSet }
        }
    }
    override suspend fun getUsageForPackageAndDates(packageName: String, dateStrings: List<String>): List<DailyAppUsageRecord> = dailyAppUsageDao.getUsageForPackageAndDates(packageName, dateStrings)
    override suspend fun getAggregatedScrollForPackageAndDates(packageName: String, dateStrings: List<String>): List<AppScrollDataPerDate> = scrollSessionDao.getAggregatedScrollForPackageAndDates(packageName, dateStrings)
    override fun getAllDistinctUsageDateStrings(): Flow<List<String>> = dailyAppUsageDao.getAllDistinctUsageDateStrings()
    override fun getTotalUnlockCountForDate(dateString: String): Flow<Int> = unlockSessionDao.getUnlockCountForDateFlow(dateString)

    override fun getTotalNotificationCountForDate(dateString: String): Flow<Int> = dailyDeviceSummaryDao.getNotificationCountForDate(dateString).map { it ?: 0 }
    override fun getDeviceSummaryForDate(dateString: String): Flow<DailyDeviceSummary?> = dailyDeviceSummaryDao.getSummaryForDate(dateString)
    override fun getScrollDataForDate(dateString: String): Flow<List<AppScrollData>> {
        val scrollFlow = scrollSessionDao.getScrollDataForDate(dateString)
        return combine(scrollFlow, filterSet) { scrolls, currentFilterSet ->
            scrolls.filter { it.packageName !in currentFilterSet }
        }
    }

    override fun getUsageRecordsForDateRange(startDateString: String, endDateString: String): Flow<List<DailyAppUsageRecord>> {
        val usageFlow = dailyAppUsageDao.getUsageRecordsForDateRange(startDateString, endDateString)
        return combine(usageFlow, filterSet) { usage, currentFilterSet ->
            usage.filter { it.packageName !in currentFilterSet }
        }
    }

    override fun getAllDeviceSummaries(): Flow<List<DailyDeviceSummary>> = dailyDeviceSummaryDao.getAllSummaries()

    override fun getTotalUsageTimePerDay(): Flow<Map<java.time.LocalDate, Int>> {
        return dailyAppUsageDao.getAllUsageRecords().map { records ->
            records.groupBy { DateUtil.parseLocalDate(it.dateString)!! }
                .mapValues { (_, value) ->
                    value.sumOf { it.usageTimeMillis }.toInt()
                }
        }
    }

    override fun getAppUsageForDateRange(startDate: java.time.LocalDate, endDate: java.time.LocalDate): Flow<List<DailyAppUsageRecord>> {
        val startDateString = DateUtil.formatDateToYyyyMmDdString(startDate)
        val endDateString = DateUtil.formatDateToYyyyMmDdString(endDate)
        return getUsageRecordsForDateRange(startDateString, endDateString)
    }

    override fun getTotalScrollPerDay(): Flow<Map<java.time.LocalDate, Int>> {
        return scrollSessionDao.getAllScrollSessions().map { sessions ->
            sessions.groupBy { DateUtil.parseLocalDate(it.dateString)!! }
                .mapValues { (_, value) ->
                    value.sumOf { it.scrollAmount }.toInt()
                }
        }
    }

    override fun getScrollDataForDateRange(startDate: java.time.LocalDate, endDate: java.time.LocalDate): Flow<List<AppScrollData>> {
        val startDateString = DateUtil.formatDateToYyyyMmDdString(startDate)
        val endDateString = DateUtil.formatDateToYyyyMmDdString(endDate)
        val scrollFlow = scrollSessionDao.getScrollDataForDateRange(startDateString, endDateString)
        return combine(scrollFlow, filterSet) { scrolls, currentFilterSet ->
            scrolls.filter { it.packageName !in currentFilterSet }
        }
    }

    override fun getNotificationSummaryForPeriod(startDateString: String, endDateString: String): Flow<List<NotificationSummary>> = notificationDao.getNotificationSummaryForPeriod(startDateString, endDateString)

    override fun getNotificationCountPerAppForPeriod(startDateString: String, endDateString: String): Flow<List<NotificationCountPerApp>> {
        val notificationFlow = notificationDao.getNotificationCountPerAppForPeriod(startDateString, endDateString)
        return combine(notificationFlow, filterSet) { notifications, currentFilterSet ->
            notifications.filter { it.packageName !in currentFilterSet }
        }
    }

    override fun getAllNotificationSummaries(): Flow<List<NotificationSummary>> = notificationDao.getAllNotificationSummaries()

    override fun getInsightsForDate(dateString: String): Flow<List<DailyInsight>> {
        return dailyInsightDao.getInsightsForDateAsFlow(dateString)
    }

    // --- Insight-Specific Implementations ---
    override suspend fun getFirstAppUsedAfter(timestamp: Long): RawAppEvent? {
        val currentFilterSet = this.filterSet.first()
        var nextEvent = rawAppEventDao.getFirstEventAfter(timestamp, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED)
        while (nextEvent != null && nextEvent.packageName in currentFilterSet) {
            nextEvent = rawAppEventDao.getFirstEventAfter(nextEvent.eventTimestamp, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED)
        }
        return nextEvent
    }

    override suspend fun getLastAppUsedOn(dateString: String): RawAppEvent? {
        val currentFilterSet = this.filterSet.first()
        val startTime = DateUtil.getStartOfDayUtcMillis(dateString)
        val endTime = DateUtil.getEndOfDayUtcMillis(dateString)
        // Fetch all resume events for the day and find the last one that isn't filtered out.
        return rawAppEventDao.getEventsForPeriod(startTime, endTime)
            .filter { it.eventType == RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED && it.packageName !in currentFilterSet }
            .lastOrNull()
    }

    override suspend fun getLastAppUsedBetween(startTime: Long, endTime: Long): RawAppEvent? {
        val currentFilterSet = this.filterSet.first()
        return rawAppEventDao.getEventsForPeriod(startTime, endTime)
            .filter { it.eventType == RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED && it.packageName !in currentFilterSet }
            .lastOrNull()
    }


    override fun getCompulsiveCheckCountsByPackage(startDate: String, endDate: String): Flow<List<PackageCount>> {
        val compulsiveCountsFlow = unlockSessionDao.getCompulsiveCheckCountsByPackage(startDate, endDate)
        return combine(compulsiveCountsFlow, filterSet) { compulsiveCounts, currentFilterSet ->
            compulsiveCounts.filter { it.packageName !in currentFilterSet }
        }
    }

    override fun getNotificationDrivenUnlockCountsByPackage(startDate: String, endDate: String): Flow<List<PackageCount>> {
        val notificationUnlocksFlow = unlockSessionDao.getNotificationDrivenUnlockCountsByPackage(startDate, endDate)
        return combine(notificationUnlocksFlow, filterSet) { unlockCounts, currentFilterSet ->
            unlockCounts.filter { it.packageName !in currentFilterSet }
        }
    }

    override fun getUnlockSessionsForDateRange(startDate: String, endDate: String): Flow<List<UnlockSessionRecord>> {
        val unlockSessionsFlow = unlockSessionDao.getUnlockSessionsForDateRange(startDate, endDate)
        return combine(unlockSessionsFlow, filterSet) { sessions, currentFilterSet ->
            sessions.filter { it.firstAppPackageName == null || it.firstAppPackageName !in currentFilterSet }
        }
    }



    internal fun mapUsageEventToRawAppEvent(event: UsageEvents.Event): RawAppEvent? {
        val internalEventType = when (event.eventType) {
            UsageEvents.Event.ACTIVITY_RESUMED -> RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED
            UsageEvents.Event.ACTIVITY_PAUSED -> RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED
            UsageEvents.Event.ACTIVITY_STOPPED -> RawAppEvent.EVENT_TYPE_ACTIVITY_STOPPED
            UsageEvents.Event.USER_INTERACTION -> RawAppEvent.EVENT_TYPE_USER_INTERACTION
            UsageEvents.Event.SCREEN_INTERACTIVE -> RawAppEvent.EVENT_TYPE_SCREEN_INTERACTIVE
            UsageEvents.Event.SCREEN_NON_INTERACTIVE -> RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE
            UsageEvents.Event.KEYGUARD_SHOWN -> RawAppEvent.EVENT_TYPE_KEYGUARD_SHOWN
            UsageEvents.Event.KEYGUARD_HIDDEN -> RawAppEvent.EVENT_TYPE_KEYGUARD_HIDDEN
            12 -> RawAppEvent.EVENT_TYPE_USER_PRESENT // For USER_PRESENT on API < 33
            26 -> RawAppEvent.EVENT_TYPE_USER_UNLOCKED // For USER_UNLOCKED on API 30+
            else -> -1
        }
        if (internalEventType == -1) return null

        return RawAppEvent(
            packageName = event.packageName,
            className = event.className,
            eventType = internalEventType,
            eventTimestamp = event.timeStamp,
            eventDateString = DateUtil.formatUtcTimestampToLocalDateString(event.timeStamp),
            source = RawAppEvent.SOURCE_USAGE_STATS
        )
    }



    override suspend fun backfillHistoricalAppUsageData(numberOfDays: Int): Boolean = withContext(ioDispatcher) {
        if (!PermissionUtils.hasUsageStatsPermission(context)) {
            Timber.w("Cannot perform backfill, Usage Stats permission not granted.")
            return@withContext false
        }
        // This function now processes the last 7 days for a quick, accurate initial setup.
        // The `numberOfDays` parameter is ignored.
        Timber.i("Starting one-time backfill for the last 7 days.")

        val datesToProcess = (0..6).map { DateUtil.getPastDateString(it) }

        val allEventsToInsert = mutableSetOf<RawAppEvent>()

        for (date in datesToProcess) {
            try {
                val startTime = DateUtil.getStartOfDayUtcMillis(date)
                val endTime = DateUtil.getEndOfDayUtcMillis(date)

                val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
                val event = UsageEvents.Event()

                while (usageEvents.hasNextEvent()) {
                    usageEvents.getNextEvent(event)
                    mapUsageEventToRawAppEvent(event)?.let { allEventsToInsert.add(it) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error fetching historical events for date: $date")
                // Continue to next date if one fails
            }
        }


        if (allEventsToInsert.isEmpty()) {
            Timber.i("No historical events found for the last 7 days. Backfill complete.")
            return@withContext true
        }

        // Insert all unique events fetched
        rawAppEventDao.insertEvents(allEventsToInsert.toList())
        Timber.d("Inserted ${allEventsToInsert.size} unique historical events for the last 7 days.")

        // Process each day
        for (date in datesToProcess) {
            try {
                val eventsForDate = allEventsToInsert.filter { it.eventDateString == date }
                if (eventsForDate.isNotEmpty()) {
                    processBackfillForDate(date, eventsForDate)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error processing backfilled data for date: $date")
            }
        }

        Timber.i("One-time backfill for the last 7 days completed successfully.")
        true
    }

    private suspend fun processBackfillForDate(dateString: String, events: List<RawAppEvent>) {
        val currentFilterSet = this.filterSet.first()
        val notifications = notificationDao.getNotificationsForDateList(dateString)
        val notificationsByPackage = notificationDao.getNotificationCountsPerAppForDate(dateString)
            .filter { it.packageName !in currentFilterSet }
            .associate { it.packageName to it.count }

        // --- 1. Delegate all calculations to the processor ---
        val result = dailyDataProcessor(dateString, events, notifications, currentFilterSet, notificationsByPackage, null)

        // --- 2. Atomic write to database ---
        appDatabase.withTransaction {
            unlockSessionDao.deleteSessionsForDate(dateString)
            dailyAppUsageDao.deleteUsageForDate(dateString)
            dailyDeviceSummaryDao.deleteSummaryForDate(dateString)
            scrollSessionDao.deleteSessionsForDate(dateString)
            dailyInsightDao.deleteInsightsForDate(dateString)

            if (result.unlockSessions.isNotEmpty()) {
                unlockSessionDao.insertSessions(result.unlockSessions)
            }
            if (result.scrollSessions.isNotEmpty()) {
                scrollSessionDao.insertSessions(result.scrollSessions)
            }
            if (result.usageRecords.isNotEmpty()) {
                dailyAppUsageDao.insertAllUsage(result.usageRecords)
            }
            result.deviceSummary?.let { dailyDeviceSummaryDao.insertOrUpdate(it) }
            if (result.insights.isNotEmpty()) {
                dailyInsightDao.insertInsights(result.insights)
            }
        }
    }
    override suspend fun getCurrentForegroundApp(): String? = withContext(ioDispatcher) {
        if (!PermissionUtils.hasUsageStatsPermission(context)) {
            Timber.w("Cannot get current foreground app, permission not granted.")
            return@withContext null
        }
        val endTime = System.currentTimeMillis()
        val startTime = endTime - TimeUnit.MINUTES.toMillis(1) // Look in the last minute

        val usageStatsList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)

        return@withContext usageStatsList?.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    override fun setAppLimit(packageName: String, limitInMinutes: Int) {
        // This is a placeholder implementation. A real implementation would save the limit to a database.
        Timber.d("Setting limit for $packageName to $limitInMinutes minutes.")
    }
}
