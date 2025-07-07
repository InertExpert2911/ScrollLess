package com.example.scrolltrack.services

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.scrolltrack.db.RawAppEvent
import com.example.scrolltrack.db.RawAppEventDao
import com.example.scrolltrack.di.InferredScrollPrefs
import com.example.scrolltrack.util.AppConstants
import com.example.scrolltrack.util.DateUtil
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.sqrt

@HiltWorker
class FlushInferredScrollWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    @InferredScrollPrefs private val prefs: SharedPreferences,
    private val rawAppEventDao: RawAppEventDao
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "FlushInferredScrollWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val allCounters = prefs.all
        if (allCounters.isEmpty()) {
            Timber.d("No inferred scroll counters to flush.")
            return@withContext Result.success()
        }

        Timber.i("Starting to flush ${allCounters.size} inferred scroll counters.")
        val eventsToInsert = mutableListOf<RawAppEvent>()
        val currentTime = System.currentTimeMillis()
        val currentDateString = DateUtil.formatUtcTimestampToLocalDateString(currentTime)

        for ((packageName, count) in allCounters) {
            if (count !is Int || count <= 0) continue

            val inferredValue = (sqrt(count.toDouble()) * AppConstants.INFERRED_SCROLL_MULTIPLIER).toLong()
            if (inferredValue > 0) {
                eventsToInsert.add(
                    RawAppEvent(
                        packageName = packageName,
                        className = null,
                        eventType = RawAppEvent.EVENT_TYPE_SCROLL_INFERRED,
                        eventTimestamp = currentTime,
                        eventDateString = currentDateString,
                        source = RawAppEvent.SOURCE_ACCESSIBILITY,
                        value = inferredValue
                    )
                )
            }
        }

        try {
            if (eventsToInsert.isNotEmpty()) {
                rawAppEventDao.insertEvents(eventsToInsert)
                Timber.i("Successfully flushed ${eventsToInsert.size} inferred scroll events to the database.")
            }
            // Clear all counters after successfully flushing them.
            prefs.edit { clear() }
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Failed to flush inferred scroll events to database.")
            Result.retry()
        }
    }
} 