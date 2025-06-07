package com.example.scrolltrack.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing the total foreground usage time for a specific package on a specific date.
 */
@Entity(
    tableName = "daily_app_usage",
    indices = [Index(value = ["package_name", "date_string"], unique = true)] // Ensure one entry per app per day
)
data class DailyAppUsageRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "date_string") // Format: "YYYY-MM-DD"
    val dateString: String,

    @ColumnInfo(name = "usage_time_millis")
    val usageTimeMillis: Long, // Total time app was in foreground

    @ColumnInfo(name = "active_time_millis", defaultValue = "0")
    val activeTimeMillis: Long = 0L, // Time user was actively interacting (clicking, typing, focusing)

    @ColumnInfo(name = "last_updated_timestamp") // When this record was last calculated/updated
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)
