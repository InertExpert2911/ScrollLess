package com.example.scrolltrack.util

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.scrolltrack.data.ScrollDataRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.Date

@HiltWorker
class UsageStatsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val scrollDataRepository: ScrollDataRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "UsageStatsWorker"
    }

    override suspend fun doWork(): Result {
        Timber.tag(WORK_NAME).i("UsageStatsWorker started.")
        return try {
            val syncSuccess = scrollDataRepository.syncSystemEvents()
            if (syncSuccess) {
                Timber.i("Sync successful. Now processing dates.")
                // Process today and yesterday to handle events that span across midnight.
                val today = DateUtil.getCurrentLocalDateString()
                val yesterday = DateUtil.getYesterdayDateString()

                scrollDataRepository.processAndSummarizeDate(yesterday)
                scrollDataRepository.processAndSummarizeDate(today)

                Timber.tag(WORK_NAME).i("UsageStatsWorker finished processing successfully.")
                Result.success()
            } else {
                Timber.tag(WORK_NAME).w("UsageStatsWorker finished with a failure to sync. Retrying.")
                Result.retry()
            }
        } catch (e: Exception) {
            Timber.tag(WORK_NAME).e(e, "UsageStatsWorker failed with an exception.")
            // For unexpected errors, it's often safer to retry.
            Result.retry()
        }
    }
} 