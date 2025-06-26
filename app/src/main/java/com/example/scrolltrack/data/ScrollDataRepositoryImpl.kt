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

    override fun getDailyUsageRecordsForDate(dateString: String): Flow<List<DailyAppUsageRecord>> {
        return dailyAppUsageDao.getUsageForDate(dateString)
    }

    override fun getUsageRecordsForDateRange(startDateString: String, endDateString: String): Flow<List<DailyAppUsageRecord>> {
        return dailyAppUsageDao.getUsageRecordsForDateRange(startDateString, endDateString)
    }
    
    override suspend fun insertScrollSession(session: ScrollSessionRecord) {
        scrollSessionDao.insertSession(session)
    }

    @Suppress("DEPRECATION")
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
            else -> RawAppEvent.EVENT_TYPE_UNKNOWN
        }
    }

    @Suppress("DEPRECATION")
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

        val todayDateString = DateUtil.getCurrentLocalDateString()
        val startOfDayUTC = DateUtil.getStartOfDayUtcMillis(todayDateString)
        val endOfTodayUTC = System.currentTimeMillis() // Query up to the present moment for today's data

        try {
            val usageStatsList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDayUTC, endOfTodayUTC)

            // Even if the list is null or empty, we must clear out any old data for today
            // to ensure the UI reflects that there is now no data.
            dailyAppUsageDao.deleteUsageForDate(todayDateString)

            if (usageStatsList.isNullOrEmpty()) {
                Log.d(TAG_REPO, "No usage stats returned from system for today ($todayDateString). Previous data for today cleared.")
                return@withContext true
            }

            val relevantStats = usageStatsList.filter {
                !isFilteredPackage(it.packageName) && it.totalTimeInForeground > 0
            }

            if (relevantStats.isEmpty()) {
                Log.d(TAG_REPO, "No relevant app usage found for today ($todayDateString) after filtering.")
                return@withContext true
            }

            val recordsToInsert = relevantStats.map { stat ->
                DailyAppUsageRecord(
                    packageName = stat.packageName,
                    dateString = todayDateString,
                    usageTimeMillis = stat.totalTimeInForeground,
                    activeTimeMillis = 0L, // Active time calculation is not supported by this simpler, more robust method
                    lastUpdatedTimestamp = System.currentTimeMillis()
                )
            }

            // The delete operation was moved to the top to handle all cases
            dailyAppUsageDao.insertAllUsage(recordsToInsert)
            Log.i(TAG_REPO, "Successfully updated ${recordsToInsert.size} usage records for today ($todayDateString).")

        } catch (e: Exception) {
            Log.e(TAG_REPO, "Error during today's usage update for $todayDateString: ${e.message}", e)
            return@withContext false
        }
        
        return@withContext true
    }

    private fun calculateActiveTimeFromInteractions(interactionTimestamps: List<Long>, sessionStartTime: Long, sessionEndTime: Long): Long {
        if (interactionTimestamps.isEmpty()) return 0L

        val intervals = interactionTimestamps.map { it to it + ACTIVE_TIME_INTERACTION_WINDOW_MS }.sortedBy { it.first }
        val merged = mutableListOf<Pair<Long, Long>>()
        
        intervals.firstOrNull()?.let { merged.add(it) } ?: return 0L

        for (i in 1 until intervals.size) {
            val current = intervals[i]
            val last = merged.last()
            if (current.first < last.second) {
                merged[merged.size - 1] = last.first to max(last.second, current.second)
            } else {
                merged.add(current)
            }
        }
        
        val clippedIntervals = merged.map { (start, end) ->
            max(start, sessionStartTime) to min(end, sessionEndTime)
        }.filter { it.first < it.second }

        return clippedIntervals.sumOf { it.second - it.first }
    }

    internal fun aggregateUsage(allEvents: List<RawAppEvent>, periodEndDate: Long): Map<Pair<String, String>, Pair<Long, Long>> {
        data class Session(val pkg: String, val startTime: Long, val endTime: Long)
        val sessions = mutableListOf<Session>()
        val currentSessionStart = mutableMapOf<String, Long>()

        allEvents.forEach { event ->
            val pkg = event.packageName
            if (isFilteredPackage(pkg)) return@forEach

            val isResume = event.eventType == RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED
            val isPauseOrStop = event.eventType == RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED || event.eventType == RawAppEvent.EVENT_TYPE_ACTIVITY_STOPPED
            val isScreenOff = event.eventType == RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE

            if (isResume) {
                currentSessionStart.keys.filter { it != pkg }.forEach { otherPkg ->
                    currentSessionStart.remove(otherPkg)?.let { startTime ->
                        if(event.eventTimestamp > startTime) sessions.add(Session(otherPkg, startTime, event.eventTimestamp - 1))
                    }
                }
                if (!currentSessionStart.containsKey(pkg)) {
                    currentSessionStart[pkg] = event.eventTimestamp
                }
            } else if (isPauseOrStop) {
                currentSessionStart.remove(pkg)?.let { startTime ->
                    if(event.eventTimestamp > startTime) sessions.add(Session(pkg, startTime, event.eventTimestamp))
                }
            } else if (isScreenOff) {
                currentSessionStart.keys.toList().forEach { runningPkg ->
                    currentSessionStart.remove(runningPkg)?.let { startTime ->
                        if(event.eventTimestamp > startTime) sessions.add(Session(runningPkg, startTime, event.eventTimestamp))
                    }
                }
            }
        }
        currentSessionStart.forEach { (pkg, startTime) ->
            if(periodEndDate > startTime) sessions.add(Session(pkg, startTime, periodEndDate))
        }

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

    override fun getTotalUsageTimeMillisForDate(dateString: String): Flow<Long?> {
        return dailyAppUsageDao.getTotalUsageTimeMillisForDate(dateString)
    }

    override suspend fun backfillHistoricalAppUsageData(numberOfDays: Int): Boolean = withContext(Dispatchers.IO) {
        val usageStatsManager =
            application.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return@withContext false

        val today = Calendar.getInstance()
        var overallSuccess = true

        for (i in 1..numberOfDays) {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = today.timeInMillis
                add(Calendar.DAY_OF_YEAR, -i)
            }
            val historicalDateString = DateUtil.formatDateToYyyyMmDdString(calendar.time)
            val startOfDayUTC = DateUtil.getStartOfDayUtcMillis(historicalDateString)
            val endOfDayUTC = DateUtil.getEndOfDayUtcMillis(historicalDateString)

            try {
                val usageStatsList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDayUTC, endOfDayUTC)

                if (usageStatsList.isNullOrEmpty()) {
                    Log.d(TAG_REPO, "No usage stats returned from system for $historicalDateString. Skipping.")
                    continue
                }

                // Filter out system apps and apps with no usage
                val relevantStats = usageStatsList.filter {
                    !isFilteredPackage(it.packageName) && it.totalTimeInForeground > 0
                }

                if(relevantStats.isEmpty()) {
                    Log.d(TAG_REPO, "No relevant app usage found for $historicalDateString after filtering.")
                    continue
                }

                val recordsToInsert = relevantStats.map { stat ->
                    DailyAppUsageRecord(
                        packageName = stat.packageName,
                        dateString = historicalDateString,
                        usageTimeMillis = stat.totalTimeInForeground,
                        activeTimeMillis = 0L,
                        lastUpdatedTimestamp = System.currentTimeMillis()
                    )
                }

                dailyAppUsageDao.deleteUsageForDate(historicalDateString)
                dailyAppUsageDao.insertAllUsage(recordsToInsert)
                Log.i(TAG_REPO, "Successfully backfilled ${recordsToInsert.size} usage records for $historicalDateString.")

            } catch (e: Exception) {
                Log.e(TAG_REPO, "Error during backfill for $historicalDateString: ${e.message}", e)
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
            packageName
        }
    }

    override suspend fun getUsageForPackageAndDates(packageName: String, dateStrings: List<String>): List<DailyAppUsageRecord> {
        return dailyAppUsageDao.getUsageForPackageAndDates(packageName, dateStrings)
    }

    override suspend fun getAggregatedScrollForPackageAndDates(packageName: String, dateStrings: List<String>): List<AppScrollDataPerDate> {
        return scrollSessionDao.getAggregatedScrollForPackageAndDates(packageName, dateStrings)
    }

    override suspend fun getAggregatedScrollForDateUi(dateString: String): List<AppScrollUiItem> {
        val aggregatedData = scrollSessionDao.getAggregatedScrollDataForDate(dateString).first()
        val uiItems = mutableListOf<AppScrollUiItem>()
        for (data in aggregatedData) {
            processScrollDataToUiItem(data)?.let {
                uiItems.add(it)
            }
        }
        return uiItems
    }

    override fun getAllDistinctUsageDateStrings(): Flow<List<String>> {
        return dailyAppUsageDao.getAllDistinctUsageDateStrings()
    }

    override fun getAllDistinctScrollDateStrings(): Flow<List<String>> {
        return scrollSessionDao.getAllDistinctScrollDateStrings()
    }
} 