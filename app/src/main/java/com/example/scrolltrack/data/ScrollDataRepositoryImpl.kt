package com.example.scrolltrack.data

import android.app.Application
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.example.scrolltrack.db.DailyAppUsageDao // Ensure this is imported
import com.example.scrolltrack.db.DailyAppUsageRecord
import com.example.scrolltrack.db.ScrollSessionDao
import com.example.scrolltrack.db.ScrollSessionRecord
import com.example.scrolltrack.db.AppScrollDataPerDate // Import the new data class
import com.example.scrolltrack.util.DateUtil
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.Date

class ScrollDataRepositoryImpl(
    private val scrollSessionDao: ScrollSessionDao,
    private val dailyAppUsageDao: DailyAppUsageDao, // Make sure this is passed in constructor
    private val application: Application
) : ScrollDataRepository {

    private val TAG_REPO = "ScrollDataRepoImpl"
    private val packageManager: PackageManager = application.packageManager

    override fun getAggregatedScrollDataForDate(dateString: String): Flow<List<AppScrollData>> {
        return scrollSessionDao.getAggregatedScrollDataForDate(dateString)
    }

    override fun getTotalScrollForDate(dateString: String): Flow<Long?> {
        return scrollSessionDao.getTotalScrollForDate(dateString)
    }

    override fun getAllSessions(): Flow<List<ScrollSessionRecord>> {
        return scrollSessionDao.getAllSessionsFlow()
    }

    // New method implementation
    override fun getDailyUsageRecordsForDate(dateString: String): Flow<List<DailyAppUsageRecord>> {
        return dailyAppUsageDao.getUsageForDate(dateString)
    }

    // Implementation for the missing method
    override fun getUsageRecordsForDateRange(startDateString: String, endDateString: String): Flow<List<DailyAppUsageRecord>> {
        return dailyAppUsageDao.getUsageRecordsForDateRange(startDateString, endDateString)
    }

    override suspend fun updateTodayAppUsageStats(): Boolean {
        val usageStatsManager =
            application.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: run {
                    Log.e(TAG_REPO, "UsageStatsManager not available for today's update.")
                    return false
                }

        Log.d(TAG_REPO, "Starting update for today's app usage stats.")
        val recordsToInsert = mutableListOf<DailyAppUsageRecord>()

        val todayCalendar = Calendar.getInstance()
        val dateString = DateUtil.formatDate(todayCalendar.time)
        val dayStartTime = DateUtil.getStartOfDayMillis(dateString)
        val dayEndTime = DateUtil.getEndOfDayMillis(dateString)

        try {
            val dailyUsageStatsList: List<UsageStats>? =
                usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    dayStartTime,
                    dayEndTime
                )

            if (dailyUsageStatsList != null && dailyUsageStatsList.isNotEmpty()) {
                for (usageStats in dailyUsageStatsList) {
                    if (usageStats.totalTimeInForeground > 0) {
                        recordsToInsert.add(
                            DailyAppUsageRecord(
                                packageName = usageStats.packageName,
                                dateString = dateString,
                                usageTimeMillis = usageStats.totalTimeInForeground,
                                lastUpdatedTimestamp = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_REPO, "Error fetching or processing usage stats for today's update for $dateString", e)
            return false // Indicate failure
        }

        if (recordsToInsert.isNotEmpty()) {
            try {
                dailyAppUsageDao.insertAllUsage(recordsToInsert)
                Log.i(TAG_REPO, "Successfully inserted/updated ${recordsToInsert.size} usage records for today ($dateString).")
            } catch (e: Exception) {
                Log.e(TAG_REPO, "Error inserting today's usage records into database for $dateString.", e)
                return false // Indicate failure
            }
        } else {
            Log.i(TAG_REPO, "No new usage records to insert for today ($dateString).")
        }
        return true // Indicate success
    }

    override suspend fun getTotalUsageTimeMillisForDate(dateString: String): Long? {
        val usageStatsManager =
            application.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: run {
                    Log.e(TAG_REPO, "UsageStatsManager not available.")
                    return null
                }

        val startTime = DateUtil.getStartOfDayMillis(dateString)
        val endTime = DateUtil.getEndOfDayMillis(dateString)

        Log.d(TAG_REPO, "Querying UsageStats from: ${Date(startTime)} to: ${Date(endTime)} for NON-FILTERED total usage time on $dateString.")

        var totalRawUsageTime: Long = 0
        try {
            val usageStatsList: List<UsageStats>? =
                usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    startTime,
                    endTime
                )

            if (usageStatsList != null && usageStatsList.isNotEmpty()) {
                for (usageStats in usageStatsList) {
                    if (usageStats.totalTimeInForeground > 0) {
                        totalRawUsageTime += usageStats.totalTimeInForeground
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_REPO, "Error calculating raw total usage stats for $dateString", e)
            return null
        }
        Log.d(TAG_REPO, "Total NON-FILTERED usage time for $dateString: $totalRawUsageTime ms")
        return totalRawUsageTime
    }

    override suspend fun backfillHistoricalAppUsageData(numberOfDays: Int): Boolean {
        val usageStatsManager =
            application.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: run {
                    Log.e(TAG_REPO, "UsageStatsManager not available for backfill.")
                    return false
                }

        Log.i(TAG_REPO, "Starting historical app usage data backfill for the last $numberOfDays days.")
        val recordsToInsert = mutableListOf<DailyAppUsageRecord>()

        for (i in 0 until numberOfDays) {
            val targetCalendar = Calendar.getInstance()
            targetCalendar.add(Calendar.DAY_OF_YEAR, -i)
            val dateString = DateUtil.formatDate(targetCalendar.time)
            val dayStartTime = DateUtil.getStartOfDayMillis(dateString)
            val dayEndTime = DateUtil.getEndOfDayMillis(dateString)

            try {
                val dailyUsageStatsList: List<UsageStats>? =
                    usageStatsManager.queryUsageStats(
                        UsageStatsManager.INTERVAL_DAILY,
                        dayStartTime,
                        dayEndTime
                    )

                if (dailyUsageStatsList != null && dailyUsageStatsList.isNotEmpty()) {
                    for (usageStats in dailyUsageStatsList) {
                        if (usageStats.totalTimeInForeground > 0) {
                            recordsToInsert.add(
                                DailyAppUsageRecord(
                                    packageName = usageStats.packageName,
                                    dateString = dateString,
                                    usageTimeMillis = usageStats.totalTimeInForeground,
                                    lastUpdatedTimestamp = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG_REPO, "Error fetching or processing usage stats during backfill for $dateString", e)
            }
        }

        if (recordsToInsert.isNotEmpty()) {
            try {
                dailyAppUsageDao.insertAllUsage(recordsToInsert)
                Log.i(TAG_REPO, "Successfully inserted/updated ${recordsToInsert.size} historical usage records.")
            } catch (e: Exception) {
                Log.e(TAG_REPO, "Error inserting historical usage records into database.", e)
                return false
            }
        } else {
            Log.i(TAG_REPO, "No new historical usage records to insert from backfill.")
        }
        return true
    }

    // --- Methods for App Detail Screen Chart Data ---
    override suspend fun getUsageForPackageAndDates(packageName: String, dateStrings: List<String>): List<DailyAppUsageRecord> {
        return dailyAppUsageDao.getUsageForPackageAndDates(packageName, dateStrings)
    }

    override suspend fun getAggregatedScrollForPackageAndDates(packageName: String, dateStrings: List<String>): List<AppScrollDataPerDate> {
        return scrollSessionDao.getAggregatedScrollForPackageAndDates(packageName, dateStrings)
    }
}
