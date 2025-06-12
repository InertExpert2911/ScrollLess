package com.example.scrolltrack.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RawAppEventDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: RawAppEvent)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvents(events: List<RawAppEvent>)

    @Query("SELECT * FROM raw_app_events WHERE event_date_string = :dateString ORDER BY event_timestamp ASC")
    suspend fun getEventsForDate(dateString: String): List<RawAppEvent>

    @Query("SELECT * FROM raw_app_events WHERE event_timestamp >= :startTime AND event_timestamp < :endTime ORDER BY event_timestamp ASC")
    suspend fun getEventsForPeriod(startTime: Long, endTime: Long): List<RawAppEvent>

    @Query("SELECT * FROM raw_app_events WHERE package_name = :packageName AND event_timestamp >= :startTime AND event_timestamp < :endTime ORDER BY event_timestamp ASC")
    suspend fun getEventsForPackageNameInPeriod(packageName: String, startTime: Long, endTime: Long): List<RawAppEvent>

    @Query("DELETE FROM raw_app_events WHERE event_timestamp < :cutoffTimestamp")
    suspend fun deleteOldEvents(cutoffTimestamp: Long)

    @Query("SELECT MIN(event_timestamp) FROM raw_app_events")
    suspend fun getFirstEventTimestamp(): Long?

    @Query("SELECT MAX(event_timestamp) FROM raw_app_events")
    suspend fun getLastEventTimestamp(): Long?

    @Query("DELETE FROM raw_app_events WHERE event_date_string = :dateString")
    suspend fun deleteEventsForDateString(dateString: String)

    @Query("SELECT MAX(event_timestamp) FROM raw_app_events WHERE event_date_string = :dateString")
    suspend fun getLatestEventTimestampForDate(dateString: String): Long?

} 