package com.example.scrolltrack.data

// Assuming AppScrollData is in this 'data' package
// If it's in 'db', change the import accordingly.
// import com.example.scrolltrack.db.AppScrollData
import com.example.scrolltrack.db.DailyAppUsageRecord
import com.example.scrolltrack.db.ScrollSessionRecord
import kotlinx.coroutines.flow.Flow

interface ScrollDataRepository {

    /**
     * Retrieves aggregated scroll data (package name and sum of scroll_amount)
     * for a specific date, ordered by total scroll amount descending.
     */
    fun getAggregatedScrollDataForDate(dateString: String): Flow<List<AppScrollData>>

    /**
     * Calculates the overall total summed scrollAmount for a specific date.
     */
    fun getTotalScrollForDate(dateString: String): Flow<Long?>

    /**
     * Retrieves all scroll sessions from the database, ordered by start time descending.
     */
    fun getAllSessions(): Flow<List<ScrollSessionRecord>>

    /**
     * Fetches the total foreground usage time for all filtered apps on a specific date.
     * @param dateString The date in "YYYY-MM-DD" format.
     * @return Total usage time in milliseconds, or null if permission is denied or an error occurs.
     */
    suspend fun getTotalUsageTimeMillisForDate(dateString: String): Long?

    /**
     * Fetches and stores historical per-app usage data for the specified number of past days.
     * Applies filtering to include only user-relevant apps.
     * @param numberOfDays How many past days of data to fetch (e.g., 7 or 10).
     * @return True if successful, false otherwise.
     */
    suspend fun backfillHistoricalAppUsageData(numberOfDays: Int): Boolean

    /**
     * Retrieves all raw DailyAppUsageRecord entries for a specific date.
     * The ViewModel will then map these to UI items, including fetching app names/icons.
     */
    fun getDailyUsageRecordsForDate(dateString: String): Flow<List<DailyAppUsageRecord>> // New method

    /**
     * Fetches and stores app usage stats specifically for the current day.
     * @return True if successful, false otherwise.
     */
    suspend fun updateTodayAppUsageStats(): Boolean
}
