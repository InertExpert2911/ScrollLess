package com.example.scrolltrack.data.processors

import com.example.scrolltrack.db.DailyInsight
import com.example.scrolltrack.db.RawAppEvent
import com.example.scrolltrack.db.UnlockSessionRecord
import com.example.scrolltrack.util.DateUtil
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class InsightGenerator @Inject constructor() {
    operator fun invoke(
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
        val meaningfulCount = unlockSessions.count { it.sessionType == "Intentional" || it.sessionEndReason == "INTERRUPTED" || it.sessionType == null }.toLong()
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
}