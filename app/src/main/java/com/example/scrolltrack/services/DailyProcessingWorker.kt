package com.example.scrolltrack.services

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.util.DateUtil
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class DailyProcessingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val scrollDataRepository: ScrollDataRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "DailyProcessingWorker"
    }

    override suspend fun doWork(): Result {
        Timber.i("Starting daily processing worker.")
        return try {
            // Process today and yesterday to catch any data from the previous day
            // that might have been logged after midnight.
            val today = DateUtil.getCurrentLocalDateString()
            val yesterday = DateUtil.getPastDateString(1)

            Timber.d("Processing data for yesterday: $yesterday")
            scrollDataRepository.processAndSummarizeDate(yesterday)

            Timber.d("Processing data for today: $today")
            val currentForegroundApp = scrollDataRepository.getCurrentForegroundApp()
            Timber.d("Current foreground app detected: $currentForegroundApp")
            scrollDataRepository.processAndSummarizeDate(today, currentForegroundApp)

            Timber.i("Daily processing worker finished successfully.")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Daily processing worker failed.")
            Result.retry()
        }
    }
} 