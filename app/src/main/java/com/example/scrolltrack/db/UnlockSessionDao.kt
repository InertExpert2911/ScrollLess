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

    @Query("UPDATE unlock_sessions SET lock_timestamp = :lockTimestamp, duration_millis = :duration, first_app_package_name = :firstAppPackage, triggering_notification_key = :notificationKey, session_type = :sessionType, session_end_reason = :sessionEndReason, is_compulsive = :isCompulsive WHERE id = :sessionId")
    suspend fun closeSession(
        sessionId: Long,
        lockTimestamp: Long,
        duration: Long,
        firstAppPackage: String?,
        notificationKey: String?,
        sessionType: String,
        sessionEndReason: String,
        isCompulsive: Boolean = false
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

    @Query("SELECT * FROM unlock_sessions WHERE date_string BETWEEN :startDateString AND :endDateString")
    fun getUnlockSessionsForDateRange(startDateString: String, endDateString: String): Flow<List<UnlockSessionRecord>>

    @Query("SELECT first_app_package_name as packageName, COUNT(id) as count FROM unlock_sessions WHERE session_type = 'Glance' AND date_string BETWEEN :startDateString AND :endDateString AND first_app_package_name IS NOT NULL GROUP BY first_app_package_name ORDER BY count DESC")
    fun getGlanceCountsByPackage(startDateString: String, endDateString: String): Flow<List<PackageCount>>

    @Query("""
        SELECT first_app_package_name as packageName, COUNT(*) as count
        FROM unlock_sessions
        WHERE date_string BETWEEN :startDate AND :endDate AND is_compulsive = 1 AND first_app_package_name IS NOT NULL
        GROUP BY first_app_package_name
    """)
    fun getCompulsiveCheckCountsByPackage(startDate: String, endDate: String): Flow<List<PackageCount>>

    @Query("SELECT n.package_name as packageName, COUNT(u.id) as count FROM unlock_sessions u JOIN notifications n ON u.triggering_notification_key = n.notification_key WHERE u.date_string BETWEEN :startDateString AND :endDateString AND u.triggering_notification_key IS NOT NULL GROUP BY n.package_name ORDER BY count DESC")
    fun getNotificationDrivenUnlockCounts(startDateString: String, endDateString: String): Flow<List<PackageCount>>
}

/**
 * A generic data class for DAO queries that return a package name and an associated count.
 */
data class PackageCount(
    val packageName: String,
    val count: Int
)
