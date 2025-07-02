package com.example.scrolltrack.services

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.db.*
import com.example.scrolltrack.util.DateUtil
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import androidx.room.withTransaction
import java.util.concurrent.TimeUnit

@HiltWorker
class DailyDataAggregatorWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val appDatabase: AppDatabase,
    private val rawAppEventDao: RawAppEventDao,
    private val notificationDao: NotificationDao,
    private val dailyDeviceSummaryDao: DailyDeviceSummaryDao,
    private val appMetadataRepository: AppMetadataRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORKER_NAME = "DailyDataAggregatorWorker"
    }

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val yesterdayString = DateUtil.getYesterdayDateString()
                Log.d(WORKER_NAME, "Starting daily aggregation for date: $yesterdayString")

                // 1. Get Yesterday's Data
                val rawEvents = rawAppEventDao.getEventsForDate(yesterdayString)
                val notifications = notificationDao.getNotificationsForDateList(yesterdayString)

                // 2. Process Data
                // Unlocks
                val unlockEvents = rawEvents.filter { it.eventType == RawAppEvent.EVENT_TYPE_USER_PRESENT }
                val totalUnlocks = unlockEvents.size
                val firstUnlockTime = unlockEvents.minOfOrNull { it.eventTimestamp }

                // Notifications
                val totalNotifications = notifications.size

                // App Opens (with contextual debounce)
                val totalAppOpens = calculateContextualAppOpens(rawEvents)

                // 3. Save Summary
                val summary = DailyDeviceSummary(
                    dateString = yesterdayString,
                    totalUnlockCount = totalUnlocks,
                    totalNotificationCount = totalNotifications,
                    totalAppOpens = totalAppOpens,
                    firstUnlockTimestampUtc = firstUnlockTime,
                    lastUpdatedTimestamp = System.currentTimeMillis()
                )
                
                appDatabase.withTransaction {
                    dailyDeviceSummaryDao.insertOrUpdate(summary)
                }

                Log.d(WORKER_NAME, "Successfully aggregated data for $yesterdayString. Unlocks: $totalUnlocks, Notifications: $totalNotifications, App Opens: $totalAppOpens")
                Result.success()
            } catch (e: Exception) {
                Log.e(WORKER_NAME, "Failed to aggregate daily data.", e)
                Result.failure()
            }
        }
    }

    private suspend fun calculateContextualAppOpens(events: List<RawAppEvent>): Int {
        val resumeEvents = events
            .filter { it.eventType == RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED }
            .sortedBy { it.eventTimestamp }

        if (resumeEvents.isEmpty()) return 0

        val appOpenTimestamps = mutableMapOf<String, Long>()
        var openCount = 0

        // Build a filter set of system apps/launchers to ignore
        val filterSet = appMetadataRepository.getAllMetadata()
            .filter { it.userHidesOverride ?: !it.isUserVisible }
            .map { it.packageName }
            .toSet()

        resumeEvents.forEach { event ->
            if (filterSet.contains(event.packageName)) return@forEach

            val lastOpenTimestamp = appOpenTimestamps[event.packageName] ?: 0L
            // Debounce: Count as a new open only if it's been more than 15 seconds
            if (event.eventTimestamp - lastOpenTimestamp > TimeUnit.SECONDS.toMillis(15)) {
                openCount++
            }
            appOpenTimestamps[event.packageName] = event.eventTimestamp
        }
        return openCount
    }
} 