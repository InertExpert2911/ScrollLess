package com.example.scrolltrack.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyDeviceSummaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(summary: DailyDeviceSummary)

    @Query("SELECT * FROM daily_device_summary WHERE date_string = :dateString")
    fun getSummaryForDate(dateString: String): Flow<DailyDeviceSummary?>

    @Query("SELECT total_unlock_count FROM daily_device_summary WHERE date_string = :dateString")
    fun getUnlockCountForDate(dateString: String): Flow<Int?>

    @Query("SELECT total_notification_count FROM daily_device_summary WHERE date_string = :dateString")
    fun getNotificationCountForDate(dateString: String): Flow<Int?>

    @Query("SELECT * FROM daily_device_summary ORDER BY date_string ASC")
    fun getAllSummaries(): Flow<List<DailyDeviceSummary>>

} 