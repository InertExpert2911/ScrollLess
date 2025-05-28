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

class ScrollDataRepositoryImpl(
    private val scrollSessionDao: ScrollSessionDao,
    private val dailyAppUsageDao: DailyAppUsageDao, // Make sure this is passed in constructor
    private val rawAppEventDao: RawAppEventDao, // Added RawAppEventDao
    private val application: Application
) : ScrollDataRepository {

    private val TAG_REPO = "ScrollDataRepoImpl"
    private val packageManager: PackageManager = application.packageManager

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

    override suspend fun updateTodayAppUsageStats(): Boolean = withContext(Dispatchers.IO) {
        val usageStatsManager =
            application.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: run {
                    Log.e(TAG_REPO, "UsageStatsManager not available.")
                    return@withContext false
                }

        val todayLocalString = DateUtil.getCurrentLocalDateString()
        val startOfDayUTC = DateUtil.getStartOfDayUtcMillis(todayLocalString)
        val endOfDayUTC = DateUtil.getEndOfDayUtcMillis(todayLocalString)

        Log.d(TAG_REPO, "Starting update for today's app usage stats. Range: $startOfDayUTC to $endOfDayUTC")

        // Step 1: Query and Log fresh raw events from UsageStatsManager
        val systemEvents: UsageEvents? = try {
            usageStatsManager.queryEvents(startOfDayUTC, endOfDayUTC)
        } catch (e: Exception) {
            Log.e(TAG_REPO, "Error querying usage events from UsageStatsManager", e)
            null
        }

        val rawEventsToInsert = mutableListOf<RawAppEvent>()
        val eventProcessingBatchSize = 100

        if (systemEvents != null) {
            val event = UsageEvents.Event()
            while (systemEvents.hasNextEvent()) {
                systemEvents.getNextEvent(event)
                val packageName = event.packageName ?: continue
                val eventTimestampUTC = event.timeStamp
                // Ensure event is within the day to avoid issues if queryEvents is too broad
                if (eventTimestampUTC < startOfDayUTC || eventTimestampUTC > endOfDayUTC) {
                    continue
                }

                val internalEventType = mapUsageEventTypeToInternal(event.eventType)
                val eventDateString = DateUtil.formatUtcTimestampToLocalDateString(eventTimestampUTC)
                rawEventsToInsert.add(
                    RawAppEvent(
                        packageName = packageName,
                        className = event.className,
                        eventType = internalEventType,
                        eventTimestamp = eventTimestampUTC,
                        eventDateString = eventDateString
                    )
                )

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
            dailyAppUsageDao.deleteUsageForDate(todayLocalString)
            Log.d(TAG_REPO, "Cleared existing aggregated usage for $todayLocalString")
        } catch (e: Exception) {
            Log.e(TAG_REPO, "Error clearing aggregated usage for $todayLocalString", e)
            // Decide if this is a fatal error for the update process
        }

        // Step 3: Fetch all stored raw events for today to perform aggregation
        val storedRawEventsForToday: List<RawAppEvent> = try {
            rawAppEventDao.getEventsForPeriod(startOfDayUTC, endOfDayUTC) // Already sorts by timestamp ASC
        } catch (e: Exception) {
            Log.e(TAG_REPO, "Error fetching stored raw events for $todayLocalString", e)
            return@withContext false
        }

        if (storedRawEventsForToday.isEmpty()) {
            Log.i(TAG_REPO, "No stored raw events found for $todayLocalString. No aggregation to perform.")
            return@withContext true // Successfully did nothing if no events
        }

        Log.d(TAG_REPO, "Processing ${storedRawEventsForToday.size} stored raw events for $todayLocalString for aggregation.")

        // Step 4 & 5: Process stored raw events and aggregate usage
        val currentAppSessions = mutableMapOf<String, Long>() // packageName to sessionStartTimeUTC
        val appUsageAggregator = mutableMapOf<Pair<String, String>, Long>() // Pair(PackageName, LocalDateString) to DurationMillis

        for (rawEvent in storedRawEventsForToday) {
            val packageName = rawEvent.packageName
            val eventTimestampUTC = rawEvent.eventTimestamp
            val internalEventType = rawEvent.eventType

            // Filter out self-package events, except screen events (already handled during initial logging but good for consistency)
            if (packageName == application.packageName && internalEventType != RawAppEvent.EVENT_TYPE_SCREEN_INTERACTIVE && internalEventType != RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE) {
                continue
            }

            when (internalEventType) {
                RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED -> {
                    if (currentAppSessions.containsKey(packageName)) {
                        val previousSessionStartTimeUTC = currentAppSessions[packageName]!!
                        if (eventTimestampUTC > previousSessionStartTimeUTC) {
                            processAndAggregateSession(packageName, previousSessionStartTimeUTC, eventTimestampUTC - 1, appUsageAggregator)
                        }
                    }
                    currentAppSessions[packageName] = eventTimestampUTC
                }
                RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED -> {
                    val sessionStartTimeFromMap = currentAppSessions.remove(packageName)
                    if (sessionStartTimeFromMap != null) {
                        if (eventTimestampUTC > sessionStartTimeFromMap) {
                            processAndAggregateSession(packageName, sessionStartTimeFromMap, eventTimestampUTC, appUsageAggregator)
                        } else {
                            Log.w(TAG_REPO, "PAUSED event for $packageName: End time $eventTimestampUTC <= start time $sessionStartTimeFromMap. Skipping.")
                        }
                    }
                    // No warning here for missing resume, as we are processing a complete log for the day
                }
                RawAppEvent.EVENT_TYPE_USER_INTERACTION -> {
                    if (!currentAppSessions.containsKey(packageName)) {
                        currentAppSessions[packageName] = eventTimestampUTC
                    }
                }
                RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE -> {
                    val appsToFinalize = currentAppSessions.toMap()
                    for ((pkg, startTime) in appsToFinalize) {
                        if (eventTimestampUTC > startTime) {
                            processAndAggregateSession(pkg, startTime, eventTimestampUTC, appUsageAggregator)
                        }
                        currentAppSessions.remove(pkg)
                    }
                }
                // We don't need to explicitly handle SCREEN_INTERACTIVE for session start here,
                // as ACTIVITY_RESUMED will handle it. Screen events are more for overall device state.
            }
        }

        // Finalize any sessions still marked as active at the end of the day
        for ((pkgName, sessionStartTimeUTC) in currentAppSessions) {
            if (endOfDayUTC > sessionStartTimeUTC) {
                processAndAggregateSession(pkgName, sessionStartTimeUTC, endOfDayUTC, appUsageAggregator)
            }
        }
        currentAppSessions.clear()

        var recordsProcessed = 0
        if (appUsageAggregator.isNotEmpty()) {
            Log.d(TAG_REPO, "Aggregated Usage for $todayLocalString from stored raw events:")
            val dailyRecordsToInsert = mutableListOf<DailyAppUsageRecord>()
            for ((key, totalDuration) in appUsageAggregator) {
                val (pkg, dateStr) = key
                if (totalDuration > 0) {
                    dailyRecordsToInsert.add(DailyAppUsageRecord(
                        packageName = pkg,
                        dateString = dateStr,
                        usageTimeMillis = totalDuration,
                        lastUpdatedTimestamp = DateUtil.getUtcTimestamp()
                    ))
                }
            }
            if (dailyRecordsToInsert.isNotEmpty()){
                dailyAppUsageDao.insertAllUsage(dailyRecordsToInsert)
                recordsProcessed = dailyRecordsToInsert.size
            }
        }
        Log.i(TAG_REPO, "Successfully inserted/updated $recordsProcessed usage records for $todayLocalString from stored raw events.")
        return@withContext true
    }

    // Helper function to process a single session and add its duration to the aggregator
    private fun processAndAggregateSession(
        packageName: String,
        sessionStartTimeUTC: Long,
        sessionEndTimeUTC: Long,
        aggregator: MutableMap<Pair<String, String>, Long>
    ) {
        if (sessionStartTimeUTC == 0L || sessionEndTimeUTC <= sessionStartTimeUTC) {
            // Log.v(TAG_REPO, "Skipping zero or negative duration session for $packageName ($sessionStartTimeUTC -> $sessionEndTimeUTC)")
            return // No valid duration to record
        }

        // Log.d(TAG_REPO, "Processing session for $packageName: Start: $sessionStartTimeUTC, End: $sessionEndTimeUTC, Duration: ${sessionEndTimeUTC - sessionStartTimeUTC}")

        var currentProcessingTimeUTC = sessionStartTimeUTC

        while (currentProcessingTimeUTC < sessionEndTimeUTC) {
            val currentLocalDateString = DateUtil.formatUtcTimestampToLocalDateString(currentProcessingTimeUTC)
            val endOfCurrentLocalDateUtc = DateUtil.getEndOfDayUtcMillis(currentLocalDateString)

            // Determine the end of this segment: either the end of the current local day or the actual session end, whichever comes first.
            val segmentEndTimeUTC = min(endOfCurrentLocalDateUtc, sessionEndTimeUTC)

            // Duration for this segment
            val segmentDuration = segmentEndTimeUTC - currentProcessingTimeUTC // Revised

            if (segmentDuration > 0) { // Ensure duration is strictly positive
                val key = Pair(packageName, currentLocalDateString)
                aggregator[key] = (aggregator[key] ?: 0L) + segmentDuration
                // Log.d(TAG_REPO, "  -> Added to $currentLocalDateString for $packageName: $segmentDuration ms. New total for day: ${aggregator[key]}")
            }
            // Move to the start of the next segment (which is the end time of the current segment)
            currentProcessingTimeUTC = segmentEndTimeUTC // Revised
        }
    }

    override suspend fun getTotalUsageTimeMillisForDate(dateString: String): Long? {
        // Old implementation queried UsageStatsManager directly.
        // New implementation will delegate to the DAO to sum from our processed records.
        Log.d(TAG_REPO, "Fetching total usage time for $dateString from dailyAppUsageDao.")
        return dailyAppUsageDao.getTotalUsageTimeMillisForDate(dateString).first()
    }

    override suspend fun backfillHistoricalAppUsageData(numberOfDays: Int): Boolean = withContext(Dispatchers.IO) {
        val usageStatsManager =
            application.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: run {
                    Log.e(TAG_REPO, "UsageStatsManager not available for backfill.")
                    return@withContext false
                }

        Log.i(TAG_REPO, "Starting historical app usage data backfill for the last $numberOfDays days using event-based processing.")

        var overallSuccess = true

        for (i in numberOfDays downTo 1) { // Iterate from N days ago up to yesterday
            val calendar = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -i)
            }
            val historicalDateLocalString = DateUtil.formatDateToYyyyMmDdString(calendar.time)
            val startOfDayUTC = DateUtil.getStartOfDayUtcMillis(historicalDateLocalString)
            val endOfDayUTC = DateUtil.getEndOfDayUtcMillis(historicalDateLocalString)

            Log.d(TAG_REPO, "Backfilling data for date: $historicalDateLocalString (Range: $startOfDayUTC to $endOfDayUTC)")

            // Step 1: Clear any previously logged raw events and aggregated data for this historical day
            try {
                rawAppEventDao.deleteEventsForDateString(historicalDateLocalString)
                dailyAppUsageDao.deleteUsageForDate(historicalDateLocalString)
                Log.d(TAG_REPO, "Cleared existing raw and aggregated data for $historicalDateLocalString")
            } catch (e: Exception) {
                Log.e(TAG_REPO, "Error clearing existing data for $historicalDateLocalString during backfill", e)
                overallSuccess = false
                continue // Skip to next day if clearing fails
            }

            // Step 2: Query and Log fresh raw events from UsageStatsManager for the historical day
            val systemEvents: UsageEvents? = try {
                usageStatsManager.queryEvents(startOfDayUTC, endOfDayUTC)
            } catch (e: Exception) {
                Log.e(TAG_REPO, "Error querying usage events from UsageStatsManager for $historicalDateLocalString", e)
                overallSuccess = false
                continue // Skip to next day
            }

            val rawEventsToInsert = mutableListOf<RawAppEvent>()
            if (systemEvents != null) {
                val event = UsageEvents.Event()
                while (systemEvents.hasNextEvent()) {
                    systemEvents.getNextEvent(event)
                    val packageName = event.packageName ?: continue
                    val eventTimestampUTC = event.timeStamp
                    if (eventTimestampUTC < startOfDayUTC || eventTimestampUTC > endOfDayUTC) {
                        continue // Should not happen if queryEvents range is precise
                    }
                    val internalEventType = mapUsageEventTypeToInternal(event.eventType)
                    rawEventsToInsert.add(
                        RawAppEvent(
                            packageName = packageName,
                            className = event.className,
                            eventType = internalEventType,
                            eventTimestamp = eventTimestampUTC,
                            eventDateString = historicalDateLocalString // Use the target date string
                        )
                    )
                }
                if (rawEventsToInsert.isNotEmpty()) {
                    try {
                        rawAppEventDao.insertEvents(rawEventsToInsert.toList())
                    } catch (e: Exception) {
                        Log.e(TAG_REPO, "Error batch inserting raw events for $historicalDateLocalString", e)
                        overallSuccess = false
                        // Continue to attempt aggregation with any events that might have been stored from other sources if desired
                    }
                }
            } else {
                Log.w(TAG_REPO, "UsageEvents from UsageStatsManager is null for $historicalDateLocalString.")
            }

            // Step 3: Fetch all stored raw events for the historical day to perform aggregation
            val storedRawEventsForDay: List<RawAppEvent> = try {
                rawAppEventDao.getEventsForPeriod(startOfDayUTC, endOfDayUTC)
            } catch (e: Exception) {
                Log.e(TAG_REPO, "Error fetching stored raw events for $historicalDateLocalString", e)
                overallSuccess = false
                continue // Skip to next day
            }

            if (storedRawEventsForDay.isEmpty()) {
                Log.i(TAG_REPO, "No stored raw events found for $historicalDateLocalString after logging. No aggregation to perform.")
                // This is not an error, just no data for the day.
                continue
            }
            Log.d(TAG_REPO, "Processing ${storedRawEventsForDay.size} stored raw events for $historicalDateLocalString for aggregation.")

            // Step 4 & 5: Process stored raw events and aggregate usage (same logic as updateTodayAppUsageStats)
            val currentAppSessions = mutableMapOf<String, Long>()
            val appUsageAggregator = mutableMapOf<Pair<String, String>, Long>()

            for (rawEvent in storedRawEventsForDay) {
                val packageName = rawEvent.packageName
                val eventTimestampUTC = rawEvent.eventTimestamp
                val internalEventType = rawEvent.eventType

                if (packageName == application.packageName && internalEventType != RawAppEvent.EVENT_TYPE_SCREEN_INTERACTIVE && internalEventType != RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE) {
                    continue
                }

                when (internalEventType) {
                    RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED -> {
                        if (currentAppSessions.containsKey(packageName)) {
                            val previousSessionStartTimeUTC = currentAppSessions[packageName]!!
                            if (eventTimestampUTC > previousSessionStartTimeUTC) {
                                processAndAggregateSession(packageName, previousSessionStartTimeUTC, eventTimestampUTC - 1, appUsageAggregator)
                            }
                        }
                        currentAppSessions[packageName] = eventTimestampUTC
                    }
                    RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED -> {
                        val sessionStartTimeFromMap = currentAppSessions.remove(packageName)
                        if (sessionStartTimeFromMap != null && eventTimestampUTC > sessionStartTimeFromMap) {
                            processAndAggregateSession(packageName, sessionStartTimeFromMap, eventTimestampUTC, appUsageAggregator)
                        }
                    }
                    RawAppEvent.EVENT_TYPE_USER_INTERACTION -> {
                        if (!currentAppSessions.containsKey(packageName)) {
                            currentAppSessions[packageName] = eventTimestampUTC
                        }
                    }
                    RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE -> {
                        val appsToFinalize = currentAppSessions.toMap()
                        for ((pkg, startTime) in appsToFinalize) {
                            if (eventTimestampUTC > startTime) {
                                processAndAggregateSession(pkg, startTime, eventTimestampUTC, appUsageAggregator)
                            }
                            currentAppSessions.remove(pkg)
                        }
                    }
                }
            }

            for ((pkgName, sessionStartTimeUTC) in currentAppSessions) {
                if (endOfDayUTC > sessionStartTimeUTC) {
                    processAndAggregateSession(pkgName, sessionStartTimeUTC, endOfDayUTC, appUsageAggregator)
                }
            }
            currentAppSessions.clear()

            var recordsProcessed = 0
            if (appUsageAggregator.isNotEmpty()) {
                val dailyRecordsToInsert = mutableListOf<DailyAppUsageRecord>()
                for ((key, totalDuration) in appUsageAggregator) {
                    val (pkg, dateStr) = key // dateStr should be historicalDateLocalString
                    if (totalDuration > 0) {
                        dailyRecordsToInsert.add(DailyAppUsageRecord(
                            packageName = pkg,
                            dateString = dateStr, // Ensure this uses historicalDateLocalString
                            usageTimeMillis = totalDuration,
                            lastUpdatedTimestamp = DateUtil.getUtcTimestamp()
                        ))
                    }
                }
                if (dailyRecordsToInsert.isNotEmpty()){
                    try {
                        dailyAppUsageDao.insertAllUsage(dailyRecordsToInsert)
                        recordsProcessed = dailyRecordsToInsert.size
                    } catch (e: Exception) {
                        Log.e(TAG_REPO, "Error inserting aggregated records for $historicalDateLocalString", e)
                        overallSuccess = false
                    }
                }
            }
            Log.i(TAG_REPO, "Processed $recordsProcessed usage records for historical date $historicalDateLocalString.")
        } // End loop for days

        Log.i(TAG_REPO, "Historical app usage data backfill completed. Overall success: $overallSuccess")
        return@withContext overallSuccess
    }

    // --- Methods for App Detail Screen Chart Data ---
    override suspend fun getUsageForPackageAndDates(packageName: String, dateStrings: List<String>): List<DailyAppUsageRecord> {
        return dailyAppUsageDao.getUsageForPackageAndDates(packageName, dateStrings)
    }

    override suspend fun getAggregatedScrollForPackageAndDates(packageName: String, dateStrings: List<String>): List<AppScrollDataPerDate> {
        return scrollSessionDao.getAggregatedScrollForPackageAndDates(packageName, dateStrings)
    }
}

