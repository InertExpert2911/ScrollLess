package com.example.scrolltrack.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.example.scrolltrack.util.DateUtil
import kotlin.math.max

class UsageStatsProcessor(context: Context) {
    private val TAG = "UsageStatsProcessor"
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun fetchAndProcessUsageStatsForToday(): Map<Pair<String, String>, Long> {
        val today = DateUtil.getCurrentLocalDateString()
        val startOfDay = DateUtil.getStartOfDayUtcMillis(today)
        val endOfDay = DateUtil.getEndOfDayUtcMillis(today)

        val events = fetchUsageEvents(startOfDay, endOfDay)
        return processEvents(events, endOfDay)
    }

    private fun fetchUsageEvents(startTime: Long, endTime: Long): List<UsageEvents.Event> {
        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val events = mutableListOf<UsageEvents.Event>()
        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            events.add(event)
        }
        return events
    }

    private fun processEvents(events: List<UsageEvents.Event>, periodEnd: Long): Map<Pair<String, String>, Long> {
        val sessions = mutableListOf<Pair<String, LongRange>>()
        val currentSessionStart = mutableMapOf<String, Long>()

        events.forEach { event ->
            val pkg = event.packageName
            val eventType = event.eventType
            val timestamp = event.timeStamp

            if (eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                currentSessionStart.keys.filter { it != pkg }.forEach { otherPkg ->
                    currentSessionStart.remove(otherPkg)?.let { startTime ->
                        if (timestamp > startTime) sessions.add(otherPkg to (startTime..timestamp))
                    }
                }
                if (!currentSessionStart.containsKey(pkg)) {
                    currentSessionStart[pkg] = timestamp
                }
            } else if (eventType == UsageEvents.Event.ACTIVITY_PAUSED || eventType == UsageEvents.Event.ACTIVITY_STOPPED) {
                currentSessionStart.remove(pkg)?.let { startTime ->
                    if (timestamp > startTime) sessions.add(pkg to (startTime..timestamp))
                }
            }
        }
        currentSessionStart.forEach { (pkg, startTime) ->
            if (periodEnd > startTime) sessions.add(pkg to (startTime..periodEnd))
        }

        val aggregator = mutableMapOf<Pair<String, String>, Long>()
        sessions.forEach { (pkg, range) ->
            val date = DateUtil.formatUtcTimestampToLocalDateString(range.first)
            val key = Pair(pkg, date)
            val duration = range.last - range.first
            aggregator[key] = aggregator.getOrDefault(key, 0) + duration
        }
        return aggregator
    }
} 