package com.example.scrolltrack.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(tableName = "raw_app_events",
    indices = [
        Index(value = ["event_date_string", "event_timestamp"])
    ]
)
data class RawAppEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "package_name", index = true)
    val packageName: String,

    @ColumnInfo(name = "class_name")
    val className: String?,

    @ColumnInfo(name = "event_type")
    val eventType: Int,

    @ColumnInfo(name = "event_timestamp", index = true)
    val eventTimestamp: Long,

    @ColumnInfo(name = "event_date_string", index = true)
    val eventDateString: String // Format: "yyyy-MM-dd"
) {
    companion object {
        // --- System event types from UsageStatsManager ---
        const val EVENT_TYPE_UNKNOWN = 0
        const val EVENT_TYPE_ACTIVITY_RESUMED = 1
        const val EVENT_TYPE_ACTIVITY_PAUSED = 2
        const val EVENT_TYPE_ACTIVITY_STOPPED = 3
        const val EVENT_TYPE_CONFIGURATION_CHANGE = 4
        const val EVENT_TYPE_USER_INTERACTION = 5
        const val EVENT_TYPE_SCREEN_INTERACTIVE = 6
        const val EVENT_TYPE_SCREEN_NON_INTERACTIVE = 7
        const val EVENT_TYPE_KEYGUARD_SHOWN = 8
        const val EVENT_TYPE_KEYGUARD_HIDDEN = 9
        const val EVENT_TYPE_FOREGROUND_SERVICE_START = 10
        const val EVENT_TYPE_FOREGROUND_SERVICE_STOP = 11

        // --- Custom event types from our Accessibility Service ---
        const val EVENT_TYPE_ACCESSIBILITY_OFFSET = 1000 // To avoid collision with system events
        const val EVENT_TYPE_ACCESSIBILITY_VIEW_CLICKED = EVENT_TYPE_ACCESSIBILITY_OFFSET + 1
        const val EVENT_TYPE_ACCESSIBILITY_VIEW_FOCUSED = EVENT_TYPE_ACCESSIBILITY_OFFSET + 2
        const val EVENT_TYPE_ACCESSIBILITY_TYPING = EVENT_TYPE_ACCESSIBILITY_OFFSET + 3
        const val EVENT_TYPE_ACCESSIBILITY_SCROLLED = EVENT_TYPE_ACCESSIBILITY_OFFSET + 4

        // --- Custom event types from our Notification Listener ---
        const val EVENT_TYPE_NOTIFICATION_OFFSET = 2000
        const val EVENT_TYPE_NOTIFICATION_POSTED = EVENT_TYPE_NOTIFICATION_OFFSET + 1
        const val EVENT_TYPE_NOTIFICATION_REMOVED = EVENT_TYPE_NOTIFICATION_OFFSET + 2

        // Function to check if an event is from the accessibility service
        fun isAccessibilityEvent(eventType: Int): Boolean {
            return eventType >= EVENT_TYPE_ACCESSIBILITY_OFFSET
        }
    }
}