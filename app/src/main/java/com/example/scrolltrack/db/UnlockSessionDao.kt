package com.example.scrolltrack.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UnlockSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(unlockSession: UnlockSessionRecord)

    @Query("SELECT * FROM unlock_sessions WHERE lock_timestamp IS NULL AND unlock_timestamp < :beforeTimestamp ORDER BY unlock_timestamp DESC LIMIT 1")
    suspend fun getOpenSessionBefore(beforeTimestamp: Long): UnlockSessionRecord?

    @Query("UPDATE unlock_sessions SET lock_timestamp = :lockTimestamp, duration_millis = :duration, first_app_package_name = :firstAppPackage, triggering_notification_key = :notificationKey WHERE id = :sessionId")
    suspend fun closeSession(sessionId: Long, lockTimestamp: Long, duration: Long, firstAppPackage: String?, notificationKey: String?)

    @Query("SELECT COUNT(id) FROM unlock_sessions WHERE date_string = :dateString")
    fun getUnlockCountForDate(dateString: String): kotlinx.coroutines.flow.Flow<Int>

    @Query("DELETE FROM unlock_sessions WHERE date_string = :dateString")
    suspend fun deleteSessionsForDate(dateString: String)

    @Query("SELECT unlock_timestamp FROM unlock_sessions WHERE date_string = :dateString")
    suspend fun getUnlockTimestampsForDate(dateString: String): List<Long>
} 