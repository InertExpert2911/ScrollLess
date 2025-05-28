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

    companion object {
        private const val CONFIG_CHANGE_PEEK_AHEAD_MS = 1000L // Time to look ahead for config change after a pause
        private const val CONFIG_CHANGE_MERGE_THRESHOLD_MS = 3000L // Time within which a resume merges a transient config-change pause
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
            Log.d(TAG_REPO, "Attempting to fetch stored raw events for $todayLocalString from $startOfDayUTC to $endOfDayUTC")
            val events = rawAppEventDao.getEventsForPeriod(startOfDayUTC, endOfDayUTC) // Already sorts by timestamp ASC
            Log.d(TAG_REPO, "Successfully fetched ${events.size} stored raw events for $todayLocalString")
            events
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.e(TAG_REPO, "Fetching stored raw events for $todayLocalString was CANCELLED", e)
            throw e // Re-throw cancellation to ensure the job is properly cancelled
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
        val potentiallyTransientPause = mutableMapOf<String, Long>() // packageName -> timestamp of a pause that might be due to config change
        val lastEventTypeForApp = mutableMapOf<String, Int>() // packageName -> last processed RawAppEvent.EVENT_TYPE_ for this app

        storedRawEventsForToday.forEachIndexed { index, rawEvent ->
            val packageName = rawEvent.packageName
            val eventTimestampUTC = rawEvent.eventTimestamp
            val internalEventType = rawEvent.eventType
            val className = rawEvent.className // Needed for more precise config change check

            // Filter out self-package events, except screen events
            if (packageName == application.packageName && internalEventType != RawAppEvent.EVENT_TYPE_SCREEN_INTERACTIVE && internalEventType != RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE) {
                lastEventTypeForApp[packageName] = internalEventType // Still record it happened
                return@forEachIndexed // Skip processing for aggregation
            }

            when (internalEventType) {
                RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED -> {
                    Log.d(TAG_REPO, "EVENT: RESUMED $packageName at $eventTimestampUTC")
                    // Implicitly pause any other app that was considered active
                    currentAppSessions.keys.filter { it != packageName }.forEach { otherPkg ->
                        val otherAppSessionStart = currentAppSessions.remove(otherPkg)
                        if (otherAppSessionStart != null && eventTimestampUTC -1 > otherAppSessionStart) {
                            Log.d(TAG_REPO, "Implicitly PAUSING $otherPkg due to $packageName RESUME. Session: $otherAppSessionStart -> ${eventTimestampUTC -1}")
                            processAndAggregateSession(otherPkg, otherAppSessionStart, eventTimestampUTC - 1, appUsageAggregator)
                        }
                        potentiallyTransientPause.remove(otherPkg) // Clear any transient state for the other app
                    }

                    val transientPauseTime = potentiallyTransientPause.remove(packageName)
                    if (transientPauseTime != null && (eventTimestampUTC - transientPauseTime < CONFIG_CHANGE_MERGE_THRESHOLD_MS)) {
                        // This RESUME is a continuation of a session after a transient config-change pause
                        // The session is already in currentAppSessions with its original start time.
                        Log.d(TAG_REPO, "RESUMED $packageName ($className): Continuing session after transient pause. Original start: ${currentAppSessions[packageName]}, Pause was at $transientPauseTime, Resumed at $eventTimestampUTC")
                        // No need to call processAndAggregateSession here, the session continues.
                    } else {
                        // Standard RESUME or resume after non-transient pause / long gap
                        val existingSessionStartTime = currentAppSessions[packageName]
                        if (existingSessionStartTime != null) {
                            // App was already active (e.g. re-focus, or previous segment ended by screen off and now it's back)
                            // If it's a new segment, finalize the old one.
                            // However, if it's truly the same session (e.g. screen on after screen off), this check is tricky.
                            // For now, if it's already there, we assume the screen-off logic handled the previous segment.
                            // A simple re-focus would also mean its session might have been implicitly paused by another app, then resumed.
                            // The critical part is that a *new* RESUME for an app already in currentAppSessions implies its previous active period ended.
                            // Let's refine: if it's in currentAppSessions, it means its session was ongoing *or* just restored (e.g. after screen on).
                            // If this resume is significantly later, it should start a new segment. But our implicit pause logic above handles this.
                            Log.v(TAG_REPO, "RESUMED $packageName ($className): Already in current sessions (started at $existingSessionStartTime). New active period from $eventTimestampUTC.")
                            // If there was an existing session, and this resume is truly new (not a merge), the implicit pause logic above *should* have handled it.
                            // The only case left is if it's the *same app* resuming after no other app interrupted it, meaning this is a continuation perhaps after a brief backgrounding without pause event or screen on.
                            // For safety, if it's still in currentAppSessions, its old segment should have been closed by an intervening event (pause, screen_off, or another app's resume).
                            // We only update the start time if this is effectively a *new* start, not a merge.
                            if (transientPauseTime == null) { // Only treat as a new segment start if not merging
                                if (eventTimestampUTC > existingSessionStartTime) {
                                    // This implies the app was running, something happened (e.g. screen off), and now it's back.
                                    // The screen off should have closed the previous segment. This resume starts a new one.
                                     Log.v(TAG_REPO, "Finalizing previous segment for $packageName ($className) from $existingSessionStartTime to ${eventTimestampUTC -1} before new resume.")
                                     processAndAggregateSession(packageName, existingSessionStartTime, eventTimestampUTC -1, appUsageAggregator)
                                }
                            }
                        }
                        currentAppSessions[packageName] = eventTimestampUTC
                        Log.d(TAG_REPO, "RESUMED $packageName ($className): New session started at $eventTimestampUTC.")
                    }
                }
                RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED -> {
                    Log.d(TAG_REPO, "EVENT: PAUSED $packageName at $eventTimestampUTC")
                    var isTransientConfigChangePause = false
                    val nextEvent = if (index + 1 < storedRawEventsForToday.size) storedRawEventsForToday[index + 1] else null
                    val eventAfterNext = if (index + 2 < storedRawEventsForToday.size) storedRawEventsForToday[index + 2] else null

                    // Revised logic:
                    // 1. Check if the next event is a CONFIG_CHANGE within the PEEK_AHEAD window.
                    //    The CONFIG_CHANGE event itself might come from the "android" package.
                    // 2. Then, check if the event *after* the CONFIG_CHANGE is an ACTIVITY_RESUMED
                    //    for the *original app (packageName, className)*, also within a PEEK_AHEAD window.
                    if (nextEvent != null &&
                        nextEvent.eventType == RawAppEvent.EVENT_TYPE_CONFIGURATION_CHANGE && // Check type first
                        (nextEvent.eventTimestamp - eventTimestampUTC < CONFIG_CHANGE_PEEK_AHEAD_MS)) { // Check timing of CONFIG_CHANGE

                        // Now, ensure the event *after* the CONFIG_CHANGE is a RESUME for the *original app and class*
                        if (eventAfterNext != null &&
                            eventAfterNext.packageName == packageName && // Original app's package
                            eventAfterNext.className == className &&   // Original app's class
                            eventAfterNext.eventType == RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED &&
                            (eventAfterNext.eventTimestamp - nextEvent.eventTimestamp < CONFIG_CHANGE_PEEK_AHEAD_MS)) { // Timing from CONFIG_CHANGE to RESUME
                            
                            isTransientConfigChangePause = true
                        }
                    }

                    if (isTransientConfigChangePause) {
                        Log.d(TAG_REPO, "PAUSED $packageName ($className): Detected as POTENTIALLY TRANSIENT (due to upcoming config change and resume). Storing pause time $eventTimestampUTC. Session continues.")
                        potentiallyTransientPause[packageName] = eventTimestampUTC
                        // DO NOT remove from currentAppSessions or process session yet.
                    } else {
                        val sessionStartTimeFromMap = currentAppSessions.remove(packageName)
                        if (sessionStartTimeFromMap != null) {
                            if (eventTimestampUTC > sessionStartTimeFromMap) {
                                Log.d(TAG_REPO, "PAUSED $packageName ($className): Standard pause. Session: $sessionStartTimeFromMap -> $eventTimestampUTC.")
                                processAndAggregateSession(packageName, sessionStartTimeFromMap, eventTimestampUTC, appUsageAggregator)
                            } else {
                                Log.w(TAG_REPO, "PAUSED $packageName ($className): End time $eventTimestampUTC <= start time $sessionStartTimeFromMap. Skipping.")
                            }
                        }
                        potentiallyTransientPause.remove(packageName) // Clear any prior transient state
                    }
                }
                RawAppEvent.EVENT_TYPE_ACTIVITY_STOPPED -> {
                    Log.d(TAG_REPO, "EVENT: STOPPED $packageName at $eventTimestampUTC")
                    val sessionStartTimeFromMap = currentAppSessions.remove(packageName)
                    if (sessionStartTimeFromMap != null) {
                        if (eventTimestampUTC > sessionStartTimeFromMap) {
                            Log.d(TAG_REPO, "STOPPED $packageName ($className): Session: $sessionStartTimeFromMap -> $eventTimestampUTC.")
                            processAndAggregateSession(packageName, sessionStartTimeFromMap, eventTimestampUTC, appUsageAggregator)
                        } else {
                             Log.w(TAG_REPO, "STOPPED $packageName ($className): End time $eventTimestampUTC <= start time $sessionStartTimeFromMap. Skipping.")
                        }
                    }
                    potentiallyTransientPause.remove(packageName) // Clear any transient state definitively
                }
                 RawAppEvent.EVENT_TYPE_CONFIGURATION_CHANGE -> {
                    Log.d(TAG_REPO, "EVENT: CONFIG_CHANGE $packageName ($className) at $eventTimestampUTC. Handled by PAUSE/RESUME logic.")
                    // This event itself doesn't directly alter sessions; its impact is via PAUSE/RESUME lookaheads.
                }
                RawAppEvent.EVENT_TYPE_USER_INTERACTION -> {
                    Log.d(TAG_REPO, "EVENT: USER_INTERACTION $packageName at $eventTimestampUTC")
                    if (!currentAppSessions.containsKey(packageName) && !potentiallyTransientPause.containsKey(packageName)) {
                        // If no active session and not in a transient pause, this interaction might imply a start.
                        // However, a RESUME event is more definitive. For now, this mainly serves as an activity indicator.
                        // Let's not start a session here to avoid conflicts if a RESUME is imminent.
                        Log.v(TAG_REPO, "USER_INTERACTION for $packageName: Not currently in an active or transient session. No session started by this event alone.")
                    }
                }
                RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE -> {
                    Log.d(TAG_REPO, "EVENT: SCREEN_NON_INTERACTIVE at $eventTimestampUTC")
                    val appsToFinalize = currentAppSessions.toMap() // Iterate over a copy
                    for ((pkg, startTime) in appsToFinalize) {
                        if (eventTimestampUTC > startTime) {
                            Log.d(TAG_REPO, "SCREEN_NON_INTERACTIVE: Finalizing session for $pkg. $startTime -> $eventTimestampUTC")
                            processAndAggregateSession(pkg, startTime, eventTimestampUTC, appUsageAggregator)
                        }
                        currentAppSessions.remove(pkg)
                        potentiallyTransientPause.remove(pkg) // Screen off clears transient states
                    }
                }
                RawAppEvent.EVENT_TYPE_SCREEN_INTERACTIVE -> {
                     Log.d(TAG_REPO, "EVENT: SCREEN_INTERACTIVE at $eventTimestampUTC. No direct session change; RESUME events will follow for active app.")
                }
                // Other event types (KEYGUARD_SHOWN/HIDDEN, etc.) can be logged but might not directly alter app sessions here
                // unless explicitly needed. SCREEN_NON_INTERACTIVE is the main one for session termination.
            }
            lastEventTypeForApp[packageName] = internalEventType
        } // End of forEachIndexed loop

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
                Log.d(TAG_REPO, "Attempting to fetch stored raw events for $historicalDateLocalString from $startOfDayUTC to $endOfDayUTC (Backfill)")
                val events = rawAppEventDao.getEventsForPeriod(startOfDayUTC, endOfDayUTC)
                Log.d(TAG_REPO, "Successfully fetched ${events.size} stored raw events for $historicalDateLocalString (Backfill)")
                events
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.e(TAG_REPO, "Fetching stored raw events for $historicalDateLocalString (Backfill) was CANCELLED", e)
                throw e // Re-throw cancellation
            } catch (e: Exception) {
                Log.e(TAG_REPO, "Error fetching stored raw events for $historicalDateLocalString (Backfill)", e)
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
            val potentiallyTransientPause = mutableMapOf<String, Long>() // packageName -> timestamp of a pause that might be due to config change
            val lastEventTypeForApp = mutableMapOf<String, Int>() // packageName -> last processed RawAppEvent.EVENT_TYPE_ for this app

            storedRawEventsForDay.forEachIndexed { index, rawEvent ->
                val packageName = rawEvent.packageName
                val eventTimestampUTC = rawEvent.eventTimestamp
                val internalEventType = rawEvent.eventType
                val className = rawEvent.className // Needed for more precise config change check

                // Filter out self-package events, except screen events
                if (packageName == application.packageName && internalEventType != RawAppEvent.EVENT_TYPE_SCREEN_INTERACTIVE && internalEventType != RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE) {
                    lastEventTypeForApp[packageName] = internalEventType // Still record it happened
                    return@forEachIndexed // Skip processing for aggregation
                }

                when (internalEventType) {
                    RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED -> {
                        Log.d(TAG_REPO, "EVENT: RESUMED $packageName at $eventTimestampUTC (Backfill for $historicalDateLocalString)")
                        currentAppSessions.keys.filter { it != packageName }.forEach { otherPkg ->
                            val otherAppSessionStart = currentAppSessions.remove(otherPkg)
                            if (otherAppSessionStart != null && eventTimestampUTC -1 > otherAppSessionStart) {
                                Log.d(TAG_REPO, "Implicitly PAUSING $otherPkg due to $packageName RESUME (Backfill for $historicalDateLocalString). Session: $otherAppSessionStart -> ${'$'}{eventTimestampUTC -1}")
                                processAndAggregateSession(otherPkg, otherAppSessionStart, eventTimestampUTC - 1, appUsageAggregator)
                            }
                            potentiallyTransientPause.remove(otherPkg)
                        }

                        val transientPauseTime = potentiallyTransientPause.remove(packageName)
                        if (transientPauseTime != null && (eventTimestampUTC - transientPauseTime < CONFIG_CHANGE_MERGE_THRESHOLD_MS)) {
                            Log.d(TAG_REPO, "RESUMED $packageName ($className) (Backfill for $historicalDateLocalString): Continuing session after transient pause. Original start: ${'$'}{currentAppSessions[packageName]}, Pause was at $transientPauseTime, Resumed at $eventTimestampUTC")
                        } else {
                            val existingSessionStartTime = currentAppSessions[packageName]
                            if (existingSessionStartTime != null) {
                                if (transientPauseTime == null) {
                                    if (eventTimestampUTC > existingSessionStartTime) {
                                         Log.v(TAG_REPO, "Finalizing previous segment for $packageName ($className) (Backfill for $historicalDateLocalString) from $existingSessionStartTime to ${'$'}{eventTimestampUTC -1} before new resume.")
                                         processAndAggregateSession(packageName, existingSessionStartTime, eventTimestampUTC -1, appUsageAggregator)
                                    }
                                }
                            }
                            currentAppSessions[packageName] = eventTimestampUTC
                            Log.d(TAG_REPO, "RESUMED $packageName ($className) (Backfill for $historicalDateLocalString): New session started at $eventTimestampUTC.")
                        }
                    }
                    RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED -> {
                        Log.d(TAG_REPO, "EVENT: PAUSED $packageName at $eventTimestampUTC (Backfill for $historicalDateLocalString)")
                        var isTransientConfigChangePause = false
                        val nextEvent = if (index + 1 < storedRawEventsForDay.size) storedRawEventsForDay[index + 1] else null
                        val eventAfterNext = if (index + 2 < storedRawEventsForDay.size) storedRawEventsForDay[index + 2] else null

                        // Revised logic (applied to backfill loop as well):
                        if (nextEvent != null &&
                            nextEvent.eventType == RawAppEvent.EVENT_TYPE_CONFIGURATION_CHANGE &&
                            (nextEvent.eventTimestamp - eventTimestampUTC < CONFIG_CHANGE_PEEK_AHEAD_MS)) {
    
                            if (eventAfterNext != null &&
                                eventAfterNext.packageName == packageName &&
                                eventAfterNext.className == className &&
                                eventAfterNext.eventType == RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED &&
                                (eventAfterNext.eventTimestamp - nextEvent.eventTimestamp < CONFIG_CHANGE_PEEK_AHEAD_MS)) {
                                
                                isTransientConfigChangePause = true
                            }
                        }

                        if (isTransientConfigChangePause) {
                            Log.d(TAG_REPO, "PAUSED $packageName ($className) (Backfill for $historicalDateLocalString): Detected as POTENTIALLY TRANSIENT. Storing pause time $eventTimestampUTC. Session continues.")
                            potentiallyTransientPause[packageName] = eventTimestampUTC
                        } else {
                            val sessionStartTimeFromMap = currentAppSessions.remove(packageName)
                            if (sessionStartTimeFromMap != null) {
                                if (eventTimestampUTC > sessionStartTimeFromMap) {
                                    Log.d(TAG_REPO, "PAUSED $packageName ($className) (Backfill for $historicalDateLocalString): Standard pause. Session: $sessionStartTimeFromMap -> $eventTimestampUTC.")
                                    processAndAggregateSession(packageName, sessionStartTimeFromMap, eventTimestampUTC, appUsageAggregator)
                                } else {
                                    Log.w(TAG_REPO, "PAUSED $packageName ($className) (Backfill for $historicalDateLocalString): End time $eventTimestampUTC <= start $sessionStartTimeFromMap. Skipping.")
                                }
                            }
                            potentiallyTransientPause.remove(packageName)
                        }
                    }
                    RawAppEvent.EVENT_TYPE_ACTIVITY_STOPPED -> {
                        Log.d(TAG_REPO, "EVENT: STOPPED $packageName at $eventTimestampUTC (Backfill for $historicalDateLocalString)")
                        val sessionStartTimeFromMap = currentAppSessions.remove(packageName)
                        if (sessionStartTimeFromMap != null) {
                            if (eventTimestampUTC > sessionStartTimeFromMap) {
                                Log.d(TAG_REPO, "STOPPED $packageName ($className) (Backfill for $historicalDateLocalString): Session: $sessionStartTimeFromMap -> $eventTimestampUTC.")
                                processAndAggregateSession(packageName, sessionStartTimeFromMap, eventTimestampUTC, appUsageAggregator)
                            } else {
                                 Log.w(TAG_REPO, "STOPPED $packageName ($className) (Backfill for $historicalDateLocalString): End time $eventTimestampUTC <= start $sessionStartTimeFromMap. Skipping.")
                            }
                        }
                        potentiallyTransientPause.remove(packageName)
                    }
                     RawAppEvent.EVENT_TYPE_CONFIGURATION_CHANGE -> {
                        Log.d(TAG_REPO, "EVENT: CONFIG_CHANGE $packageName ($className) at $eventTimestampUTC (Backfill for $historicalDateLocalString). Handled by PAUSE/RESUME.")
                    }
                    RawAppEvent.EVENT_TYPE_USER_INTERACTION -> {
                        Log.d(TAG_REPO, "EVENT: USER_INTERACTION $packageName at $eventTimestampUTC (Backfill for $historicalDateLocalString)")
                        // Similar to updateToday, no direct session start here.
                    }
                    RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE -> {
                        Log.d(TAG_REPO, "EVENT: SCREEN_NON_INTERACTIVE at $eventTimestampUTC (Backfill for $historicalDateLocalString)")
                        val appsToFinalize = currentAppSessions.toMap()
                        for ((pkg, startTime) in appsToFinalize) {
                            if (eventTimestampUTC > startTime) {
                                Log.d(TAG_REPO, "SCREEN_NON_INTERACTIVE (Backfill for $historicalDateLocalString): Finalizing $pkg. $startTime -> $eventTimestampUTC")
                                processAndAggregateSession(pkg, startTime, eventTimestampUTC, appUsageAggregator)
                            }
                            currentAppSessions.remove(pkg)
                            potentiallyTransientPause.remove(pkg)
                        }
                    }
                    RawAppEvent.EVENT_TYPE_SCREEN_INTERACTIVE -> {
                         Log.d(TAG_REPO, "EVENT: SCREEN_INTERACTIVE at $eventTimestampUTC (Backfill for $historicalDateLocalString). No direct session change.")
                    }
                }
                lastEventTypeForApp[packageName] = internalEventType
            } // End of forEachIndexed loop for backfill

            // Finalize any sessions still marked as active at the end of the day (for backfill)
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


