package com.example.scrolltrack.data

import android.app.Application
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.example.scrolltrack.db.DailyAppUsageDao
import com.example.scrolltrack.db.DailyAppUsageRecord
import com.example.scrolltrack.db.ScrollSessionDao
import com.example.scrolltrack.db.ScrollSessionRecord
import com.example.scrolltrack.util.DateUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date

class ScrollDataRepositoryImpl(
    private val scrollSessionDao: ScrollSessionDao, // Assuming you'll add dailyAppUsageDao here
    private val dailyAppUsageDao: DailyAppUsageDao, // Added DAO for usage stats
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

    override suspend fun getTotalUsageTimeMillisForDate(dateString: String): Long? {
        val usageStatsManager =
            application.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: run {
                    Log.e(TAG_REPO, "UsageStatsManager not available.")
                    return null
                }

        val startTime = DateUtil.getStartOfDayMillis(dateString)
        val endTime = DateUtil.getEndOfDayMillis(dateString)

        Log.d(TAG_REPO, "Querying UsageStats from: ${Date(startTime)} to: ${Date(endTime)} for filtered usage time on $dateString.")

        var totalFilteredUsageTime: Long = 0
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
                        val packageName = usageStats.packageName
                        if (packageName == application.packageName) continue
                        try {
                            val appInfo: ApplicationInfo = packageManager.getApplicationInfo(packageName, 0)
                            if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) &&
                                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0)) {
                                continue
                            }
                            if (packageManager.getLaunchIntentForPackage(packageName) == null) {
                                continue
                            }
                            totalFilteredUsageTime += usageStats.totalTimeInForeground
                        } catch (e: PackageManager.NameNotFoundException) {
                            // Log.w(TAG_REPO, "Package info not found during total calculation: $packageName for date $dateString")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_REPO, "Error calculating total usage stats for $dateString", e)
            return null // Return null on error
        }
        return totalFilteredUsageTime
    }

    override suspend fun backfillHistoricalAppUsageData(numberOfDays: Int): Boolean = withContext(Dispatchers.IO) {
        val usageStatsManager =
            application.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: run {
                    Log.e(TAG_REPO, "UsageStatsManager not available for backfill.")
                    return@withContext false
                }

        Log.i(TAG_REPO, "Starting historical app usage data backfill for the last $numberOfDays days.")
        val calendar = Calendar.getInstance()
        val recordsToInsert = mutableListOf<DailyAppUsageRecord>()

        for (i in 0 until numberOfDays) { // 0 is today, 1 is yesterday, etc.
            val targetCalendar = Calendar.getInstance()
            targetCalendar.add(Calendar.DAY_OF_YEAR, -i) // Go back i days

            val dateString = DateUtil.formatDate(targetCalendar.time)
            val dayStartTime = DateUtil.getStartOfDayMillis(dateString)
            val dayEndTime = DateUtil.getEndOfDayMillis(dateString)

            Log.d(TAG_REPO, "Backfilling data for date: $dateString (Range: ${Date(dayStartTime)} to ${Date(dayEndTime)})")

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
                            val packageName = usageStats.packageName
                            if (packageName == application.packageName) continue // Skip own app

                            try {
                                val appInfo: ApplicationInfo = packageManager.getApplicationInfo(packageName, 0)
                                if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) &&
                                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0)) {
                                    continue // Skip base system apps
                                }
                                if (packageManager.getLaunchIntentForPackage(packageName) == null) {
                                    continue // Skip non-launchable apps
                                }

                                recordsToInsert.add(
                                    DailyAppUsageRecord(
                                        packageName = packageName,
                                        dateString = dateString,
                                        usageTimeMillis = usageStats.totalTimeInForeground,
                                        lastUpdatedTimestamp = System.currentTimeMillis() // Mark as updated now
                                    )
                                )
                            } catch (e: PackageManager.NameNotFoundException) {
                                // Log.w(TAG_REPO, "Package not found during backfill: $packageName for date $dateString")
                            }
                        }
                    }
                } else {
                    Log.d(TAG_REPO, "No usage stats found for $dateString during backfill.")
                }
            } catch (e: SecurityException) {
                Log.e(TAG_REPO, "SecurityException during backfill for $dateString. Permission might have been revoked.", e)
                // Potentially stop or skip this day
            } catch (e: Exception) {
                Log.e(TAG_REPO, "Error fetching usage stats during backfill for $dateString", e)
            }
        }

        if (recordsToInsert.isNotEmpty()) {
            try {
                dailyAppUsageDao.insertAllUsage(recordsToInsert)
                Log.i(TAG_REPO, "Successfully inserted/updated ${recordsToInsert.size} historical usage records.")
            } catch (e: Exception) {
                Log.e(TAG_REPO, "Error inserting historical usage records into database.", e)
                return@withContext false
            }
        } else {
            Log.i(TAG_REPO, "No new historical usage records to insert.")
        }
        return@withContext true
    }
}