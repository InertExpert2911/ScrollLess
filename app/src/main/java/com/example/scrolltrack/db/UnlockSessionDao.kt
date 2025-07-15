package com.example.scrolltrack.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UnlockSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(unlockSession: UnlockSessionRecord): Long

    @Query("SELECT * FROM unlock_sessions WHERE date_string = :dateString AND lock_timestamp IS NULL AND unlock_timestamp < :beforeTimestamp ORDER BY unlock_timestamp DESC LIMIT 1")
    suspend fun getOpenSessionBefore(dateString: String, beforeTimestamp: Long): UnlockSessionRecord?

    @Query("SELECT * FROM unlock_sessions WHERE lock_timestamp IS NULL ORDER BY unlock_timestamp DESC LIMIT 1")
    suspend fun getLatestOpenSession(): UnlockSessionRecord?

    @Query("UPDATE unlock_sessions SET lock_timestamp = :lockTimestamp, duration_millis = :duration, first_app_package_name = :firstAppPackage, triggering_notification_key = :notificationKey, session_type = :sessionType, session_end_reason = :sessionEndReason WHERE id = :sessionId")
    suspend fun closeSession(
        sessionId: Long,
        lockTimestamp: Long,
        duration: Long,
        firstAppPackage: String?,
        notificationKey: String?,
        sessionType: String,
        sessionEndReason: String
    )

    @Query("DELETE FROM unlock_sessions WHERE date_string = :dateString")
    suspend fun deleteSessionsForDate(dateString: String)

    @Query("SELECT COUNT(id) FROM unlock_sessions WHERE date_string = :dateString")
    fun getUnlockCountForDateFlow(dateString: String): Flow<Int>

    @Query("SELECT COUNT(id) FROM unlock_sessions WHERE date_string = :dateString AND session_type = 'Intentional'")
    fun getIntentionalUnlockCountForDateFlow(dateString: String): Flow<Int>

    @Query("SELECT COUNT(id) FROM unlock_sessions WHERE date_string = :dateString AND session_type = 'Glance'")
    fun getGlanceUnlockCountForDateFlow(dateString: String): Flow<Int>

    @Query("SELECT * FROM unlock_sessions WHERE date_string = :dateString")
    suspend fun getUnlockSessionsForDate(dateString: String): List<UnlockSessionRecord>
}
