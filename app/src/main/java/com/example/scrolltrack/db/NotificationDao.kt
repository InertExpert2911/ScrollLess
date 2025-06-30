package com.example.scrolltrack.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.scrolltrack.data.NotificationSummary
import com.example.scrolltrack.data.NotificationCountPerApp
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNotification(notification: NotificationRecord)

    @Query("SELECT * FROM notification_records WHERE date_string = :dateString ORDER BY post_time_utc DESC")
    fun getNotificationsForDate(dateString: String): Flow<List<NotificationRecord>>

    @Query("""
        SELECT category, COUNT(*) as count
        FROM notification_records
        WHERE category IS NOT NULL AND date_string BETWEEN :startDateString AND :endDateString
        GROUP BY category
        ORDER BY count DESC
    """)
    fun getNotificationSummaryForPeriod(startDateString: String, endDateString: String): Flow<List<NotificationSummary>>

    @Query("""
        SELECT package_name as packageName, COUNT(*) as count
        FROM notification_records
        WHERE date_string BETWEEN :startDateString AND :endDateString
        GROUP BY package_name
        ORDER BY count DESC
    """)
    fun getNotificationCountPerAppForPeriod(startDateString: String, endDateString: String): Flow<List<NotificationCountPerApp>>

    @Query("DELETE FROM notification_records WHERE post_time_utc < :cutoffTimestamp")
    suspend fun deleteOldNotifications(cutoffTimestamp: Long)
} 