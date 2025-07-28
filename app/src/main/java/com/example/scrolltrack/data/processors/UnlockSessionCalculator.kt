package com.example.scrolltrack.data.processors

import com.example.scrolltrack.db.NotificationRecord
import com.example.scrolltrack.db.RawAppEvent
import com.example.scrolltrack.db.UnlockSessionRecord
import com.example.scrolltrack.util.AppConstants
import timber.log.Timber
import javax.inject.Inject

class UnlockSessionCalculator @Inject constructor() {
    operator fun invoke(
        events: List<RawAppEvent>,
        notifications: List<NotificationRecord>,
        hiddenAppsSet: Set<String>,
        unlockEventTypes: Set<Int>,
        lockEventTypes: Set<Int>
    ): List<UnlockSessionRecord> {
        if (events.isEmpty()) return emptyList()

        val sortedEvents = events.sortedBy { it.eventTimestamp }
        val sessions = mutableListOf<UnlockSessionRecord>()
        var openSession: UnlockSessionRecord? = null

        for (event in sortedEvents) {
            val isUnlock = event.eventType in unlockEventTypes
            val isLock = event.eventType in lockEventTypes
            val isServiceStop = event.eventType == RawAppEvent.EVENT_TYPE_SERVICE_STOPPED

            if (isUnlock) {
                if (openSession != null) {
                    Timber.w("Found a ghost session. Closing it before starting a new one.")
                    sessions.add(openSession.copy(
                        lockTimestamp = event.eventTimestamp,
                        durationMillis = event.eventTimestamp - openSession.unlockTimestamp,
                        firstAppPackageName = null,
                        sessionType = "Glance",
                        sessionEndReason = "GHOST"
                    ))
                }

                openSession = UnlockSessionRecord(
                    unlockTimestamp = event.eventTimestamp,
                    dateString = event.eventDateString,
                    unlockEventType = event.eventType.toString()
                )

            } else if (openSession != null && (isLock || isServiceStop)) {
                val currentOpenSession = openSession
                val duration = event.eventTimestamp - currentOpenSession.unlockTimestamp
                if (duration >= 0) {
                    val sessionEndReason = if (isLock) "LOCKED" else "INTERRUPTED"
                    val sessionType = if (duration < AppConstants.MINIMUM_GLANCE_DURATION_MS) "Glance" else "Intentional"

                    var isCompulsiveCheck = false
                    val firstAppEvent = events.find { e ->
                        e.eventTimestamp > currentOpenSession.unlockTimestamp &&
                                e.eventTimestamp < event.eventTimestamp &&
                                e.eventType == RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED &&
                                e.packageName !in hiddenAppsSet
                    }

                    if (firstAppEvent != null) {
                        val otherAppOpened = events.any { e ->
                            e.eventTimestamp > firstAppEvent.eventTimestamp &&
                                    e.eventTimestamp < event.eventTimestamp &&
                                    e.eventType == RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED
                        }
                        if (!otherAppOpened && duration < AppConstants.COMPULSIVE_UNLOCK_THRESHOLD_MS) {
                            isCompulsiveCheck = true
                        }
                    }

                    var notificationPackageName: String? = null
                    if (firstAppEvent != null) {
                        val recentNotification = notifications.lastOrNull { n ->
                            n.postTimeUTC < currentOpenSession.unlockTimestamp &&
                                    (currentOpenSession.unlockTimestamp - n.postTimeUTC) < AppConstants.NOTIFICATION_UNLOCK_WINDOW_MS
                        }
                        if (recentNotification != null && recentNotification.packageName == firstAppEvent.packageName) {
                            notificationPackageName = recentNotification.packageName
                        }
                    }

                    sessions.add(currentOpenSession.copy(
                        lockTimestamp = event.eventTimestamp,
                        durationMillis = duration,
                        firstAppPackageName = firstAppEvent?.packageName,
                        sessionType = sessionType,
                        sessionEndReason = sessionEndReason,
                        isCompulsive = isCompulsiveCheck,
                        triggeringNotificationPackageName = notificationPackageName
                    ))
                }
                openSession = null
            }
        }
        // Add the last open session if it exists
        openSession?.let { sessions.add(it) }
        return sessions
    }
}