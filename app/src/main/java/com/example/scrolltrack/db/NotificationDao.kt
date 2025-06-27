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

    @Query("DELETE FROM notification_records WHERE post_time_utc < :cutoffTimestamp")
    suspend fun deleteOldNotifications(cutoffTimestamp: Long)
} 