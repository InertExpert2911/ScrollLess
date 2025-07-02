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
    suspend fun insert(notification: NotificationRecord)

    @Query("SELECT * FROM notifications ORDER BY post_time_utc DESC")
    fun getAllNotifications(): Flow<List<NotificationRecord>>

    @Query("SELECT COUNT(DISTINCT id) FROM notifications WHERE date(post_time_utc / 1000, 'unixepoch', 'localtime') = :dateString")
    fun getNotificationCountForDate(dateString: String): Flow<Int>

    @Query("SELECT COUNT(DISTINCT id) FROM notifications WHERE date(post_time_utc / 1000, 'unixepoch', 'localtime') = :dateString")
    suspend fun getNotificationCountForDateImmediate(dateString: String): Int

    @Query("SELECT * FROM notifications WHERE date(post_time_utc / 1000, 'unixepoch', 'localtime') = :dateString")
    suspend fun getNotificationsForDateList(dateString: String): List<NotificationRecord>

    @Query("""
        SELECT category, COUNT(*) as count
        FROM notifications
        WHERE category IS NOT NULL AND date(post_time_utc / 1000, 'unixepoch', 'localtime') BETWEEN :startDateString AND :endDateString
        GROUP BY category
        ORDER BY count DESC
    """)
    fun getNotificationSummaryForPeriod(startDateString: String, endDateString: String): Flow<List<NotificationSummary>>

    @Query("""
        SELECT 
            package_name as packageName, 
            COUNT(id) as count 
        FROM notifications 
        WHERE date(post_time_utc / 1000, 'unixepoch', 'localtime') BETWEEN :startDateString AND :endDateString
        GROUP BY package_name 
        ORDER BY count DESC
    """)
    fun getNotificationCountPerAppForPeriod(startDateString: String, endDateString: String): Flow<List<NotificationCountPerApp>>

    @Query("SELECT * FROM notifications WHERE date(post_time_utc / 1000, 'unixepoch', 'localtime') = :dateString ORDER BY post_time_utc DESC")
    fun getNotificationsForDate(dateString: String): Flow<List<NotificationRecord>>

    @Query("DELETE FROM notifications WHERE post_time_utc < :cutoffTimestamp")
    suspend fun deleteNotificationsOlderThan(cutoffTimestamp: Long)

    @Query("UPDATE notifications SET removal_reason = :reason WHERE package_name = :packageName AND post_time_utc = :postTimeUTC")
    suspend fun updateRemovalReason(packageName: String, postTimeUTC: Long, reason: Int)
} 