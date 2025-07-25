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

    override suspend fun processAndSummarizeDate(dateString: String) = withContext(ioDispatcher) {
        Timber.d("Starting processing for date: $dateString")
        val startTime = DateUtil.getStartOfDayUtcMillis(dateString)
        val endTime = DateUtil.getEndOfDayUtcMillis(dateString)
        val events = rawAppEventDao.getEventsForPeriod(startTime, endTime)
            .distinctBy { Triple(it.eventTimestamp, it.eventType, it.packageName) }
        val notifications = notificationDao.getNotificationsForDateList(dateString)

        appDatabase.withTransaction {
            unlockSessionDao.deleteSessionsForDate(dateString)
            dailyAppUsageDao.deleteUsageForDate(dateString)
            scrollSessionDao.deleteSessionsForDate(dateString)
            dailyDeviceSummaryDao.deleteSummaryForDate(dateString)
            dailyInsightDao.deleteInsightsForDate(dateString)

            if (events.isNotEmpty()) {
                val filterSet = this@ScrollDataRepositoryImpl.filterSet.first()

                // Process the unlock timeline FIRST. This is critical.
                val unlockRelatedEvents = events.filter {
                    it.eventType in setOf(
                        RawAppEvent.EVENT_TYPE_USER_UNLOCKED,
                        RawAppEvent.EVENT_TYPE_KEYGUARD_HIDDEN,
                        RawAppEvent.EVENT_TYPE_KEYGUARD_SHOWN,
                        RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE,
                        RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED
                    )
                }
                processUnlockEvents(
                    unlockRelatedEvents,
                    notifications,
                    filterSet, // Pass the filter set
                    unlockEventTypes = setOf(RawAppEvent.EVENT_TYPE_USER_UNLOCKED, RawAppEvent.EVENT_TYPE_KEYGUARD_HIDDEN),
                    lockEventTypes = setOf(RawAppEvent.EVENT_TYPE_KEYGUARD_SHOWN, RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE)
                )

                val scrollSessions = processScrollEvents(events, filterSet)
                if (scrollSessions.isNotEmpty()) {
                    scrollSessionDao.insertSessions(scrollSessions)
                }

                val (usageRecords, deviceSummary) = processUsageAndDeviceSummary(events, filterSet, dateString)
                if (usageRecords.isNotEmpty()) {
                    dailyAppUsageDao.insertAllUsage(usageRecords)
                }
                deviceSummary?.let { dailyDeviceSummaryDao.insertOrUpdate(it) }

                val unlockSessionsForInsights = unlockSessionDao.getUnlockSessionsForDate(dateString)
                val insights = generateInsights(dateString, unlockSessionsForInsights, events, filterSet)
                if (insights.isNotEmpty()) {
                    dailyInsightDao.insertInsights(insights)
                }
            } else {
                Timber.i("No new events to process for $dateString. Existing data has been cleared.")
            }
        }
        Timber.i("Finished processing for date: $dateString.")
    }

    internal fun processScrollEvents(events: List<RawAppEvent>, filterSet: Set<String>): List<ScrollSessionRecord> {
        val allScrollEvents = events
            .filter {
                (it.eventType == RawAppEvent.EVENT_TYPE_SCROLL_MEASURED || it.eventType == RawAppEvent.EVENT_TYPE_SCROLL_INFERRED)
                        && (it.scrollDeltaX != null || it.scrollDeltaY != null || it.value != null) // Include legacy 'value' for migration
                        && it.packageName !in filterSet
            }

        // Identify all packages that have produced high-quality, measured scroll data in this batch.
        val packagesWithMeasuredScroll = allScrollEvents
            .filter { it.eventType == RawAppEvent.EVENT_TYPE_SCROLL_MEASURED }
            .map { it.packageName }
            .toSet()

        // Filter the events list. If a package has any measured events, we completely discard its inferred events for this batch.
        // This ensures an app is only represented by one data type per processing cycle, prioritizing the higher quality one.
        val scrollEvents = allScrollEvents
            .filter { event ->
                if (event.packageName in packagesWithMeasuredScroll) {
                    // This app has measured data, so only keep its measured events.
                    event.eventType == RawAppEvent.EVENT_TYPE_SCROLL_MEASURED
                } else {
                    // This app does not have measured data, so keep its events (which will be inferred).
                    true
                }
            }
            .sortedBy { it.eventTimestamp }

        if (scrollEvents.isEmpty()) return emptyList()

        val mergedSessions = mutableListOf<ScrollSessionRecord>()
        var currentSession: ScrollSessionRecord? = null

        for (event in scrollEvents) {
            val eventDataType = if (event.eventType == RawAppEvent.EVENT_TYPE_SCROLL_MEASURED) "MEASURED" else "INFERRED"

            // Prioritize new delta columns, fall back to 'value' for old events
            val deltaX = event.scrollDeltaX ?: 0
            val deltaY = event.scrollDeltaY ?: if (event.eventType == RawAppEvent.EVENT_TYPE_SCROLL_INFERRED) event.value?.toInt() ?: 0 else 0
            val totalDelta = (abs(deltaX) + abs(deltaY)).toLong()

            if (totalDelta == 0L) continue // Skip events with no effective scroll

            if (currentSession == null) {
                currentSession = ScrollSessionRecord(
                    packageName = event.packageName,
                    sessionStartTime = event.eventTimestamp,
                    sessionEndTime = event.eventTimestamp,
                    scrollAmount = totalDelta,
                    scrollAmountX = abs(deltaX).toLong(),
                    scrollAmountY = abs(deltaY).toLong(),
                    dateString = event.eventDateString,
                    sessionEndReason = "PROCESSED",
                    dataType = eventDataType
                )
            } else {
                val timeDiff = event.eventTimestamp - currentSession.sessionEndTime
                if (event.packageName == currentSession.packageName &&
                    currentSession.dataType == eventDataType &&
                    timeDiff <= AppConstants.SESSION_MERGE_GAP_MS) {
                    currentSession = currentSession.copy(
                        sessionEndTime = event.eventTimestamp,
                        scrollAmount = currentSession.scrollAmount + totalDelta,
                        scrollAmountX = currentSession.scrollAmountX + abs(deltaX),
                        scrollAmountY = currentSession.scrollAmountY + abs(deltaY)
                    )
                } else {
                    mergedSessions.add(currentSession)
                    currentSession = ScrollSessionRecord(
                        packageName = event.packageName,
                        sessionStartTime = event.eventTimestamp,
                        sessionEndTime = event.eventTimestamp,
                        scrollAmount = totalDelta,
                        scrollAmountX = abs(deltaX).toLong(),
                        scrollAmountY = abs(deltaY).toLong(),
                        dateString = event.eventDateString,
                        sessionEndReason = "PROCESSED",
                        dataType = eventDataType
                    )
                }
            }
        }
        currentSession?.let { mergedSessions.add(it) }
        return mergedSessions
    }

    internal suspend fun processUnlockEvents(
        events: List<RawAppEvent>,
        notifications: List<NotificationRecord>,
        hiddenAppsSet: Set<String>,
        unlockEventTypes: Set<Int>,
        lockEventTypes: Set<Int>
    ) {
        if (events.isEmpty()) return

        val sortedEvents = events.sortedBy { it.eventTimestamp }
        var openSession = unlockSessionDao.getLatestOpenSession()

        for (event in sortedEvents) {
            val isUnlock = event.eventType in unlockEventTypes
            val isLock = event.eventType in lockEventTypes

            if (isUnlock) {
                if (openSession != null) {
                    Timber.w("Found a ghost session (ID: ${openSession.id}). Closing it before starting a new one.")
                    unlockSessionDao.closeSession(
                        sessionId = openSession.id,
                        lockTimestamp = event.eventTimestamp,
                        duration = event.eventTimestamp - openSession.unlockTimestamp,
                        firstAppPackage = null,
                        sessionType = "Glance",
                        sessionEndReason = "GHOST"
                    )
                }

                val newSession = UnlockSessionRecord(
                    unlockTimestamp = event.eventTimestamp,
                    dateString = event.eventDateString,
                    unlockEventType = event.eventType.toString()
                )
                val id = unlockSessionDao.insert(newSession)
                openSession = newSession.copy(id = id)

            } else if (isLock && openSession != null) {
                val currentOpenSession = openSession
                val duration = event.eventTimestamp - currentOpenSession.unlockTimestamp
                if (duration >= 0) {
                    val sessionType = if (duration < AppConstants.MINIMUM_GLANCE_DURATION_MS) "Glance" else "Intentional"

                    // --- NEW LOGIC STARTS HERE ---
                    var isCompulsiveCheck = false
                    val firstAppEvent = events.find { e ->
                        e.eventTimestamp > currentOpenSession.unlockTimestamp &&
                                e.eventTimestamp < event.eventTimestamp &&
                                e.eventType == RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED &&
                                e.packageName !in hiddenAppsSet
                    }

                    if (firstAppEvent != null) {
                        // Check if any OTHER app was resumed during this session.
                        val otherAppOpened = events.any { e ->
                            e.eventTimestamp > firstAppEvent.eventTimestamp &&
                                    e.eventTimestamp < event.eventTimestamp &&
                                    e.eventType == RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED
                        }
                        // A compulsive check is a short session where only ONE app was opened.
                        if (!otherAppOpened && duration < AppConstants.COMPULSIVE_UNLOCK_THRESHOLD_MS) {
                            isCompulsiveCheck = true
                        }
                    }
                    // --- NEW LOGIC ENDS HERE ---

                    // --- NEW NOTIFICATION LOGIC STARTS HERE ---
                    var notificationPackageName: String? = null

                    // Only proceed if an app was actually opened during the session.
                    if (firstAppEvent != null) {
                        // 1. Find the most recent notification that occurred *before* the unlock.
                        val recentNotification = notifications.lastOrNull { n ->
                            n.postTimeUTC < currentOpenSession.unlockTimestamp &&
                                    (currentOpenSession.unlockTimestamp - n.postTimeUTC) < AppConstants.NOTIFICATION_UNLOCK_WINDOW_MS
                        }

                        // 2. Check if the app that sent the notification is the SAME as the app that was opened.
                        if (recentNotification != null && recentNotification.packageName == firstAppEvent.packageName) {
                            // 3. We have a match! This unlock was driven by the notification.
                            notificationPackageName = recentNotification.packageName
                        }
                    }
                    // --- NEW NOTIFICATION LOGIC ENDS HERE ---

                    unlockSessionDao.closeSession(
                        sessionId = currentOpenSession.id,
                        lockTimestamp = event.eventTimestamp,
                        duration = duration,
                        firstAppPackage = firstAppEvent?.packageName,
                        sessionType = sessionType,
                        sessionEndReason = "LOCKED",
                        isCompulsive = isCompulsiveCheck, // Pass our new flag
                        triggeringNotificationPackageName = notificationPackageName
                    )
                }
                openSession = null
            }
        }
    }


    internal suspend fun processUsageAndDeviceSummary(events: List<RawAppEvent>, filterSet: Set<String>, dateString: String): Pair<List<DailyAppUsageRecord>, DailyDeviceSummary?> {
        val endTime = DateUtil.getEndOfDayUtcMillis(dateString)
        val filteredEvents = events.filter { it.packageName !in filterSet }

        val (usageAggregates, inferredEvents) = aggregateUsage(filteredEvents, endTime)
        val allEventsForAppOpens = filteredEvents + inferredEvents
        val appOpens = calculateAppOpens(allEventsForAppOpens)

        // Close any open unlock session at the end of the day
        unlockSessionDao.getLatestOpenSession()?.let { openSession ->
            if (openSession.dateString == dateString) {
                val duration = endTime - openSession.unlockTimestamp
                val sessionType = if (duration < AppConstants.MINIMUM_GLANCE_DURATION_MS) "Glance" else "Intentional"
                unlockSessionDao.closeSession(
                    sessionId = openSession.id,
                    lockTimestamp = endTime,
                    duration = duration,
                    firstAppPackage = null,
                    sessionType = sessionType,
                    sessionEndReason = "END_OF_DAY"
                )
            }
        }

        val notificationsByPackage = notificationDao.getNotificationCountsPerAppForDate(dateString)
            .filter { it.packageName !in filterSet }
            .associate { it.packageName to it.count }
        val totalNotifications = notificationsByPackage.values.sum()

        val unlockSessions = unlockSessionDao.getUnlockSessionsForDate(dateString)
        val totalUnlocks = unlockSessions.size
        val intentionalUnlocks = unlockSessions.count { it.sessionType == "Intentional" }
        val glanceUnlocks = unlockSessions.count { it.sessionType == "Glance" }
        val firstUnlockTime = unlockSessions.minOfOrNull { it.unlockTimestamp }
        val lastUnlockTime = unlockSessions.maxOfOrNull { it.unlockTimestamp }

        val filteredAppOpens = appOpens.filterKeys { it !in filterSet }

        val allPackages = usageAggregates.keys.union(filteredAppOpens.keys).union(notificationsByPackage.keys)
        val usageRecords = allPackages.mapNotNull { pkg ->
            val (usage, active) = usageAggregates[pkg] ?: (0L to 0L)
            if (usage < AppConstants.MINIMUM_SIGNIFICANT_SESSION_DURATION_MS && (notificationsByPackage[pkg] ?: 0) == 0) null
            else DailyAppUsageRecord(
                packageName = pkg,
                dateString = dateString,
                usageTimeMillis = usage,
                activeTimeMillis = active,
                appOpenCount = filteredAppOpens.getOrDefault(pkg, 0),
                notificationCount = notificationsByPackage.getOrDefault(pkg, 0),
                lastUpdatedTimestamp = System.currentTimeMillis()
            )
        }

        if (usageRecords.isEmpty() && totalUnlocks == 0 && totalNotifications == 0) {
            return Pair(emptyList(), null)
        }

        val deviceSummary = DailyDeviceSummary(
            dateString = dateString,
            totalUsageTimeMillis = usageRecords.sumOf { it.usageTimeMillis },
            totalUnlockedDurationMillis = unlockSessions.sumOf { it.durationMillis ?: 0L },
            totalUnlockCount = totalUnlocks,
            intentionalUnlockCount = intentionalUnlocks,
            glanceUnlockCount = glanceUnlocks,
            firstUnlockTimestampUtc = firstUnlockTime,
            lastUnlockTimestampUtc = lastUnlockTime,
            totalNotificationCount = totalNotifications,
            totalAppOpens = filteredAppOpens.values.sum(),
            lastUpdatedTimestamp = System.currentTimeMillis()
        )
        return Pair(usageRecords, deviceSummary)
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

    internal suspend fun generateInsights(
        dateString: String,
        unlockSessions: List<UnlockSessionRecord>,
        allEvents: List<RawAppEvent>,
        filterSet: Set<String>
    ): List<DailyInsight> {
        if (unlockSessions.isEmpty() && allEvents.isEmpty()) return emptyList()

        val insights = mutableListOf<DailyInsight>()
        val sortedEvents = allEvents.sortedBy { it.eventTimestamp }

        // --- Basic Unlock Insights ---
        val glanceCount = unlockSessions.count { it.sessionType == "Glance" }.toLong()
        val meaningfulCount = unlockSessions.count { it.sessionType == "Intentional" }.toLong()
        insights.add(DailyInsight(dateString = dateString, insightKey = "glance_count", longValue = glanceCount))
        insights.add(DailyInsight(dateString = dateString, insightKey = "meaningful_unlock_count", longValue = meaningfulCount))

        val firstUnlockTime = unlockSessions.minOfOrNull { it.unlockTimestamp }
        firstUnlockTime?.let {
            insights.add(DailyInsight(dateString = dateString, insightKey = "first_unlock_time", longValue = it))
        }
        unlockSessions.maxOfOrNull { it.unlockTimestamp }?.let {
            insights.add(DailyInsight(dateString = dateString, insightKey = "last_unlock_time", longValue = it))
        }

        // --- First & Last App Used ---
        if (firstUnlockTime != null) {
            // CORRECTED LOGIC: Find the first app event that occurs AFTER the first unlock event.
            sortedEvents.firstOrNull { it.eventType == RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED && it.packageName !in filterSet && it.eventTimestamp > firstUnlockTime }?.let {
                insights.add(DailyInsight(dateString = dateString, insightKey = "first_app_used", stringValue = it.packageName, longValue = it.eventTimestamp))
            }
        }
        sortedEvents.lastOrNull { it.eventType == RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED && it.packageName !in filterSet }?.let {
            insights.add(DailyInsight(dateString = dateString, insightKey = "last_app_used", stringValue = it.packageName, longValue = it.eventTimestamp))
        }
        
        // --- Top Compulsive App Insight ---
        unlockSessions
            .filter { it.isCompulsive && it.firstAppPackageName != null }
            .groupingBy { it.firstAppPackageName!! }
            .eachCount()
            .maxByOrNull { it.value }?.let { (packageName, count) ->
                insights.add(DailyInsight(dateString = dateString, insightKey = "top_compulsive_app", stringValue = packageName, longValue = count.toLong()))
            }

        // --- Top Notification-Driven App Insight ---
        unlockSessions
            .filter { it.triggeringNotificationPackageName != null }
            .groupingBy { it.triggeringNotificationPackageName!! }
            .eachCount()
            .maxByOrNull { it.value }?.let { (packageName, count) ->
                insights.add(DailyInsight(dateString = dateString, insightKey = "top_notification_unlock_app", stringValue = packageName, longValue = count.toLong()))
            }

        // --- Busiest Hour Insight ---
        if (unlockSessions.isNotEmpty()) {
            val busiestHour = unlockSessions
                .map { DateUtil.formatUtcTimestampToLocalDateTime(it.unlockTimestamp).hour }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }?.key
            busiestHour?.let {
                insights.add(DailyInsight(dateString = dateString, insightKey = "busiest_unlock_hour", longValue = it.toLong()))
            }
        }

        // --- Night Owl Insight ---
        val startOfDay = DateUtil.getStartOfDayUtcMillis(dateString)
        val endOfNightWindow = startOfDay + TimeUnit.HOURS.toMillis(4)
        sortedEvents.lastOrNull { it.eventType == RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED && it.packageName !in filterSet && it.eventTimestamp in startOfDay..endOfNightWindow }?.let {
            insights.add(DailyInsight(dateString = dateString, insightKey = "night_owl_last_app", stringValue = it.packageName, longValue = it.eventTimestamp))
        }

        return insights
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

    internal suspend fun calculateAppOpens(events: List<RawAppEvent>): Map<String, Int> {
        val sortedEvents = events.sortedBy { it.eventTimestamp }
        if (sortedEvents.isEmpty()) return emptyMap()

        val appOpens = mutableMapOf<String, Int>()
        var lastRelevantEventType: Int? = null
        var lastAppOpenTimestamp = 0L

        val relevantEventTypes = setOf(
            RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED,
            RawAppEvent.EVENT_TYPE_USER_UNLOCKED,
            RawAppEvent.EVENT_TYPE_KEYGUARD_HIDDEN,
            RawAppEvent.EVENT_TYPE_RETURN_TO_HOME
        )

        for (event in sortedEvents) {
            if (event.eventType == RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED) {
                var isAppOpen = false
                if (lastAppOpenTimestamp == 0L) { // First open ever
                    isAppOpen = true
                } else if (lastRelevantEventType == RawAppEvent.EVENT_TYPE_USER_UNLOCKED || lastRelevantEventType == RawAppEvent.EVENT_TYPE_KEYGUARD_HIDDEN) {
                    isAppOpen = true
                } else if (lastRelevantEventType == RawAppEvent.EVENT_TYPE_RETURN_TO_HOME) {
                    isAppOpen = true
                } else if (event.eventTimestamp - lastAppOpenTimestamp > AppConstants.CONTEXTUAL_APP_OPEN_DEBOUNCE_MS) {
                    isAppOpen = true
                }

                if (isAppOpen) {
                    appOpens[event.packageName] = appOpens.getOrDefault(event.packageName, 0) + 1
                    lastAppOpenTimestamp = event.eventTimestamp
                }
            }

            if (event.eventType in relevantEventTypes) {
                lastRelevantEventType = event.eventType
            }
        }
        return appOpens
    }

    private fun calculateAccurateNotificationCounts(events: List<RawAppEvent>): Map<String, Int> {
        // THIS FUNCTION IS NO LONGER USED and can be removed.
        val notificationEvents = events.filter { it.eventType == RawAppEvent.EVENT_TYPE_NOTIFICATION_POSTED }
        return notificationEvents.groupingBy { it.packageName }.eachCount()
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

    internal suspend fun aggregateUsage(allEvents: List<RawAppEvent>, periodEndDate: Long): Pair<Map<String, Pair<Long, Long>>, List<RawAppEvent>> {
        data class Session(val pkg: String, val startTime: Long, val endTime: Long)
        val sessions = mutableListOf<Session>()
        val foregroundAppStartTimes = mutableMapOf<String, Long>()
        val inferredEvents = mutableListOf<RawAppEvent>()

        val sortedEvents = allEvents.sortedBy { it.eventTimestamp }

        sortedEvents.forEachIndexed { index, event ->
            val pkg = event.packageName
            when (event.eventType) {
                RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED -> {
                    if (!foregroundAppStartTimes.containsKey(pkg)) {
                        foregroundAppStartTimes[pkg] = event.eventTimestamp
                    }
                }
                RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, RawAppEvent.EVENT_TYPE_ACTIVITY_STOPPED -> {
                    foregroundAppStartTimes.remove(pkg)?.let { startTime ->
                        val endTime = event.eventTimestamp
                        if (endTime > startTime) {
                            sessions.add(Session(pkg, startTime, endTime))

                            // Check for return to home
                            val nextEvent = sortedEvents.getOrNull(index + 1)
                            if (nextEvent == null || (nextEvent.eventType != RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED && nextEvent.eventTimestamp - endTime > 500)) {
                                inferredEvents.add(
                                    RawAppEvent(
                                        packageName = "android.system.home",
                                        className = null,
                                        eventType = RawAppEvent.EVENT_TYPE_RETURN_TO_HOME,
                                        eventTimestamp = endTime + 1,
                                        eventDateString = DateUtil.formatUtcTimestampToLocalDateString(endTime + 1),
                                        source = RawAppEvent.SOURCE_USAGE_STATS
                                    )
                                )
                            }
                        }
                    }
                }
                RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE -> {
                    foregroundAppStartTimes.keys.toList().forEach { runningPkg ->
                        foregroundAppStartTimes.remove(runningPkg)?.let { startTime ->
                            if (event.eventTimestamp > startTime) {
                                sessions.add(Session(runningPkg, startTime, event.eventTimestamp))
                            }
                        }
                    }
                }
            }
        }

        foregroundAppStartTimes.forEach { (pkg, startTime) ->
            if (periodEndDate > startTime) sessions.add(Session(pkg, startTime, periodEndDate))
        }

        val aggregator = mutableMapOf<String, Pair<Long, Long>>()
        val interactionEventsByPackage = allEvents
            .filter { RawAppEvent.isAccessibilityEvent(it.eventType) }
            .groupBy { it.packageName }

        sessions.forEach { session ->
            val usageTime = session.endTime - session.startTime
            val sessionInteractionEvents = interactionEventsByPackage[session.pkg]
                ?.filter { it.eventTimestamp in session.startTime..session.endTime }
                ?.sortedBy { it.eventTimestamp }
                ?: emptyList()

            val activeTime = calculateActiveTimeFromInteractions(sessionInteractionEvents, session.startTime, session.endTime)
            val (currentUsage, currentActive) = aggregator.getOrDefault(session.pkg, 0L to 0L)
            aggregator[session.pkg] = (currentUsage + usageTime) to (currentActive + activeTime)
        }
        return aggregator to inferredEvents
    }

    internal fun calculateActiveTimeFromInteractions(events: List<RawAppEvent>, sessionStart: Long, sessionEnd: Long): Long {
        if (events.isEmpty()) return 0L

        val intervals = events.map { event ->
            val window = when (event.eventType) {
                RawAppEvent.EVENT_TYPE_SCROLL_INFERRED, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED -> AppConstants.ACTIVE_TIME_SCROLL_WINDOW_MS
                RawAppEvent.EVENT_TYPE_ACCESSIBILITY_TYPING -> AppConstants.ACTIVE_TIME_TYPE_WINDOW_MS
                RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_CLICKED, RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_FOCUSED -> AppConstants.ACTIVE_TIME_TAP_WINDOW_MS
                RawAppEvent.EVENT_TYPE_USER_INTERACTION -> AppConstants.ACTIVE_TIME_INTERACTION_WINDOW_MS // Fallback for general interaction
                else -> 0L
            }
            event.eventTimestamp to event.eventTimestamp + window
        }.filter { it.second > it.first }

        if (intervals.isEmpty()) return 0L

        val sortedIntervals = intervals.sortedBy { it.first }
        val merged = mutableListOf<Pair<Long, Long>>()
        merged.add(sortedIntervals.first())

        for (i in 1 until sortedIntervals.size) {
            val (lastStart, lastEnd) = merged.last()
            val (currentStart, currentEnd) = sortedIntervals[i]
            if (currentStart < lastEnd) {
                merged[merged.size - 1] = lastStart to maxOf(lastEnd, currentEnd)
            } else {
                merged.add(sortedIntervals[i])
            }
        }
        return merged.sumOf { (start, end) ->
            (minOf(end, sessionEnd) - maxOf(start, sessionStart)).coerceAtLeast(0L)
        }
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
        appDatabase.withTransaction {
            // Clear any partial data for today to ensure a clean slate
            unlockSessionDao.deleteSessionsForDate(dateString)
            dailyAppUsageDao.deleteUsageForDate(dateString)
            dailyDeviceSummaryDao.deleteSummaryForDate(dateString)

            // --- 1. Process Unlocks ---
            val unlockRelatedEvents = events.filter {
                it.eventType in setOf(
                    RawAppEvent.EVENT_TYPE_USER_UNLOCKED,
                    RawAppEvent.EVENT_TYPE_KEYGUARD_HIDDEN,
                    RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE
                )
            }.sortedBy { it.eventTimestamp }

            if (unlockRelatedEvents.isNotEmpty()) {
                processUnlockEvents(
                    unlockRelatedEvents,
                    emptyList(), // No notification data for this specific backfill
                    currentFilterSet,
                    unlockEventTypes = setOf(RawAppEvent.EVENT_TYPE_USER_UNLOCKED, RawAppEvent.EVENT_TYPE_KEYGUARD_HIDDEN),
                    lockEventTypes = setOf(RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE)
                )
            }
            val unlockSessions = unlockSessionDao.getUnlockSessionsForDate(dateString)
            val totalUnlocks = unlockSessions.size

            // --- 2. Process App Opens and Usage ---
            val filteredEvents = events.filter { it.packageName !in currentFilterSet }
            val (usageAggregates, inferredEvents) = aggregateUsage(filteredEvents, DateUtil.getEndOfDayUtcMillis(dateString))
            val allEventsForAppOpens = filteredEvents + inferredEvents
            val appOpens = calculateAppOpens(allEventsForAppOpens)
            val totalAppOpens = appOpens.values.sum()

            // --- 3. Save App-Specific Usage Records ---
            val allPackages = usageAggregates.keys.union(appOpens.keys)
            val usageRecords = allPackages.map { pkg ->
                val (usage, active) = usageAggregates[pkg] ?: (0L to 0L)
                DailyAppUsageRecord(
                    packageName = pkg,
                    dateString = dateString,
                    usageTimeMillis = usage,
                    activeTimeMillis = active,
                    appOpenCount = appOpens.getOrDefault(pkg, 0),
                    notificationCount = 0, // Not calculated in this backfill
                    lastUpdatedTimestamp = System.currentTimeMillis()
                )
            }
            if (usageRecords.isNotEmpty()) {
                dailyAppUsageDao.insertAllUsage(usageRecords)
            }

            // --- 4. Save the Daily Device Summary ---
            val intentionalUnlocks = unlockSessions.count { it.sessionType == "Intentional" }
            val glanceUnlocks = unlockSessions.count { it.sessionType == "Glance" }
            dailyDeviceSummaryDao.insertOrUpdate(
                DailyDeviceSummary(
                    dateString = dateString,
                    totalUsageTimeMillis = usageRecords.sumOf { it.usageTimeMillis },
                    totalUnlockedDurationMillis = unlockSessions.sumOf { it.durationMillis ?: 0L },
                    totalUnlockCount = totalUnlocks,
                    intentionalUnlockCount = intentionalUnlocks,
                    glanceUnlockCount = glanceUnlocks,
                    totalAppOpens = totalAppOpens,
                    lastUpdatedTimestamp = System.currentTimeMillis()
                )
            )
        }
    }
}
