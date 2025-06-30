package com.example.scrolltrack.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notification_records",
    indices = [
        Index(value = ["package_name"]),
        Index(value = ["post_time_utc"])
    ]
)
data class NotificationRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "post_time_utc")
    val postTimeUTC: Long,

    @ColumnInfo(name = "title")
    val title: String?,

    @ColumnInfo(name = "text")
    val text: String?,

    @ColumnInfo(name = "category")
    val category: String?,

    @ColumnInfo(name = "date_string")
    val dateString: String // "YYYY-MM-DD" format for easier grouping by day
) 