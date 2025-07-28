package com.example.scrolltrack.data.processors

import com.example.scrolltrack.db.*
import com.example.scrolltrack.util.AppConstants
import com.example.scrolltrack.util.DateUtil
import com.example.scrolltrack.util.Clock
import javax.inject.Inject

class AppUsageCalculator @Inject constructor(
    private val clock: Clock
) {
    suspend operator fun invoke(
        events: List<RawAppEvent>,
        filterSet: Set<String>,
        dateString: String,
        unlockSessions: List<UnlockSessionRecord>,
        notificationsByPackage: Map<String, Int>,
        initialForegroundApp: String?
    ): Pair<List<DailyAppUsageRecord>, DailyDeviceSummary?> {
        val periodStartDate = DateUtil.getStartOfDayUtcMillis(dateString)
        val periodEndDate = DateUtil.getEndOfDayUtcMillis(dateString)

        // Step 1: Calculate usage on the COMPLETE, UNFILTERED event list for maximum accuracy.
        val (usageAggregates, inferredEvents) = aggregateUsage(
            events,
            periodStartDate,
            periodEndDate,
            initialForegroundApp
        )
        val allEventsForAppOpens = events + inferredEvents
        val appOpens = calculateAppOpens(allEventsForAppOpens)

        // --- Step 2: CENTRALIZED FILTERING OF OUTPUT ---
        val visibleUsageAggregates = usageAggregates.filterKeys { it !in filterSet }
        val visibleAppOpens = appOpens.filterKeys { it !in filterSet }
        val visibleNotifications = notificationsByPackage.filterKeys { it !in filterSet }

        val allVisiblePackages = visibleUsageAggregates.keys
            .union(visibleAppOpens.keys)
            .union(visibleNotifications.keys)

        val usageRecords = allVisiblePackages.mapNotNull { pkg ->
            val (usage, active) = visibleUsageAggregates[pkg] ?: (0L to 0L)
            if (usage < AppConstants.MINIMUM_SIGNIFICANT_SESSION_DURATION_MS && (visibleNotifications[pkg] ?: 0) == 0) null
            else DailyAppUsageRecord(
                packageName = pkg,
                dateString = dateString,
                usageTimeMillis = usage,
                activeTimeMillis = active,
                appOpenCount = visibleAppOpens.getOrDefault(pkg, 0),
                notificationCount = visibleNotifications.getOrDefault(pkg, 0),
                lastUpdatedTimestamp = clock.currentTimeMillis()
            )
        }

        val totalUnlocks = unlockSessions.size
        val intentionalUnlocks = unlockSessions.count { it.sessionType == "Intentional" }
        val glanceUnlocks = unlockSessions.count { it.sessionType == "Glance" }
        val firstUnlockTime = unlockSessions.minOfOrNull { it.unlockTimestamp }
        val lastUnlockTime = unlockSessions.maxOfOrNull { it.unlockTimestamp }

        if (usageRecords.isEmpty() && totalUnlocks == 0 && visibleNotifications.isEmpty()) {
            return Pair(emptyList(), null)
        }

        // Step 3: Create the summary based ONLY on the filtered, visible data.
        val deviceSummary = DailyDeviceSummary(
            dateString = dateString,
            totalUsageTimeMillis = usageRecords.sumOf { it.usageTimeMillis },
            totalUnlockedDurationMillis = unlockSessions.sumOf { it.durationMillis ?: 0L },
            totalUnlockCount = totalUnlocks,
            intentionalUnlockCount = intentionalUnlocks,
            glanceUnlockCount = glanceUnlocks,
            firstUnlockTimestampUtc = firstUnlockTime,
            lastUnlockTimestampUtc = lastUnlockTime,
            totalNotificationCount = visibleNotifications.values.sum(),
            totalAppOpens = visibleAppOpens.values.sum(),
            lastUpdatedTimestamp = clock.currentTimeMillis()
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
        periodStartDate: Long,
        periodEndDate: Long,
        initialForegroundApp: String?
    ): Pair<Map<String, Pair<Long, Long>>, List<RawAppEvent>> {
        val usageAggregator = mutableMapOf<String, Long>()
        val interactionsAggregator = mutableMapOf<String, MutableList<RawAppEvent>>()

        val stateChangeEvents = allEvents.filter {
            it.eventType in setOf(
                RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED,
                RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED,
                RawAppEvent.EVENT_TYPE_KEYGUARD_SHOWN,
                RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE
            )
        }.sortedBy { it.eventTimestamp }

        val interactionEventTypes = setOf(
            RawAppEvent.EVENT_TYPE_SCROLL_INFERRED, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED,
            RawAppEvent.EVENT_TYPE_ACCESSIBILITY_TYPING, RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_CLICKED,
            RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_FOCUSED, RawAppEvent.EVENT_TYPE_USER_INTERACTION
        )
        val interactionEvents = allEvents.filter { it.eventType in interactionEventTypes }

        if (stateChangeEvents.isEmpty()) {
            if (initialForegroundApp != null) {
                val duration = periodEndDate - periodStartDate
                if (duration > 0) {
                    usageAggregator[initialForegroundApp] = duration
                    val interactions = interactionEvents.filter { it.eventTimestamp in periodStartDate until periodEndDate }
                    if (interactions.isNotEmpty()) {
                        interactionsAggregator.getOrPut(initialForegroundApp) { mutableListOf() }.addAll(interactions)
                    }
                }
            }
        } else {
            var lastEventTimestamp = periodStartDate
            var currentForegroundApp: String? = initialForegroundApp

            for (event in stateChangeEvents) {
                val eventTimestamp = event.eventTimestamp
                val duration = eventTimestamp - lastEventTimestamp

                if (currentForegroundApp != null && duration > 0) {
                    usageAggregator[currentForegroundApp] =
                        usageAggregator.getOrDefault(currentForegroundApp, 0L) + duration

                    val interactions = interactionEvents.filter { it.eventTimestamp in lastEventTimestamp until eventTimestamp }
                    if (interactions.isNotEmpty()) {
                        interactionsAggregator.getOrPut(currentForegroundApp) { mutableListOf() }.addAll(interactions)
                    }
                }

                currentForegroundApp = when (event.eventType) {
                    RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED -> event.packageName
                    RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED -> {
                        if (currentForegroundApp == event.packageName) null else currentForegroundApp
                    }
                    RawAppEvent.EVENT_TYPE_KEYGUARD_SHOWN,
                    RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE -> null
                    else -> currentForegroundApp
                }
                lastEventTimestamp = eventTimestamp
            }

            val finalDuration = periodEndDate - lastEventTimestamp
            if (currentForegroundApp != null && finalDuration > 0) {
                usageAggregator[currentForegroundApp] =
                    usageAggregator.getOrDefault(currentForegroundApp, 0L) + finalDuration
                val interactions = interactionEvents.filter { it.eventTimestamp in lastEventTimestamp until periodEndDate }
                if (interactions.isNotEmpty()) {
                    interactionsAggregator.getOrPut(currentForegroundApp) { mutableListOf() }.addAll(interactions)
                }
            }
        }

        val finalUsageMap = usageAggregator.mapValues { (pkg, usage) ->
            val activeTime = calculateActiveTimeFromInteractions(
                interactionsAggregator.getOrDefault(pkg, emptyList()),
                periodStartDate,
                periodEndDate
            )
            usage to activeTime
        }

        return finalUsageMap to emptyList()
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