package com.example.scrolltrack.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
// import com.example.scrolltrack.data.AppUsageData // We'll need a simple data class for aggregated results
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the daily_app_usage table.
 */
@Dao
interface DailyAppUsageDao {

    /**
     * Inserts a new daily app usage record. If a record for the same package and date
     * already exists, it will be replaced.
     * This is useful for daily updates of usage stats.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateUsage(dailyAppUsageRecord: DailyAppUsageRecord)

    /**
     * Inserts a list of daily app usage records. If a record for the same package and date
     * already exists, it will be replaced.
     * Useful for batch inserting historical data.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllUsage(dailyAppUsageRecords: List<DailyAppUsageRecord>)

    /**
     * Retrieves all app usage records for a specific date, ordered by usage time descending.
     * Returns a Flow for reactive updates.
     *
     * Note: This query directly returns DailyAppUsageRecord.
     * If you need aggregated data like package name and its label/icon from PackageManager,
     * that transformation typically happens in the Repository or ViewModel.
     */
    @Query("SELECT * FROM daily_app_usage WHERE date_string = :dateString ORDER BY usage_time_millis DESC")
    fun getUsageForDate(dateString: String): Flow<List<DailyAppUsageRecord>>

    /**
     * Retrieves a specific app's usage record for a specific date.
     */
    @Query("SELECT * FROM daily_app_usage WHERE package_name = :packageName AND date_string = :dateString LIMIT 1")
    suspend fun getSpecificAppUsageForDate(packageName: String, dateString: String): DailyAppUsageRecord?

    /**
     * Calculates the total summed usage_time_millis for all apps on a specific date.
     * This gives the raw total before any filtering (like excluding system apps)
     * that might happen in the Repository or ViewModel.
     * Returns a Flow for reactive updates.
     */
    @Query("SELECT SUM(usage_time_millis) FROM daily_app_usage WHERE date_string = :dateString")
    fun getTotalUsageTimeMillisForDate(dateString: String): Flow<Long?>

    /**
     * Deletes all usage records older than a given timestamp.
     * Useful for data pruning.
     * @param timestampMillis The timestamp (milliseconds since epoch). Records with lastUpdatedTimestamp < this will be deleted.
     */
    @Query("DELETE FROM daily_app_usage WHERE last_updated_timestamp < :timestampMillis")
    suspend fun deleteOldUsageData(timestampMillis: Long): Int // Returns the number of rows deleted

    /**
     * Deletes all data from the daily_app_usage table.
     * Useful for testing or a full reset.
     */
    @Query("DELETE FROM daily_app_usage")
    suspend fun clearAllUsageData()
}

/**
 * Simple data class to hold aggregated app usage data, potentially with app label.
 * This is an example if you wanted the DAO to return a custom object.
 * However, for simplicity, getUsageForDate returns List<DailyAppUsageRecord>
 * and the mapping to include appName/icon is done in the ViewModel.
 */
// data class AppUsageData(
//    val packageName: String,
//    val usageTimeMillis: Long
//    // val appName: String, // Could be added if joining with another table or mapping later
// )
