package com.example.scrolltrack.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "raw_app_events")
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
        // UsageStatsManager Event Types (Subset that we might map directly or indirectly)
        // These are illustrative; you'll map android.app.usage.UsageEvents.Event constants
        const val EVENT_TYPE_UNKNOWN = 0 // Or map to UsageEvents.Event.NONE
        const val EVENT_TYPE_FOREGROUND_SERVICE_START = 1 // Example, maps to UsageEvents.Event.FOREGROUND_SERVICE_START
        const val EVENT_TYPE_FOREGROUND_SERVICE_STOP = 2  // Example, maps to UsageEvents.Event.FOREGROUND_SERVICE_STOP
        const val EVENT_TYPE_ACTIVITY_RESUMED = 5         // Maps to UsageEvents.Event.ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
        const val EVENT_TYPE_ACTIVITY_PAUSED = 6          // Maps to UsageEvents.Event.ACTIVITY_PAUSED / MOVE_TO_BACKGROUND
        const val EVENT_TYPE_ACTIVITY_STOPPED = 7         // Maps to UsageEvents.Event.ACTIVITY_STOPPED
        const val EVENT_TYPE_CONFIGURATION_CHANGE = 8     // Maps to UsageEvents.Event.CONFIGURATION_CHANGE
        const val EVENT_TYPE_SYSTEM_INTERACTION = 9       // Example, maps to UsageEvents.Event.SYSTEM_INTERACTION (though this is broad)
        const val EVENT_TYPE_USER_INTERACTION = 10        // Maps to UsageEvents.Event.USER_INTERACTION
        const val EVENT_TYPE_SCREEN_INTERACTIVE = 11      // Maps to UsageEvents.Event.SCREEN_INTERACTIVE (Device unlocked)
        const val EVENT_TYPE_SCREEN_NON_INTERACTIVE = 12  // Maps to UsageEvents.Event.SCREEN_NON_INTERACTIVE (Device locked/screen off)
        const val EVENT_TYPE_KEYGUARD_SHOWN = 13          // Maps to UsageEvents.Event.KEYGUARD_SHOWN
        const val EVENT_TYPE_KEYGUARD_HIDDEN = 14         // Maps to UsageEvents.Event.KEYGUARD_HIDDEN
        // Add more specific mappings as needed based on UsageEvents.Event
        // For example, if you want to distinguish between MOVE_TO_FOREGROUND and ACTIVITY_RESUMED more clearly
        // or handle other events like SHORTCUT_INVOCATION, etc.
    }
} 