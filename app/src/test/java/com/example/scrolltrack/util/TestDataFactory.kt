package com.example.scrolltrack.util

import com.example.scrolltrack.db.RawAppEvent
import com.example.scrolltrack.db.UnlockSessionRecord

object TestDataFactory {

    fun createRawAppEvent(
        id: Long = 0,
        packageName: String = "com.test.app",
        eventType: Int = RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED,
        timestamp: Long,
        dateString: String = "2023-01-01"
    ): RawAppEvent {
        return RawAppEvent(
            id = id,
            packageName = packageName,
            className = "MainActivity",
            eventType = eventType,
            eventTimestamp = timestamp,
            eventDateString = dateString,
            source = RawAppEvent.SOURCE_USAGE_STATS
        )
    }

    fun createUnlockSession(
        id: Long = 0,
        unlockTimestamp: Long,
        dateString: String,
        isCompulsive: Boolean = false,
        firstApp: String? = "com.test.app",
        unlockEventType: String = "TEST_EVENT"
    ): UnlockSessionRecord {
        return UnlockSessionRecord(
            id = id,
            unlockTimestamp = unlockTimestamp,
            dateString = dateString,
            isCompulsive = isCompulsive,
            firstAppPackageName = firstApp,
            lockTimestamp = unlockTimestamp + 10000, // 10 seconds duration
            unlockEventType = unlockEventType
        )
    }
}
