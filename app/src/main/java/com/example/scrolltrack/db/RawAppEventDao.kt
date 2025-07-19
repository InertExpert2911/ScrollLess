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

    @Query("SELECT * FROM raw_app_events WHERE event_timestamp >= :startOfDayUTC AND event_timestamp <= :endOfDayUTC ORDER BY event_timestamp ASC")
    suspend fun getEventsForPeriod(startOfDayUTC: Long, endOfDayUTC: Long): List<RawAppEvent>

    @Query("SELECT * FROM raw_app_events WHERE event_timestamp >= :startOfDayUTC AND event_timestamp <= :endOfDayUTC ORDER BY event_timestamp ASC")
    fun getEventsForPeriodFlow(startOfDayUTC: Long, endOfDayUTC: Long): Flow<List<RawAppEvent>>

    @Query("SELECT * FROM raw_app_events WHERE event_date_string = :dateString ORDER BY event_timestamp ASC")
    suspend fun getEventsForDate(dateString: String): List<RawAppEvent>

    @Query("SELECT * FROM raw_app_events WHERE event_type = :eventType AND event_timestamp >= :startTimestamp AND event_timestamp < :endTimestamp")
    suspend fun getEventsOfTypeForPeriod(eventType: Int, startTimestamp: Long, endTimestamp: Long): List<RawAppEvent>

    @Query("SELECT COUNT(*) FROM raw_app_events WHERE event_date_string = :dateString AND event_type = :eventType")
    fun countEventsOfTypeForDate(dateString: String, eventType: Int): Flow<Int>

    @Query("SELECT MIN(event_timestamp) FROM raw_app_events WHERE event_date_string = :dateString AND event_type = :eventType")
    fun getFirstEventTimestampOfTypeForDate(dateString: String, eventType: Int): Flow<Long?>

    @Query("SELECT * FROM raw_app_events WHERE event_date_string = :dateString AND event_type = :eventType")
    fun getEventsOfTypeForDate(dateString: String, eventType: Int): Flow<List<RawAppEvent>>

    @Query("SELECT MAX(event_timestamp) FROM raw_app_events WHERE event_date_string = :dateString")
    suspend fun getLatestEventTimestampForDate(dateString: String): Long?

    @Query("SELECT MAX(event_timestamp) FROM raw_app_events")
    suspend fun getLatestEventTimestamp(): Long?

    @Query("DELETE FROM raw_app_events WHERE event_timestamp < :timestamp")
    suspend fun deleteEventsBefore(timestamp: Long)

    @Query("SELECT * FROM raw_app_events WHERE event_type = :eventType AND event_timestamp > :timestamp ORDER BY event_timestamp ASC LIMIT 1")
    suspend fun getFirstEventAfter(timestamp: Long, eventType: Int): RawAppEvent?

    @Query("SELECT * FROM raw_app_events WHERE event_type = :eventType AND event_date_string = :dateString ORDER BY event_timestamp DESC LIMIT 1")
    suspend fun getLastEventForDate(dateString: String, eventType: Int): RawAppEvent?
}
