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
        val usageStatsManager =
            application.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: run {
                    Log.e(TAG_REPO, "UsageStatsManager not available for today's update.")
                    return false
                }

        Log.d(TAG_REPO, "Starting update for today's app usage stats using event-based processing.")

        // Define the query range: from the start of yesterday to the end of today (local time, converted to UTC)
        // This ensures we capture sessions that started yesterday and ended today.
        val todayLocalString = DateUtil.getCurrentLocalDateString()
        val yesterdayCalendar = Calendar.getInstance().apply {
            time = DateUtil.parseLocalDateString(todayLocalString) ?: Date() // Fallback to now if parsing fails
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val yesterdayLocalString = DateUtil.formatDateToYyyyMmDdString(yesterdayCalendar.time) // Changed from formatDate

        val queryStartTimeUTC = DateUtil.getStartOfDayUtcMillis(yesterdayLocalString)
        val queryEndTimeUTC = DateUtil.getEndOfDayUtcMillis(todayLocalString)

        if (queryStartTimeUTC >= queryEndTimeUTC) {
            Log.e(TAG_REPO, "Invalid query time range for usage stats update. Start: $queryStartTimeUTC, End: $queryEndTimeUTC")
            return false
        }

        val appUsageAggregator = mutableMapOf<Pair<String, String>, Long>() // Key: (packageName, localDateString), Value: totalMillis

        try {
            val usageEvents = usageStatsManager.queryEvents(queryStartTimeUTC, queryEndTimeUTC)
            if (usageEvents == null) {
                Log.w(TAG_REPO, "queryEvents returned null. No usage events to process.")
                // Attempt to insert/update based on whatever might be in appUsageAggregator (likely empty)
                // or decide if this is a hard failure. For now, proceed to save (empty) records.
            }

            val currentAppSessions = mutableMapOf<String, Long>() // packageName to sessionStartTimeUTC
            val event = UsageEvents.Event()

            while (usageEvents != null && usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                val eventTimestampUTC = event.timeStamp
                val packageName = event.packageName ?: continue // Skip if package name is null

                if (packageName == application.packageName) continue // Skip our own app's usage

                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        // If there was a previous foreground event for this app without a background, finalize it first.
                        // This can happen if events are missed or service restarted. Treat current event time as end.
                        if (currentAppSessions.containsKey(packageName)) {
                            val sessionStartTimeUTC = currentAppSessions[packageName]!!
                            // Use eventTimestampUTC - 1 to avoid zero duration if events are immediate
                            processAndAggregateSession(packageName, sessionStartTimeUTC, eventTimestampUTC - 1, appUsageAggregator)
                        }
                        currentAppSessions[packageName] = eventTimestampUTC
                    }
                    UsageEvents.Event.ACTIVITY_STOPPED,
                    UsageEvents.Event.ACTIVITY_PAUSED -> { // Also consider paused as end of focused usage
                        if (currentAppSessions.containsKey(packageName)) {
                            val sessionStartTimeUTC = currentAppSessions.remove(packageName)!!
                            // Ensure session end is not before start; use start time if it is (0 duration)
                            val sessionEndTimeUTC = max(eventTimestampUTC, sessionStartTimeUTC)
                            processAndAggregateSession(packageName, sessionStartTimeUTC, sessionEndTimeUTC, appUsageAggregator)
                        }
                    }
                    // Handling for other events if necessary (e.g., configuration changes, screen off might interrupt sessions)
                     UsageEvents.Event.SCREEN_INTERACTIVE -> { // User interacting with screen
                        // This might indicate the start of general phone usage, but not specific app session start.
                        // Could be used for phone unlock tracking later.
                    }
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> { // Screen off
                        // Finalize all currently open sessions when screen goes off
                        val screenOffTimeUTC = eventTimestampUTC
                        currentAppSessions.keys.toList().forEach { pkg -> // Iterate over copy of keys
                            val sessionStartTimeUTC = currentAppSessions.remove(pkg)!!
                            val sessionEndTimeUTC = max(screenOffTimeUTC, sessionStartTimeUTC)
                            processAndAggregateSession(pkg, sessionStartTimeUTC, sessionEndTimeUTC, appUsageAggregator)
                        }
                    }
                }
            }

            // After iterating through all events, some sessions might still be "open"
            // (e.g., app was in foreground when queryEvents range ended).
            // Finalize them using queryEndTimeUTC as their end time.
            val endOfQueryRangeTimestamp = queryEndTimeUTC
            currentAppSessions.keys.toList().forEach { pkg ->
                val sessionStartTimeUTC = currentAppSessions.remove(pkg)!!
                val sessionEndTimeUTC = max(endOfQueryRangeTimestamp, sessionStartTimeUTC)
                processAndAggregateSession(pkg, sessionStartTimeUTC, sessionEndTimeUTC, appUsageAggregator)
            }


        } catch (e: Exception) {
            Log.e(TAG_REPO, "Error fetching or processing usage events for today's update.", e)
            return false // Indicate failure
        }

        val recordsToInsert = appUsageAggregator.mapNotNull { (key, totalMillis) ->
            val (packageName, localDateString) = key
            if (totalMillis > 0) { // Only insert if there's actual usage time
                // Filter to only include today's records for "updateTodayAppUsageStats"
                // The query range included yesterday to catch midnight-spanning sessions.
                if (localDateString == todayLocalString) {
                     DailyAppUsageRecord(
                        packageName = packageName,
                        dateString = localDateString,
                        usageTimeMillis = totalMillis,
                        lastUpdatedTimestamp = DateUtil.getUtcTimestamp() // Use consistent UTC timestamp
                    )
                } else {
                    null // Will be filtered out by mapNotNull
                }
            } else {
                null
            }
        }

        if (recordsToInsert.isNotEmpty()) {
            try {
                dailyAppUsageDao.insertAllUsage(recordsToInsert) // Uses OnConflictStrategy.REPLACE
                Log.i(TAG_REPO, "Successfully inserted/updated ${recordsToInsert.size} usage records for today ($todayLocalString) using event-based processing.")
            } catch (e: Exception) {
                Log.e(TAG_REPO, "Error inserting today's usage records into database for $todayLocalString.", e)
                return false // Indicate failure
            }
        } else {
            Log.i(TAG_REPO, "No new usage records to insert for today ($todayLocalString) after event processing.")
        }
        return true // Indicate success
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

        val totalSessionDuration = sessionEndTimeUTC - sessionStartTimeUTC
        // Log.d(TAG_REPO, "Processing session for $packageName: Start: $sessionStartTimeUTC, End: $sessionEndTimeUTC, Duration: $totalSessionDuration")


        var currentProcessingTimeUTC = sessionStartTimeUTC

        while (currentProcessingTimeUTC < sessionEndTimeUTC) {
            val currentLocalDateString = DateUtil.formatUtcTimestampToLocalDateString(currentProcessingTimeUTC)
            val endOfCurrentLocalDateUtc = DateUtil.getEndOfDayUtcMillis(currentLocalDateString)

            // Determine the end of this segment: either the end of the current local day or the actual session end, whichever comes first.
            val segmentEndTimeUTC = min(endOfCurrentLocalDateUtc, sessionEndTimeUTC)

            // Duration for this segment
            val segmentDuration = (segmentEndTimeUTC - currentProcessingTimeUTC + 1).coerceAtLeast(0L)

            if (segmentDuration > 0) {
                val key = Pair(packageName, currentLocalDateString)
                aggregator[key] = (aggregator[key] ?: 0L) + segmentDuration
                // Log.d(TAG_REPO, "  -> Added to $currentLocalDateString for $packageName: $segmentDuration ms. New total for day: ${aggregator[key]}")
            }
            // Move to the start of the next segment (which is the millisecond after the current segment ended)
            currentProcessingTimeUTC = segmentEndTimeUTC + 1
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
