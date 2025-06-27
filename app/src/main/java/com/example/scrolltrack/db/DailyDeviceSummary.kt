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

    @ColumnInfo(name = "total_unlock_count")
    val totalUnlockCount: Int,

    @ColumnInfo(name = "total_notification_count")
    val totalNotificationCount: Int,

    @ColumnInfo(name = "last_updated_timestamp")
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
) 