package com.example.scrolltrack.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.scrolltrack.data.AppScrollData
import kotlinx.coroutines.flow.Flow

@Dao
interface ScrollSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<ScrollSessionRecord>)

    @Query("DELETE FROM scroll_sessions WHERE date_string = :date")
    suspend fun deleteSessionsForDate(date: String)

    @Query("""
        SELECT SUM(scroll_amount)
        FROM scroll_sessions
        WHERE date_string = :date
    """)
    fun getTotalScrollForDate(date: String): Flow<Long?>

    @Query("""
        SELECT package_name as packageName, 
               SUM(scroll_amount) as totalScroll, 
               SUM(scroll_amount_x) as totalScrollX,
               SUM(scroll_amount_y) as totalScrollY,
               dataType
        FROM scroll_sessions
        WHERE date_string = :dateString
        GROUP BY package_name, dataType
    """)
    fun getScrollDataForDate(dateString: String): Flow<List<AppScrollData>>

    @Query("""
        SELECT date_string as dateString, SUM(scroll_amount) as totalScroll, SUM(scroll_amount_x) as totalScrollX, SUM(scroll_amount_y) as totalScrollY
        FROM scroll_sessions
        WHERE package_name = :packageName AND date_string IN (:dateStrings)
        GROUP BY date_string
    """)
    suspend fun getAggregatedScrollForPackageAndDates(packageName: String, dateStrings: List<String>): List<AppScrollDataPerDate>

    @Query("SELECT DISTINCT date_string FROM scroll_sessions ORDER BY date_string DESC")
    fun getAllDistinctScrollDateStrings(): Flow<List<String>>

    @Query("SELECT * FROM scroll_sessions")
    fun getAllScrollSessions(): Flow<List<ScrollSessionRecord>>

    @Query("""
        SELECT package_name as packageName, 
               SUM(scroll_amount) as totalScroll, 
               SUM(scroll_amount_x) as totalScrollX,
               SUM(scroll_amount_y) as totalScrollY,
               dataType
        FROM scroll_sessions
        WHERE date_string BETWEEN :startDateString AND :endDateString
        GROUP BY package_name, dataType
    """)
    fun getScrollDataForDateRange(startDateString: String, endDateString: String): Flow<List<AppScrollData>>
}
// New data class to hold scroll data along with its date, as AppScrollData only has packageName and totalScroll
data class AppScrollDataPerDate(
    val dateString: String, // YYYY-MM-DD
    val totalScroll: Long,
    val totalScrollX: Long,
    val totalScrollY: Long
)
