package com.example.scrolltrack.data

import android.app.Application
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.example.scrolltrack.db.DailyAppUsageDao // Ensure this is imported
import com.example.scrolltrack.db.DailyAppUsageRecord
import com.example.scrolltrack.db.ScrollSessionDao
import com.example.scrolltrack.db.ScrollSessionRecord
import com.example.scrolltrack.db.AppScrollDataPerDate // Import the new data class
import com.example.scrolltrack.db.RawAppEvent // Added import
import com.example.scrolltrack.db.RawAppEventDao // Added import
import com.example.scrolltrack.util.DateUtil
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.Date
import android.app.usage.UsageEvents // Added for queryEvents
import kotlinx.coroutines.flow.first // Added import for .first() on Flow
import kotlin.math.max // Added for maxOf
import kotlin.math.min // Added for minOf
import kotlinx.coroutines.withContext // Ensure this is imported
import kotlinx.coroutines.Dispatchers // Ensure this is imported
import android.annotation.SuppressLint
import com.example.scrolltrack.ui.model.AppScrollUiItem
import com.example.scrolltrack.ui.model.AppUsageUiItem
import com.example.scrolltrack.ui.model.AppDailyDetailData

class ScrollDataRepositoryImpl(
    private val scrollSessionDao: ScrollSessionDao,
    private val dailyAppUsageDao: DailyAppUsageDao, // Make sure this is passed in constructor
    private val rawAppEventDao: RawAppEventDao, // Added RawAppEventDao
    private val application: Application
) : ScrollDataRepository {

    private val TAG_REPO = "ScrollDataRepoImpl"
    private val packageManager: PackageManager = application.packageManager

    // Filter list - add package names of apps to exclude from tracking
    private val filteredPackages = setOf(
        "com.example.scrolltrack", // Exclude the app itself
        "com.android.systemui",
        "com.google.android.apps.nexuslauncher", // Example: exclude a launcher
        "com.nothing.launcher"
        // Add other system/launcher packages as needed
    )

    /**
     * Checks if the given package name should be filtered out from tracking.
     * Currently, it filters out the app's own package and common launcher packages.
     *
     * @param packageName The name of the package to check.
     * @return `true` if the package should be filtered, `false` otherwise.
     */
    internal fun isFilteredPackage(packageName: String): Boolean {
        return packageName == "com.example.scrolltrack" ||
                packageName.contains("launcher")
    }

    /**
     * Private helper to process a single scroll data record into a UI-ready item,
     * handling package manager lookups.
     */
    private suspend fun processScrollDataToUiItem(appScrollData: AppScrollData): AppScrollUiItem? {
        return withContext(Dispatchers.IO) {
            try {
                val appInfo = packageManager.getApplicationInfo(appScrollData.packageName, 0)
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                val icon = packageManager.getApplicationIcon(appScrollData.packageName)
                AppScrollUiItem(
                    id = appScrollData.packageName,
                    appName = appName,
                    icon = icon,
                    totalScroll = appScrollData.totalScroll,
                    packageName = appScrollData.packageName
                )
            } catch (e: PackageManager.NameNotFoundException) {
                // Handle cases where the app might have been uninstalled
                null
            }
        }
    }

    companion object {
        private const val CONFIG_CHANGE_PEEK_AHEAD_MS = 1000L // Time to look ahead for config change after a pause
        private const val CONFIG_CHANGE_MERGE_THRESHOLD_MS = 3000L // Time within which a resume merges a transient config-change pause
        private const val MINIMUM_SIGNIFICANT_SESSION_DURATION_MS = 2000L // Ignore sessions shorter than this
        private const val QUICK_SWITCH_THRESHOLD_MS = 2000L // Minimum duration for a session to be considered significant
        private const val EVENT_FETCH_OVERLAP_MS = 10000L // 10 seconds overlap for iterative fetching
        private const val ACTIVE_TIME_INTERACTION_WINDOW_MS = 2000L // Define the active time interaction window
    }

    override fun getAggregatedScrollDataForDate(dateString: String): Flow<List<AppScrollData>> {
        return scrollSessionDao.getAggregatedScrollDataForDate(dateString)
    }

    override fun getTotalScrollForDate(dateString: String): Flow<Long?> {
        return scrollSessionDao.getTotalScrollForDate(dateString)
    }

    override fun getAllSessions(): Flow<List<ScrollSessionRecord>> {
        return scrollSessionDao.getAllSessionsFlow()
    }

    // New method implementation
    override fun getDailyUsageRecordsForDate(dateString: String): Flow<List<DailyAppUsageRecord>> {
        return dailyAppUsageDao.getUsageForDate(dateString)
    }

    // Implementation for the missing method
    override fun getUsageRecordsForDateRange(startDateString: String, endDateString: String): Flow<List<DailyAppUsageRecord>> {
        return dailyAppUsageDao.getUsageRecordsForDateRange(startDateString, endDateString)
    }

    // Helper function to map UsageEvents.Event types to our internal RawAppEvent types
    private fun mapUsageEventTypeToInternal(eventType: Int): Int {
        return when (eventType) {
            UsageEvents.Event.NONE -> RawAppEvent.EVENT_TYPE_UNKNOWN
            UsageEvents.Event.ACTIVITY_RESUMED, UsageEvents.Event.MOVE_TO_FOREGROUND -> RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED
            UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.MOVE_TO_BACKGROUND -> RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED
            UsageEvents.Event.ACTIVITY_STOPPED -> RawAppEvent.EVENT_TYPE_ACTIVITY_STOPPED
            UsageEvents.Event.CONFIGURATION_CHANGE -> RawAppEvent.EVENT_TYPE_CONFIGURATION_CHANGE
            UsageEvents.Event.USER_INTERACTION -> RawAppEvent.EVENT_TYPE_USER_INTERACTION
            UsageEvents.Event.SCREEN_INTERACTIVE -> RawAppEvent.EVENT_TYPE_SCREEN_INTERACTIVE
            UsageEvents.Event.SCREEN_NON_INTERACTIVE -> RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE
            UsageEvents.Event.KEYGUARD_SHOWN -> RawAppEvent.EVENT_TYPE_KEYGUARD_SHOWN
            UsageEvents.Event.KEYGUARD_HIDDEN -> RawAppEvent.EVENT_TYPE_KEYGUARD_HIDDEN
            UsageEvents.Event.FOREGROUND_SERVICE_START -> RawAppEvent.EVENT_TYPE_FOREGROUND_SERVICE_START
            UsageEvents.Event.FOREGROUND_SERVICE_STOP -> RawAppEvent.EVENT_TYPE_FOREGROUND_SERVICE_STOP
            // Add other specific mappings as needed, e.g., SHORTCUT_INVOCATION, STANDBY_BUCKET_CHANGED
            else -> RawAppEvent.EVENT_TYPE_UNKNOWN // Default for unhandled event types
        }
    }

    /**
     * Converts a system UsageEvents.Event into the app's internal RawAppEvent data model.
     * This is a public helper to make it testable and abstract the conversion.
     */
    fun mapUsageEventToRawAppEvent(event: android.app.usage.UsageEvents.Event): RawAppEvent? {
        val packageName = event.packageName ?: return null
        val eventTimestampUTC = event.timeStamp

        val internalEventType = when (event.eventType) {
            android.app.usage.UsageEvents.Event.NONE -> RawAppEvent.EVENT_TYPE_UNKNOWN
            android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED, android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED
            android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED, android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED
            android.app.usage.UsageEvents.Event.ACTIVITY_STOPPED -> RawAppEvent.EVENT_TYPE_ACTIVITY_STOPPED
            android.app.usage.UsageEvents.Event.CONFIGURATION_CHANGE -> RawAppEvent.EVENT_TYPE_CONFIGURATION_CHANGE
            android.app.usage.UsageEvents.Event.USER_INTERACTION -> RawAppEvent.EVENT_TYPE_USER_INTERACTION
            android.app.usage.UsageEvents.Event.SCREEN_INTERACTIVE -> RawAppEvent.EVENT_TYPE_SCREEN_INTERACTIVE
            android.app.usage.UsageEvents.Event.SCREEN_NON_INTERACTIVE -> RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE
            android.app.usage.UsageEvents.Event.KEYGUARD_SHOWN -> RawAppEvent.EVENT_TYPE_KEYGUARD_SHOWN
            android.app.usage.UsageEvents.Event.KEYGUARD_HIDDEN -> RawAppEvent.EVENT_TYPE_KEYGUARD_HIDDEN
            android.app.usage.UsageEvents.Event.FOREGROUND_SERVICE_START -> RawAppEvent.EVENT_TYPE_FOREGROUND_SERVICE_START
            android.app.usage.UsageEvents.Event.FOREGROUND_SERVICE_STOP -> RawAppEvent.EVENT_TYPE_FOREGROUND_SERVICE_STOP
            else -> RawAppEvent.EVENT_TYPE_UNKNOWN
        }

        return RawAppEvent(
            packageName = packageName,
            className = event.className,
            eventType = internalEventType,
            eventTimestamp = eventTimestampUTC,
            eventDateString = DateUtil.formatUtcTimestampToLocalDateString(eventTimestampUTC)
        )
    }

    override suspend fun updateTodayAppUsageStats(): Boolean = withContext(Dispatchers.IO) {
        val usageStatsManager =
            application.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: run {
                    Log.e(TAG_REPO, "UsageStatsManager not available.")
                    return@withContext false
                }

        val currentUtcTimestamp = DateUtil.getUtcTimestamp()
        val todayDateString = DateUtil.getCurrentLocalDateString()

        // Step 1: Determine Start of Day UTC (SOD)
        val startOfDayUTC = DateUtil.getStartOfDayUtcMillis(todayDateString)

        // Step 2: Get the latest event timestamp from DAO
        val initialDaoTimestamp = rawAppEventDao.getLatestEventTimestampForDate(todayDateString)

        // Step 3: Determine the base timestamp. This is the one we will subtract overlap from.
        // It's SOD if initial DAO is null or before SOD, otherwise it's the initial DAO value.
        val actualBaseForOverlap: Long =
            if (initialDaoTimestamp == null || initialDaoTimestamp < startOfDayUTC) {
                startOfDayUTC
            } else {
                initialDaoTimestamp
            }

        // Step 4: Calculate the desired start time (base - overlap)
        val desiredQueryStartTime = actualBaseForOverlap - EVENT_FETCH_OVERLAP_MS

        // Step 5: The final query start time is the later of SOD or the desired start time.
        val finalQueryStartTime = max(startOfDayUTC, desiredQueryStartTime)

        val endOfTodayUTC = DateUtil.getEndOfDayUtcMillis(todayDateString)

        Log.d(TAG_REPO, "UsageStats Query Window: $finalQueryStartTime -> $endOfTodayUTC. (Calculation Details: StartOfDay_UTC=$startOfDayUTC, DB_LatestEvent=$initialDaoTimestamp, BaseForOverlap=$actualBaseForOverlap, TargetStart_PreClamp=$desiredQueryStartTime)")

        // Use UsageStatsManager to query events
        val usageEvents = usageStatsManager.queryEvents(finalQueryStartTime, endOfTodayUTC)

        val rawEventsToInsert = mutableListOf<RawAppEvent>()
        val eventProcessingBatchSize = 100
        var lastSystemEventTimestamp = 0L // For out-of-order detection

        if (usageEvents != null) {
            val event = UsageEvents.Event()
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)

                val currentSystemEventTimestamp = event.timeStamp
                if (lastSystemEventTimestamp != 0L && currentSystemEventTimestamp < lastSystemEventTimestamp) {
                    Log.w(TAG_REPO, "Out-of-order event from UsageStatsManager for ${event.packageName}. Current: $currentSystemEventTimestamp, Prev: $lastSystemEventTimestamp. Type: ${event.eventType}")
                }
                lastSystemEventTimestamp = currentSystemEventTimestamp

                val packageName = event.packageName ?: continue
                val eventTimestampUTC = event.timeStamp
                // Ensure event is within the day to avoid issues if queryEvents is too broad
                if (eventTimestampUTC < startOfDayUTC || eventTimestampUTC > endOfTodayUTC) {
                    continue
                }

                mapUsageEventToRawAppEvent(event)?.let { rawEventsToInsert.add(it) }

                if (rawEventsToInsert.size >= eventProcessingBatchSize) {
                    try {
                        rawAppEventDao.insertEvents(rawEventsToInsert.toList())
                        rawEventsToInsert.clear()
                    } catch (e: Exception) {
                        Log.e(TAG_REPO, "Error batch inserting raw events", e)
                    }
                }
            }
            // Insert any remaining raw events
            if (rawEventsToInsert.isNotEmpty()) {
                try {
                    rawAppEventDao.insertEvents(rawEventsToInsert.toList())
                    rawEventsToInsert.clear()
                } catch (e: Exception) {
                    Log.e(TAG_REPO, "Error inserting remaining raw events", e)
                }
            }
        } else {
            Log.w(TAG_REPO, "UsageEvents object from UsageStatsManager is null, no new raw events logged for today.")
            // We might still proceed to aggregate existing raw events for the day if any.
        }

        // Step 2: Clear today's old aggregated data
        try {
            dailyAppUsageDao.deleteUsageForDate(todayDateString)
            Log.d(TAG_REPO, "Cleared existing aggregated usage for $todayDateString")
        } catch (e: Exception) {
            Log.e(TAG_REPO, "Error clearing aggregated usage for $todayDateString", e)
            // Decide if this is a fatal error for the update process
        }

        // Step 3: Fetch all stored raw events for today to perform aggregation
        val storedRawEventsForToday: List<RawAppEvent> = try {
            Log.d(TAG_REPO, "Attempting to fetch stored raw events for $todayDateString from $startOfDayUTC to $endOfTodayUTC")
            val events = rawAppEventDao.getEventsForPeriod(startOfDayUTC, endOfTodayUTC) // Already sorts by timestamp ASC
            Log.d(TAG_REPO, "Successfully fetched ${events.size} stored raw events for $todayDateString")
            events
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.e(TAG_REPO, "Fetching stored raw events for $todayDateString was CANCELLED", e)
            throw e // Re-throw cancellation to ensure the job is properly cancelled
        } catch (e: Exception) {
            Log.e(TAG_REPO, "Error fetching stored raw events for $todayDateString", e)
            return@withContext false
        }

        if (storedRawEventsForToday.isEmpty()) {
            Log.i(TAG_REPO, "No stored raw events found for $todayDateString. No aggregation to perform.")
            return@withContext true // Successfully did nothing if no events
        }

        Log.d(TAG_REPO, "Processing ${storedRawEventsForToday.size} stored raw events for $todayDateString for aggregation.")

        val appUsageAggregator = aggregateUsage(storedRawEventsForToday, endOfTodayUTC)

        var recordsProcessed = 0
        if (appUsageAggregator.isNotEmpty()) {
            Log.d(TAG_REPO, "Aggregated Usage for $todayDateString from stored raw events:")
            val dailyRecordsToInsert = mutableListOf<DailyAppUsageRecord>()
            for ((key, totals) in appUsageAggregator) {
                val (pkg, dateStr) = key
                val foregroundDuration = totals.first
                val activeDuration = totals.second
                if (foregroundDuration > 0) {
                    dailyRecordsToInsert.add(DailyAppUsageRecord(
                        packageName = pkg,
                        dateString = dateStr,
                        usageTimeMillis = foregroundDuration,
                        activeTimeMillis = activeDuration,
                        lastUpdatedTimestamp = DateUtil.getUtcTimestamp()
                    ))
                }
            }
            if (dailyRecordsToInsert.isNotEmpty()){
                dailyAppUsageDao.insertAllUsage(dailyRecordsToInsert)
                recordsProcessed = dailyRecordsToInsert.size
            }
        }
        Log.i(TAG_REPO, "Successfully inserted/updated $recordsProcessed usage records for $todayDateString from stored raw events.")
        return@withContext true
    }

    private fun calculateActiveTimeFromInteractions(interactionTimestamps: List<Long>, sessionStartTime: Long, sessionEndTime: Long): Long {
        if (interactionTimestamps.isEmpty()) return 0L

        val intervals = interactionTimestamps.map { it to it + ACTIVE_TIME_INTERACTION_WINDOW_MS }.sortedBy { it.first }

        val merged = mutableListOf<Pair<Long, Long>>()
        if (intervals.isNotEmpty()) {
            merged.add(intervals.first())
        } else {
            return 0L
        }

        for (i in 1 until intervals.size) {
            val current = intervals[i]
            val last = merged.last()
            if (current.first < last.second) {
                val newEnd = max(last.second, current.second)
                merged[merged.size - 1] = last.first to newEnd
            } else {
                merged.add(current)
            }
        }
        
        // Clip merged intervals to the session boundaries.
        val clippedIntervals = merged.map { (start, end) ->
            max(start, sessionStartTime) to min(end, sessionEndTime)
        }.filter { it.first < it.second }

        return clippedIntervals.sumOf { it.second - it.first }
    }

    internal fun aggregateUsage(allEvents: List<RawAppEvent>, periodEndDate: Long): Map<Pair<String, String>, Pair<Long, Long>> {
        // Stage 1: Identify all foreground sessions from the event stream.
        data class Session(val pkg: String, val startTime: Long, val endTime: Long)
        val sessions = mutableListOf<Session>()
        val currentSessionStart = mutableMapOf<String, Long>()

        allEvents.forEach { event ->
            val pkg = event.packageName
            if (isFilteredPackage(pkg)) return@forEach

            val eventType = event.eventType
            val timestamp = event.eventTimestamp

            val isInteraction = eventType >= RawAppEvent.EVENT_TYPE_ACCESSIBILITY_OFFSET
            val isResume = eventType == RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED
            val isPauseOrStop = eventType == RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED || eventType == RawAppEvent.EVENT_TYPE_ACTIVITY_STOPPED
            val isScreenOff = eventType == RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE

            if (isResume) {
                // An app becomes primary. End sessions for all other apps.
                currentSessionStart.keys.filter { it != pkg }.forEach { otherPkg ->
                    currentSessionStart.remove(otherPkg)?.let { startTime ->
                        if(timestamp > startTime) sessions.add(Session(otherPkg, startTime, timestamp - 1))
                    }
                }
                // Start a new session for the current app if it wasn't already running.
                if (!currentSessionStart.containsKey(pkg)) {
                    currentSessionStart[pkg] = timestamp
                }
            } else if (isPauseOrStop) {
                // App is paused or stopped, end its session.
                currentSessionStart.remove(pkg)?.let { startTime ->
                    if(timestamp > startTime) sessions.add(Session(pkg, startTime, timestamp))
                }
            } else if (isScreenOff) {
                // Screen off ends all running sessions.
                currentSessionStart.keys.toList().forEach { runningPkg ->
                    currentSessionStart.remove(runningPkg)?.let { startTime ->
                        if(timestamp > startTime) sessions.add(Session(runningPkg, startTime, timestamp))
                    }
                }
            }
        }
        // End any sessions still running at the end of the specified period.
        currentSessionStart.forEach { (pkg, startTime) ->
            if(periodEndDate > startTime) sessions.add(Session(pkg, startTime, periodEndDate))
        }

        // Stage 2: Calculate usage and active time for each session.
        val aggregator = mutableMapOf<Pair<String, String>, Pair<Long, Long>>()
        val accessibilityEventTypes = setOf(
            RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_CLICKED,
            RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_FOCUSED,
            RawAppEvent.EVENT_TYPE_ACCESSIBILITY_TYPING
        )
        val interactionEventsByPackage = allEvents
            .filter { it.eventType in accessibilityEventTypes }
            .groupBy { it.packageName }

        sessions.forEach { session ->
            val usageTime = session.endTime - session.startTime
            if (usageTime < MINIMUM_SIGNIFICANT_SESSION_DURATION_MS) return@forEach

            val dateString = DateUtil.formatUtcTimestampToLocalDateString(session.startTime)
            val key = Pair(session.pkg, dateString)

            // Find interaction events that fall within this specific session's timeframe.
            val sessionInteractionTimestamps = interactionEventsByPackage[session.pkg]
                ?.map { it.eventTimestamp }
                ?.filter { it in session.startTime..session.endTime }
                ?.sorted()
                ?: emptyList()

            val activeTime = calculateActiveTimeFromInteractions(sessionInteractionTimestamps, session.startTime, session.endTime)

            val (currentUsage, currentActive) = aggregator.getOrDefault(key, 0L to 0L)
            aggregator[key] = (currentUsage + usageTime) to (currentActive + activeTime)
        }
        return aggregator
    }

    override suspend fun getTotalUsageTimeMillisForDate(dateString: String): Long? {
        // Old implementation queried UsageStatsManager directly.
        // New implementation will delegate to the DAO to sum from our processed records.
        Log.d(TAG_REPO, "Fetching total usage time for $dateString from dailyAppUsageDao.")
        return dailyAppUsageDao.getTotalUsageTimeMillisForDate(dateString).first()
    }

    override suspend fun backfillHistoricalAppUsageData(numberOfDays: Int): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG_REPO, "Starting historical backfill for the last $numberOfDays days.")

        val usageStatsManager =
            application.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: run {
                    Log.e(TAG_REPO, "UsageStatsManager not available for backfill.")
                    return@withContext false
                }

        val today = Calendar.getInstance()
        var overallSuccess = true

        for (i in 1..numberOfDays) {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = today.timeInMillis
                add(Calendar.DAY_OF_YEAR, -i)
            }
            val historicalDateLocalString = DateUtil.formatDateToYyyyMmDdString(calendar.time)
            val startOfDayUTC = DateUtil.getStartOfDayUtcMillis(historicalDateLocalString)
            val endOfDayUTC = DateUtil.getEndOfDayUtcMillis(historicalDateLocalString)

            try {
                // Check if we already have data for this day
                val existingRecordsCount = dailyAppUsageDao.getUsageCountForDateString(historicalDateLocalString)
                if (existingRecordsCount > 0) {
                    Log.i(TAG_REPO, "Skipping backfill for $historicalDateLocalString, records already exist.")
                    continue
                }

                // Fetch raw events for the historical day from UsageStatsManager
                val events = usageStatsManager.queryEvents(startOfDayUTC, endOfDayUTC)
                val rawEventsFromUsageManager = mutableListOf<RawAppEvent>()
                if (events != null) {
                    val event = UsageEvents.Event()
                    while (events.hasNextEvent()) {
                        events.getNextEvent(event)
                        val internalEventType = mapUsageEventTypeToInternal(event.eventType)
                        rawEventsFromUsageManager.add(
                            RawAppEvent(
                                packageName = event.packageName,
                                className = event.className,
                                eventType = internalEventType,
                                eventTimestamp = event.timeStamp,
                                eventDateString = DateUtil.formatUtcTimestampToLocalDateString(event.timeStamp)
                            )
                        )
                    }
                }

                // Also fetch raw accessibility events for the historical day from our DB
                val allDbEventsForDay = rawAppEventDao.getEventsForPeriod(startOfDayUTC, endOfDayUTC)
                val rawAccessibilityEventsForDay = allDbEventsForDay.filter { it.eventType >= RawAppEvent.EVENT_TYPE_ACCESSIBILITY_OFFSET }

                val allRawEvents = (rawEventsFromUsageManager + rawAccessibilityEventsForDay)
                    .sortedBy { it.eventTimestamp }
                    .distinctBy { "${it.packageName}-${it.eventType}-${it.eventTimestamp}" }


                if (allRawEvents.isEmpty()) {
                    Log.i(TAG_REPO, "No combined usage or accessibility events found for $historicalDateLocalString.")
                continue
                }

                dailyAppUsageDao.deleteUsageForDate(historicalDateLocalString)

                Log.d(TAG_REPO, "Backfill for $historicalDateLocalString: Processing ${allRawEvents.size} raw events.")

                val appUsageAggregator = aggregateUsage(allRawEvents, endOfDayUTC)

                if (appUsageAggregator.isNotEmpty()) {
                    val recordsToInsert = appUsageAggregator.map { (key, totals) ->
                        val (pkg, date) = key
                        val (usage, active) = totals
                        DailyAppUsageRecord(
                            packageName = pkg,
                            dateString = date,
                            usageTimeMillis = usage,
                            activeTimeMillis = active,
                            lastUpdatedTimestamp = DateUtil.getUtcTimestamp()
                        )
                    }
                    dailyAppUsageDao.insertAllUsage(recordsToInsert)
                    Log.i(TAG_REPO, "Successfully backfilled ${recordsToInsert.size} records for $historicalDateLocalString.")
                            } else {
                    Log.i(TAG_REPO, "No significant app usage to save for $historicalDateLocalString.")
                }

            } catch (e: Exception) {
                Log.e(TAG_REPO, "Error during backfill for $historicalDateLocalString: ${e.message}", e)
                        overallSuccess = false
                    }
                }
        return@withContext overallSuccess
    }

    @SuppressLint("PackageManagerGetSignatures")
    private fun getAppName(packageName: String): String {
        return try {
            val packageManager = application.packageManager
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.applicationInfo?.loadLabel(packageManager)?.toString() ?: packageName
        } catch (e: PackageManager.NameNotFoundException) {
            packageName // Fallback to package name if not found
        }
    }

    // --- Methods for App Detail Screen Chart Data ---
    override suspend fun getUsageForPackageAndDates(packageName: String, dateStrings: List<String>): List<DailyAppUsageRecord> {
        return dailyAppUsageDao.getUsageForPackageAndDates(packageName, dateStrings)
    }


    override suspend fun getAggregatedScrollForPackageAndDates(packageName: String, dateStrings: List<String>): List<AppScrollDataPerDate> {
        return scrollSessionDao.getAggregatedScrollForPackageAndDates(packageName, dateStrings)
    }

    override suspend fun getAggregatedScrollForDateUi(dateString: String): List<AppScrollUiItem> {
        val aggregatedData = scrollSessionDao.getAggregatedScrollDataForDate(dateString).first()
        return aggregatedData.mapNotNull { processScrollDataToUiItem(it) }
    }
}




