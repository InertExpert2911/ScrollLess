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
    @Query("SELECT package_name as packageName, SUM(scroll_amount) as totalScroll, CASE WHEN SUM(CASE WHEN dataType = 'MEASURED' THEN 1 ELSE 0 END) > 0 THEN 'MEASURED' ELSE 'INFERRED' END as dataType FROM scroll_sessions WHERE date_string = :dateString GROUP BY package_name ORDER BY totalScroll DESC")
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

    /**
     * Retrieves aggregated scroll data (package name, date_string, and sum of scroll_amount)
     * for a specific package name and a list of date strings.
     * The date_string is included to help map results back if a date has no scroll data.
     */
    @Query("""
        SELECT package_name as packageName, date_string as date, SUM(scroll_amount) as totalScroll 
        FROM scroll_sessions 
        WHERE package_name = :packageName AND date_string IN (:dateStrings) 
        GROUP BY package_name, date_string
    """)
    suspend fun getAggregatedScrollForPackageAndDates(packageName: String, dateStrings: List<String>): List<AppScrollDataPerDate>

    @Query("SELECT DISTINCT date_string FROM scroll_sessions ORDER BY date_string DESC")
    fun getAllDistinctScrollDateStrings(): Flow<List<String>>

    @Query("SELECT * FROM scroll_sessions WHERE date_string = :date")
    fun getSessionsForDate(date: String): Flow<List<ScrollSessionRecord>>

    @Query("SELECT * FROM scroll_sessions WHERE date_string BETWEEN :startDate AND :endDate")
    fun getSessionsForDateRange(startDate: String, endDate: String): Flow<List<ScrollSessionRecord>>
}

// New data class to hold scroll data along with its date, as AppScrollData only has packageName and totalScroll
data class AppScrollDataPerDate(
    val packageName: String,
    val date: String, // YYYY-MM-DD
    val totalScroll: Long
)
