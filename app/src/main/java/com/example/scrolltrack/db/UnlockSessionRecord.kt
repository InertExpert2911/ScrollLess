package com.example.scrolltrack.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "unlock_sessions",
    indices = [
        Index(value = ["date_string"]),
        Index(value = ["unlock_timestamp"])
    ]
)
data class UnlockSessionRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "unlock_timestamp")
    val unlockTimestamp: Long,

    @ColumnInfo(name = "lock_timestamp")
    val lockTimestamp: Long? = null,

    @ColumnInfo(name = "duration_millis")
    val durationMillis: Long? = null,

    @ColumnInfo(name = "date_string")
    val dateString: String,

    @ColumnInfo(name = "first_app_package_name", index = true)
    val firstAppPackageName: String? = null,

    @ColumnInfo(name = "triggering_notification_key")
    val triggeringNotificationKey: String? = null,

    @ColumnInfo(name = "unlock_event_type")
    val unlockEventType: String,

    @ColumnInfo(name = "session_type")
    val sessionType: String? = null, // e.g., "Glance", "Intentional"

    @ColumnInfo(name = "session_end_reason")
    val sessionEndReason: String? = null, // e.g., "LOCKED", "GHOST"

    @ColumnInfo(name = "is_compulsive", defaultValue = "0")
    val isCompulsive: Boolean = false
)
