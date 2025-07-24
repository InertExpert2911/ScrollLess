package com.example.scrolltrack.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_insights",
    indices = [Index(value = ["date_string", "insight_key"], unique = true)]
)
data class DailyInsight(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "date_string")
    val dateString: String,

    @ColumnInfo(name = "insight_key")
    val insightKey: String,

    @ColumnInfo(name = "string_value")
    val stringValue: String? = null,

    @ColumnInfo(name = "long_value")
    val longValue: Long? = null,

    @ColumnInfo(name = "double_value")
    val doubleValue: Double? = null
)
