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
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val scrollDataRepository: ScrollDataRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val UNIQUE_WORK_NAME = "DailyProcessingWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            Timber.d("DailyProcessingWorker started.")
            // Process today and yesterday to handle events that span across midnight.
            val today = DateUtil.getCurrentLocalDateString()
            val yesterday = DateUtil.getYesterdayDateString()

            scrollDataRepository.processAndSummarizeDate(yesterday)
            scrollDataRepository.processAndSummarizeDate(today)
            Timber.d("DailyProcessingWorker finished successfully.")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "DailyProcessingWorker failed.")
            Result.failure()
        }
    }
}