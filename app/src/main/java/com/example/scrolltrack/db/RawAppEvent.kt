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
    val eventDateString: String, // Format: "yyyy-MM-dd"

    @ColumnInfo(name = "source", index = true)
    val source: String,

    @ColumnInfo(name = "value")
    val value: Long? = null,

    // New columns for multi-axis scroll data
    @ColumnInfo(name = "scroll_delta_x", defaultValue = "NULL")
    val scrollDeltaX: Int? = null,

    @ColumnInfo(name = "scroll_delta_y", defaultValue = "NULL")
    val scrollDeltaY: Int? = null,
) {
    companion object {
        // Source constants
        const val SOURCE_USAGE_STATS = "USAGE_STATS"
        const val SOURCE_ACCESSIBILITY = "ACCESSIBILITY"
        const val SOURCE_NOTIFICATION_LISTENER = "NOTIFICATION_LISTENER"
        const val SOURCE_SYSTEM_BROADCAST = "SYSTEM_BROADCAST"

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
        const val EVENT_TYPE_USER_PRESENT = 12

        // --- Custom event types from our Accessibility Service ---
        const val EVENT_TYPE_ACCESSIBILITY_OFFSET = 1000 // To avoid collision with system events
        const val EVENT_TYPE_ACCESSIBILITY_VIEW_CLICKED = EVENT_TYPE_ACCESSIBILITY_OFFSET + 1
        const val EVENT_TYPE_ACCESSIBILITY_VIEW_FOCUSED = EVENT_TYPE_ACCESSIBILITY_OFFSET + 2
        const val EVENT_TYPE_ACCESSIBILITY_TYPING = EVENT_TYPE_ACCESSIBILITY_OFFSET + 3
        const val EVENT_TYPE_SCROLL_MEASURED = EVENT_TYPE_ACCESSIBILITY_OFFSET + 4 // Explicitly measured
        const val EVENT_TYPE_SCROLL_INFERRED = EVENT_TYPE_ACCESSIBILITY_OFFSET + 5 // Inferred from other events


        // --- Custom event types from our own logic ---
        const val EVENT_TYPE_CUSTOM_OFFSET = 3000
        @Deprecated("Use MEASURED or INFERRED specific types", ReplaceWith("EVENT_TYPE_SCROLL_MEASURED"))
        const val EVENT_TYPE_SCROLL = EVENT_TYPE_CUSTOM_OFFSET + 1 // Specific for scroll delta tracking

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