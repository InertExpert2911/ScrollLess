package com.example.scrolltrack.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a summary of device-level metrics for a single day.
 * This table is designed to have only one row per date.
 */
@Entity(tableName = "daily_device_summary")
data class DailyDeviceSummary(
    @PrimaryKey
    @ColumnInfo(name = "date_string")
    val dateString: String, // Format: "YYYY-MM-DD"

    @ColumnInfo(name = "total_usage_time_millis", defaultValue = "0")
    val totalUsageTimeMillis: Long = 0L,

    @ColumnInfo(name = "total_unlocked_duration_millis", defaultValue = "0")
    val totalUnlockedDurationMillis: Long = 0L,

    @ColumnInfo(name = "total_unlock_count")
    val totalUnlockCount: Int = 0,

    @ColumnInfo(name = "total_notification_count")
    val totalNotificationCount: Int = 0,

    @ColumnInfo(name = "last_updated_timestamp")
    val lastUpdatedTimestamp: Long = 0L,

    @ColumnInfo(name = "total_app_opens")
    val totalAppOpens: Int = 0,

    @ColumnInfo(name = "first_unlock_timestamp_utc")
    val firstUnlockTimestampUtc: Long? = null,

    @ColumnInfo(name = "last_unlock_timestamp_utc", defaultValue = "NULL")
    val lastUnlockTimestampUtc: Long? = null,

    @ColumnInfo(name = "intentional_unlock_count", defaultValue = "0")
    val intentionalUnlockCount: Int = 0,

    @ColumnInfo(name = "glance_unlock_count", defaultValue = "0")
    val glanceUnlockCount: Int = 0
)
