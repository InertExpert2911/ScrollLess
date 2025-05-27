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

    override suspend fun updateTodayAppUsageStats(): Boolean {
        val application = application // Use the injected context
        val usageStatsManager =
            application.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: run {
                    Log.e(TAG_REPO, "UsageStatsManager not available.")
                    return false
                }

        val todayLocalString = DateUtil.getCurrentLocalDateString()
        val startOfDayUTC = DateUtil.getStartOfDayUtcMillis(todayLocalString)
        val endOfDayUTC = DateUtil.getEndOfDayUtcMillis(todayLocalString)

        // Clear any potentially stale aggregated data for today before processing new events
        // This is important if we are re-calculating from scratch
        // dailyAppUsageDao.deleteUsageForDate(todayLocalString) // Consider if this is needed or if insertOrUpdate is sufficient

        Log.d(TAG_REPO, "Starting update for today's app usage stats using event-based processing. Range: $startOfDayUTC to $endOfDayUTC")

        val events: UsageEvents? = try {
            withContext(Dispatchers.IO) {
                usageStatsManager.queryEvents(startOfDayUTC, endOfDayUTC)
            }
        } catch (e: Exception) {
            Log.e(TAG_REPO, "Error querying usage events", e)
            null // Return null on error
        }

        if (events == null) {
            Log.w(TAG_REPO, "UsageEvents object is null, cannot process events for today.")
            return false // Or handle as appropriate, e.g., return true if partial success is okay
        }

        val currentAppSessions = mutableMapOf<String, Long>() // Tracks start time of current foreground app
        val appUsageAggregator = mutableMapOf<Pair<String, String>, Long>() // Pair of (PackageName, LocalDateString) to DurationMillis

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val packageName = event.packageName ?: continue // Skip if no package name
            val eventTimestampUTC = event.timeStamp

            // Skip events outside our precise daily range if queryEvents is too broad (though it shouldn't be)
            if (eventTimestampUTC < startOfDayUTC || eventTimestampUTC > endOfDayUTC) {
                // Log.v(TAG_REPO, "Skipping event for $packageName outside today's range: $eventTimestampUTC")
                continue
            }

            // Filter out system/launcher UI or very short-lived events if necessary, though current approach is to capture all
            if (packageName == application.packageName && event.eventType != UsageEvents.Event.SCREEN_INTERACTIVE && event.eventType != UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
                // Log.v(TAG_REPO, "Skipping self-package event for ${application.packageName} of type ${event.eventType}")
                continue // Skip our own app's typical usage events, but keep screen events if we track them
            }


            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    // If there was a previous foreground event for this app without a background,
                    // finalize it using the current event's timestamp as the end time.
                    // This handles cases where a PAUSED event might have been missed or service restarted.
                    if (currentAppSessions.containsKey(packageName)) {
                        val previousSessionStartTimeUTC = currentAppSessions[packageName]!!
                        if (eventTimestampUTC > previousSessionStartTimeUTC) {
                            // Log.d(TAG_REPO, "RESUMED for $packageName: Found lingering session. Closing $previousSessionStartTimeUTC -> ${eventTimestampUTC -1}")
                            processAndAggregateSession(packageName, previousSessionStartTimeUTC, eventTimestampUTC - 1, appUsageAggregator)
                        }
                    }
                    currentAppSessions[packageName] = eventTimestampUTC
                    // Log.d(TAG_REPO, "ACTIVITY_RESUMED: $packageName at $eventTimestampUTC")
                }

                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val sessionStartTimeFromMap = currentAppSessions.remove(packageName)
                    if (sessionStartTimeFromMap != null) {
                        if (eventTimestampUTC > sessionStartTimeFromMap) {
                            processAndAggregateSession(packageName, sessionStartTimeFromMap, eventTimestampUTC, appUsageAggregator)
                            // Log.d(TAG_REPO, "ACTIVITY_PAUSED: $packageName. Session: $sessionStartTimeFromMap -> $eventTimestampUTC. Duration: ${eventTimestampUTC - sessionStartTimeFromMap}")
                        } else {
                            // Log.w(TAG_REPO, "ACTIVITY_PAUSED: $packageName. End time $eventTimestampUTC <= start time $sessionStartTimeFromMap. Skipping session.")
                        }
                    } else {
                        // No active session in currentAppSessions for this packageName.
                        // This means we didn't see its RESUME in *this current processing pass*.
                        // It could be an app that was already running when this processing started,
                        // or its RESUME event was not (yet) returned by queryEvents.
                        // Critically, DO NOT assume it started at startOfDayUTC, as that causes huge overcounting for newly opened apps.
                        Log.w(TAG_REPO, "ACTIVITY_PAUSED for $packageName at $eventTimestampUTC: No corresponding RESUME event found in current processing map. This specific segment will be ignored to prevent overcounting. It might be captured in a later refresh.")
                        // The old logic that tried to find a previous resume or defaulted to startOfDayUTC is removed here to prevent the "11hr bug".
                    }
                    // Log.d(TAG_REPO, "ACTIVITY_PAUSED: $packageName at $eventTimestampUTC (after processing map entry)")
                }

                UsageEvents.Event.USER_INTERACTION -> {
                    // If an app is interacted with, it implies it's in the foreground.
                    // If we don't have a session start time for it, record this as a potential start.
                    if (!currentAppSessions.containsKey(packageName)) {
                        // Log.d(TAG_REPO, "USER_INTERACTION for $packageName without active session. Marking as active from $eventTimestampUTC.")
                        currentAppSessions[packageName] = eventTimestampUTC
                    }
                }
                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    // Potentially useful for phone unlock tracking later.
                    // Log.d(TAG_REPO, "SCREEN_INTERACTIVE at $eventTimestampUTC")
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    // Potentially useful for phone lock tracking later.
                    // Log.d(TAG_REPO, "SCREEN_NON_INTERACTIVE at $eventTimestampUTC")
                    // When screen locks, all apps effectively lose foreground status.
                    // Finalize all open sessions.
                    val appsToFinalize = currentAppSessions.toMap() // Iterate over a copy
                    for ((pkg, startTime) in appsToFinalize) {
                        if (eventTimestampUTC > startTime) {
                            // Log.d(TAG_REPO, "SCREEN_NON_INTERACTIVE: Finalizing session for $pkg. $startTime -> $eventTimestampUTC")
                            processAndAggregateSession(pkg, startTime, eventTimestampUTC, appUsageAggregator)
                        }
                        currentAppSessions.remove(pkg) // Remove from active map
                    }
                }
                // Consider other event types like CONFIGURATION_CHANGE if they affect session continuity
                // UsageEvents.Event.CONFIGURATION_CHANGE -> { Log.d(TAG_REPO, "CONFIGURATION_CHANGE for $packageName") }
                else -> {
                    // Log.v(TAG_REPO, "Unhandled event type ${event.eventType} for $packageName")
                }
            }
        }

        // Finalize any sessions that are still marked as active at the end of the event processing
        // (e.g., app is still in foreground at the time of query or end of day)
        val currentTimeForFinalization = DateUtil.getUtcTimestamp() // Current time for finalization
        for ((pkgName, sessionStartTimeUTC) in currentAppSessions) {
            // Ensure finalization does not exceed endOfDayUTC for today's processing
            val sessionEndTimeCandidate = min(currentTimeForFinalization, endOfDayUTC)
            if (sessionEndTimeCandidate > sessionStartTimeUTC) {
                // Log.d(TAG_REPO, "Finalizing lingering open session for $pkgName at query end. Start: $sessionStartTimeUTC, End: $sessionEndTimeCandidate")
                processAndAggregateSession(pkgName, sessionStartTimeUTC, sessionEndTimeCandidate, appUsageAggregator)
            }
        }
        currentAppSessions.clear() // Clear after finalization

        // Persist all aggregated usages
        var recordsProcessed = 0
        if (appUsageAggregator.isNotEmpty()) {
            Log.d(TAG_REPO, "Aggregated Usage for today ($todayLocalString):")
            for ((key, totalDuration) in appUsageAggregator) {
                val (pkg, dateStr) = key
                // Log.d(TAG_REPO, "- $pkg on $dateStr: ${totalDuration / 1000}s")
                if (totalDuration > 0) { // Only save if there's actual usage
                    val record = DailyAppUsageRecord(
                        packageName = pkg,
                        dateString = dateStr,
                        usageTimeMillis = totalDuration,
                        lastUpdatedTimestamp = DateUtil.getUtcTimestamp() // Add this field to your entity if you want to track updates
                    )
                    dailyAppUsageDao.insertOrUpdateUsage(record) // Assumes this handles conflicts by summing or replacing
                    recordsProcessed++
                }
            }
        }
        Log.i(TAG_REPO, "Successfully inserted/updated $recordsProcessed usage records for today ($todayLocalString) using event-based processing.")
        return true
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

    override suspend fun backfillHistoricalAppUsageData(numberOfDays: Int): Boolean {
        val usageStatsManager =
            application.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: run {
                    Log.e(TAG_REPO, "UsageStatsManager not available for backfill.")
                    return false
                }

        Log.i(TAG_REPO, "Starting historical app usage data backfill for the last $numberOfDays days using event-based processing.")
        
        val appUsageAggregator = mutableMapOf<Pair<String, String>, Long>() // Key: (packageName, localDateString), Value: totalMillis
        val allDateStringsProcessed = mutableSetOf<String>()


        // Determine the overall query range for events
        val todayLocalString = DateUtil.getCurrentLocalDateString()
        val endQueryDateCalendar = Calendar.getInstance().apply {
             time = DateUtil.parseLocalDateString(todayLocalString) ?: Date()
        }
        val overallQueryEndTimeUTC = DateUtil.getEndOfDayUtcMillis(DateUtil.formatDateToYyyyMmDdString(endQueryDateCalendar.time)) // Changed from formatDate

        val startQueryDateCalendar = Calendar.getInstance().apply {
            time = DateUtil.parseLocalDateString(todayLocalString) ?: Date()
            add(Calendar.DAY_OF_YEAR, -numberOfDays) // Go back N days for the start
        }
        val overallQueryStartTimeUTC = DateUtil.getStartOfDayUtcMillis(DateUtil.formatDateToYyyyMmDdString(startQueryDateCalendar.time)) // Changed from formatDate

        if (overallQueryStartTimeUTC >= overallQueryEndTimeUTC) {
            Log.e(TAG_REPO, "Invalid query time range for historical backfill. Start: $overallQueryStartTimeUTC, End: $overallQueryEndTimeUTC")
            return false
        }
        
        Log.d(TAG_REPO, "Historical backfill query range: From ${DateUtil.formatUtcTimestampToLocalDateString(overallQueryStartTimeUTC)} to ${DateUtil.formatUtcTimestampToLocalDateString(overallQueryEndTimeUTC)}")

        try {
            val usageEvents = usageStatsManager.queryEvents(overallQueryStartTimeUTC, overallQueryEndTimeUTC)
             if (usageEvents == null) {
                Log.w(TAG_REPO, "queryEvents returned null for historical backfill. No usage events to process.")
            }

            val currentAppSessions = mutableMapOf<String, Long>() // packageName to sessionStartTimeUTC
            val event = UsageEvents.Event()

            while (usageEvents != null && usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                val eventTimestampUTC = event.timeStamp
                val packageName = event.packageName ?: continue
                 if (packageName == application.packageName) continue // Skip our own app

                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        if (currentAppSessions.containsKey(packageName)) {
                            val sessionStartTimeUTC = currentAppSessions[packageName]!!
                            processAndAggregateSession(packageName, sessionStartTimeUTC, eventTimestampUTC - 1, appUsageAggregator)
                        }
                        currentAppSessions[packageName] = eventTimestampUTC
                    }
                    UsageEvents.Event.ACTIVITY_STOPPED,
                    UsageEvents.Event.ACTIVITY_PAUSED -> {
                        if (currentAppSessions.containsKey(packageName)) {
                            val sessionStartTimeUTC = currentAppSessions.remove(packageName)!!
                            val sessionEndTimeUTC = max(eventTimestampUTC, sessionStartTimeUTC)
                            processAndAggregateSession(packageName, sessionStartTimeUTC, sessionEndTimeUTC, appUsageAggregator)
                        }
                    }
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> { // Screen off
                        val screenOffTimeUTC = eventTimestampUTC
                        currentAppSessions.keys.toList().forEach { pkg ->
                            val sessionStartTimeUTC = currentAppSessions.remove(pkg)!!
                             val sessionEndTimeUTC = max(screenOffTimeUTC, sessionStartTimeUTC)
                            processAndAggregateSession(pkg, sessionStartTimeUTC, sessionEndTimeUTC, appUsageAggregator)
                        }
                    }
                }
            }
            
            // Finalize any sessions still open at the end of the query range
            val endOfQueryRangeTimestamp = overallQueryEndTimeUTC
            currentAppSessions.keys.toList().forEach { pkg ->
                val sessionStartTimeUTC = currentAppSessions.remove(pkg)!!
                 val sessionEndTimeUTC = max(endOfQueryRangeTimestamp, sessionStartTimeUTC)
                processAndAggregateSession(pkg, sessionStartTimeUTC, sessionEndTimeUTC, appUsageAggregator)
            }

        } catch (e: Exception) {
            Log.e(TAG_REPO, "Error fetching or processing usage events during historical backfill.", e)
            // Decide if we should return false or try to save partial data. For now, return false.
            return false
        }
        
        // Collect all local date strings that had any app usage aggregated
        appUsageAggregator.keys.forEach { allDateStringsProcessed.add(it.second) }


        // The old loop for days is no longer the primary driver for fetching,
        // but we can use it to ensure we create zero-usage entries if needed, or simply rely on the aggregator.
        // For now, the aggregator drives what's inserted.

        val recordsToInsert = appUsageAggregator.mapNotNull { (key, totalMillis) ->
            val (packageName, localDateString) = key
            if (totalMillis > 0) { // Only insert if there's actual usage time
                DailyAppUsageRecord(
                    packageName = packageName,
                    dateString = localDateString,
                    usageTimeMillis = totalMillis,
                    lastUpdatedTimestamp = DateUtil.getUtcTimestamp()
                )
            } else {
                null
            }
        }

        if (recordsToInsert.isNotEmpty()) {
            try {
                dailyAppUsageDao.insertAllUsage(recordsToInsert) // Uses OnConflictStrategy.REPLACE
                Log.i(TAG_REPO, "Successfully inserted/updated ${recordsToInsert.size} historical usage records from event-based backfill covering dates: ${allDateStringsProcessed.joinToString()}.")
            } catch (e: Exception) {
                Log.e(TAG_REPO, "Error inserting historical usage records into database from event-based backfill.", e)
                return false
            }
        } else {
            Log.i(TAG_REPO, "No new historical usage records to insert from event-based backfill.")
        }
        return true
    }

    // --- Methods for App Detail Screen Chart Data ---
    override suspend fun getUsageForPackageAndDates(packageName: String, dateStrings: List<String>): List<DailyAppUsageRecord> {
        return dailyAppUsageDao.getUsageForPackageAndDates(packageName, dateStrings)
    }

    override suspend fun getAggregatedScrollForPackageAndDates(packageName: String, dateStrings: List<String>): List<AppScrollDataPerDate> {
        return scrollSessionDao.getAggregatedScrollForPackageAndDates(packageName, dateStrings)
    }
}

