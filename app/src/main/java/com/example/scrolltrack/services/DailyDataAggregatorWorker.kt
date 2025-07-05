package com.example.scrolltrack.services

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.util.DateUtil
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@HiltWorker
class DailyDataAggregatorWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val dataRepository: ScrollDataRepository,
    private val appMetadataRepository: AppMetadataRepository
) : CoroutineWorker(appContext, workerParams) {

    private val TAG = "DailyDataAggregator"

    companion object {
        const val WORKER_NAME = "DailyDataAggregatorWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "Daily data aggregation worker started.")
        try {
            val yesterdayDateString = DateUtil.getYesterdayDateString()
            Log.d(TAG, "Aggregating data for date: $yesterdayDateString")

            // This worker now calls the same central logic as the live summary.
            // It ensures that the final, saved data for the previous day is consistent.
            dataRepository.getLiveSummaryForDate(yesterdayDateString).first() // a single Flow emission to trigger the calculation

            Log.i(TAG, "Daily data aggregation worker finished successfully.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during daily data aggregation", e)
            Result.failure()
        }
    }
} 