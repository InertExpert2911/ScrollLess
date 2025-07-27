package com.example.scrolltrack.data.processors

import com.example.scrolltrack.db.RawAppEvent
import com.example.scrolltrack.db.ScrollSessionRecord
import com.example.scrolltrack.util.AppConstants
import javax.inject.Inject
import kotlin.math.abs

class ScrollSessionCalculator @Inject constructor() {
    operator fun invoke(events: List<RawAppEvent>, filterSet: Set<String>): List<ScrollSessionRecord> {
        val allScrollEvents = events
            .filter {
                (it.eventType == RawAppEvent.EVENT_TYPE_SCROLL_MEASURED || it.eventType == RawAppEvent.EVENT_TYPE_SCROLL_INFERRED)
                        && (it.scrollDeltaX != null || it.scrollDeltaY != null || it.value != null) // Include legacy 'value' for migration
                        && it.packageName !in filterSet
            }

        // Identify all packages that have produced high-quality, measured scroll data in this batch.
        val packagesWithMeasuredScroll = allScrollEvents
            .filter { it.eventType == RawAppEvent.EVENT_TYPE_SCROLL_MEASURED }
            .map { it.packageName }
            .toSet()

        // Filter the events list. If a package has any measured events, we completely discard its inferred events for this batch.
        // This ensures an app is only represented by one data type per processing cycle, prioritizing the higher quality one.
        val scrollEvents = allScrollEvents
            .filter { event ->
                if (event.packageName in packagesWithMeasuredScroll) {
                    // This app has measured data, so only keep its measured events.
                    event.eventType == RawAppEvent.EVENT_TYPE_SCROLL_MEASURED
                } else {
                    // This app does not have measured data, so keep its events (which will be inferred).
                    true
                }
            }
            .sortedBy { it.eventTimestamp }

        if (scrollEvents.isEmpty()) return emptyList()

        val mergedSessions = mutableListOf<ScrollSessionRecord>()
        var currentSession: ScrollSessionRecord? = null

        for (event in scrollEvents) {
            val eventDataType = if (event.eventType == RawAppEvent.EVENT_TYPE_SCROLL_MEASURED) "MEASURED" else "INFERRED"

            // Prioritize new delta columns, fall back to 'value' for old events
            val deltaX = event.scrollDeltaX ?: 0
            val deltaY = event.scrollDeltaY ?: if (event.eventType == RawAppEvent.EVENT_TYPE_SCROLL_INFERRED) event.value?.toInt() ?: 0 else 0
            val totalDelta = (abs(deltaX) + abs(deltaY)).toLong()

            if (totalDelta == 0L) continue // Skip events with no effective scroll

            if (currentSession == null) {
                currentSession = ScrollSessionRecord(
                    packageName = event.packageName,
                    sessionStartTime = event.eventTimestamp,
                    sessionEndTime = event.eventTimestamp,
                    scrollAmount = totalDelta,
                    scrollAmountX = abs(deltaX).toLong(),
                    scrollAmountY = abs(deltaY).toLong(),
                    dateString = event.eventDateString,
                    sessionEndReason = "PROCESSED",
                    dataType = eventDataType
                )
            } else {
                val timeDiff = event.eventTimestamp - currentSession.sessionEndTime
                if (event.packageName == currentSession.packageName &&
                    currentSession.dataType == eventDataType &&
                    timeDiff <= AppConstants.SESSION_MERGE_GAP_MS) {
                    currentSession = currentSession.copy(
                        sessionEndTime = event.eventTimestamp,
                        scrollAmount = currentSession.scrollAmount + totalDelta,
                        scrollAmountX = currentSession.scrollAmountX + abs(deltaX),
                        scrollAmountY = currentSession.scrollAmountY + abs(deltaY)
                    )
                } else {
                    mergedSessions.add(currentSession)
                    currentSession = ScrollSessionRecord(
                        packageName = event.packageName,
                        sessionStartTime = event.eventTimestamp,
                        sessionEndTime = event.eventTimestamp,
                        scrollAmount = totalDelta,
                        scrollAmountX = abs(deltaX).toLong(),
                        scrollAmountY = abs(deltaY).toLong(),
                        dateString = event.eventDateString,
                        sessionEndReason = "PROCESSED",
                        dataType = eventDataType
                    )
                }
            }
        }
        currentSession?.let { mergedSessions.add(it) }
        return mergedSessions
    }
}