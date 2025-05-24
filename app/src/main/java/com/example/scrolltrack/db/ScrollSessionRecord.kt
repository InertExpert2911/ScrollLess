package com.example.scrolltrack.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scroll_sessions")
data class ScrollSessionRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "scroll_amount")
    val scrollAmount: Long,

    @ColumnInfo(name = "session_start_time")
    val sessionStartTime: Long,

    @ColumnInfo(name = "session_end_time")
    val sessionEndTime: Long,

    @ColumnInfo(name = "date_string")
    val date: String,

    @ColumnInfo(name = "session_end_reason") // New field
    val sessionEndReason: String // e.g., "APP_SWITCH", "SCREEN_OFF", "SERVICE_DESTROYED"
)