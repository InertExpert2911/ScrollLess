package com.example.scrolltrack.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.scrolltrack.data.AppScrollData // Ensure this path is correct for your AppScrollData
import kotlinx.coroutines.flow.Flow

@Dao
interface ScrollSessionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(session: ScrollSessionRecord)

    /**
     * Retrieves aggregated scroll data (package name and sum of scroll_amount)
     * for a specific date, ordered by total scroll amount descending.
     * Returns a Flow, so the UI can observe changes.
     */
    @Query("SELECT package_name as packageName, SUM(scroll_amount) as totalScroll FROM scroll_sessions WHERE date_string = :dateString GROUP BY package_name ORDER BY totalScroll DESC")
    fun getAggregatedScrollDataForDate(dateString: String): Flow<List<AppScrollData>>

    /**
     * Calculates the overall total summed scrollAmount for a specific date.
     * Returns a Flow, so the UI can observe changes.
     */
    @Query("SELECT SUM(scroll_amount) FROM scroll_sessions WHERE date_string = :dateString")
    fun getTotalScrollForDate(dateString: String): Flow<Long?>

    /**
     * Retrieves all scroll sessions from the database, ordered by start time descending.
     * Useful for debugging or a detailed list view. Returns a Flow.
     */
    @Query("SELECT * FROM scroll_sessions ORDER BY session_start_time DESC")
    fun getAllSessionsFlow(): Flow<List<ScrollSessionRecord>>

    @Query("SELECT * FROM scroll_sessions ORDER BY session_start_time DESC")
    suspend fun getAllSessions(): List<ScrollSessionRecord> // Kept for potential non-flow use

    @Query("SELECT SUM(scroll_amount) FROM scroll_sessions WHERE package_name = :pkgName AND date_string = :dateString")
    suspend fun getTotalScrollForAppOnDate(pkgName: String, dateString: String): Long?
}
