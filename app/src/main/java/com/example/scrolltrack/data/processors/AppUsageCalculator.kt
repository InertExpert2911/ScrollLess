package com.example.scrolltrack.data.processors

import com.example.scrolltrack.db.*
import com.example.scrolltrack.util.AppConstants
import com.example.scrolltrack.util.DateUtil
import javax.inject.Inject

class AppUsageCalculator @Inject constructor() {
    suspend operator fun invoke(
        events: List<RawAppEvent>,
        filterSet: Set<String>,
        dateString: String,
        unlockSessions: List<UnlockSessionRecord>,
        notificationsByPackage: Map<String, Int>
    ): Pair<List<DailyAppUsageRecord>, DailyDeviceSummary?> {
        val endTime = DateUtil.getEndOfDayUtcMillis(dateString)
        val filteredEvents = events.filter { it.packageName !in filterSet }

        val (usageAggregates, inferredEvents) = aggregateUsage(filteredEvents, endTime)
        val allEventsForAppOpens = filteredEvents + inferredEvents
        val appOpens = calculateAppOpens(allEventsForAppOpens)

        val totalNotifications = notificationsByPackage.values.sum()

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

    private fun calculateAppOpens(events: List<RawAppEvent>): Map<String, Int> {
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

    internal fun aggregateUsage(
    allEvents: List<RawAppEvent>,
    periodEndDate: Long
): Pair<Map<String, Pair<Long, Long>>, List<RawAppEvent>> {
    data class Session(val pkg: String, val startTime: Long, val endTime: Long)
    val sessions = mutableListOf<Session>()
    val inferredEvents = mutableListOf<RawAppEvent>()

    // --- REFINED STATE ---
    // This variable now perfectly models the "single foreground app" constraint.
    var currentForegroundSession: Pair<String, Long>? = null

    val sortedEvents = allEvents.sortedBy { it.eventTimestamp }

    sortedEvents.forEachIndexed { index, event ->
        val pkg = event.packageName
        when (event.eventType) {
            RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED -> {
                // An app is coming to the foreground. Close any session that was running before it.
                currentForegroundSession?.let { (runningPkg, startTime) ->
                    if (runningPkg != pkg) { // Avoid creating zero-length sessions
                        sessions.add(Session(runningPkg, startTime, event.eventTimestamp))
                    }
                }
                // Start the new session. This is now the ONLY active session.
                currentForegroundSession = pkg to event.eventTimestamp
            }

            RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, RawAppEvent.EVENT_TYPE_ACTIVITY_STOPPED -> {
                // An app is being paused. Close its session ONLY if it was the one in the foreground.
                currentForegroundSession?.let { (runningPkg, startTime) ->
                    if (runningPkg == pkg) {
                        sessions.add(Session(runningPkg, startTime, event.eventTimestamp))
                        currentForegroundSession = null // No app is in the foreground now.
                    }
                }
            }

            RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE -> {
                // The screen is off. Whatever was running is no longer in the foreground.
                currentForegroundSession?.let { (runningPkg, startTime) ->
                    sessions.add(Session(runningPkg, startTime, event.eventTimestamp))
                    currentForegroundSession = null // No app is in the foreground now.
                }
            }
        }
    }

    // If a session is still open at the end of the processing period, close it.
    currentForegroundSession?.let { (runningPkg, startTime) ->
        if (periodEndDate > startTime) {
            sessions.add(Session(runningPkg, startTime, periodEndDate))
        }
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

    private fun calculateActiveTimeFromInteractions(events: List<RawAppEvent>, sessionStart: Long, sessionEnd: Long): Long {
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
}