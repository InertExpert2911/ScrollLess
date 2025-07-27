package com.example.scrolltrack.data.processors

import com.example.scrolltrack.db.NotificationRecord
import com.example.scrolltrack.db.RawAppEvent
import javax.inject.Inject

class DailyDataProcessor @Inject constructor(
    private val unlockCalculator: UnlockSessionCalculator,
    private val scrollCalculator: ScrollSessionCalculator,
    private val usageCalculator: AppUsageCalculator,
    private val insightGenerator: InsightGenerator
) {
    suspend operator fun invoke(
        dateString: String,
        events: List<RawAppEvent>,
        notifications: List<NotificationRecord>,
        filterSet: Set<String>,
        notificationsByPackage: Map<String, Int>
    ): DailyProcessingResult {
        val unlockRelatedEvents = events.filter {
            it.eventType in setOf(
                RawAppEvent.EVENT_TYPE_USER_UNLOCKED,
                RawAppEvent.EVENT_TYPE_KEYGUARD_HIDDEN,
                RawAppEvent.EVENT_TYPE_KEYGUARD_SHOWN,
                RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE,
                RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED
            )
        }
        val unlockSessions = unlockCalculator(
            unlockRelatedEvents,
            notifications,
            filterSet,
            unlockEventTypes = setOf(RawAppEvent.EVENT_TYPE_USER_UNLOCKED, RawAppEvent.EVENT_TYPE_KEYGUARD_HIDDEN),
            lockEventTypes = setOf(RawAppEvent.EVENT_TYPE_KEYGUARD_SHOWN, RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE)
        )
        val scrollSessions = scrollCalculator(events.filter { it.packageName !in filterSet }, filterSet)
        val (usageRecords, deviceSummary) = usageCalculator(unlockRelatedEvents, filterSet, dateString, unlockSessions, notificationsByPackage)
        val insights = insightGenerator(dateString, unlockSessions, unlockRelatedEvents, filterSet)

        return DailyProcessingResult(unlockSessions, scrollSessions, usageRecords, deviceSummary, insights)
    }
}