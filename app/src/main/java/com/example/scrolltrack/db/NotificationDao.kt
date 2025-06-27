package com.example.scrolltrack.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNotification(notification: NotificationRecord)

    @Query("SELECT * FROM notification_records WHERE date_string = :dateString ORDER BY post_time_utc DESC")
    fun getNotificationsForDate(dateString: String): Flow<List<NotificationRecord>>

    @Query("SELECT COUNT(*) FROM notification_records WHERE date_string = :dateString")
    fun getTotalNotificationCountForDate(dateString: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM notification_records WHERE date_string = :dateString")
    suspend fun getNotificationCountForDate(dateString: String): Int

    @Query("SELECT DISTINCT package_name FROM notification_records WHERE date_string = :dateString")
    suspend fun getPackagesWithNotificationsForDate(dateString: String): List<String>

    @Query("SELECT COUNT(*) FROM notification_records WHERE date_string = :dateString AND package_name = :packageName")
    suspend fun getNotificationCountForAppOnDate(packageName: String, dateString: String): Int

    @Query("DELETE FROM notification_records WHERE post_time_utc < :cutoffTimestamp")
    suspend fun deleteOldNotifications(cutoffTimestamp: Long)
} 