package com.example.scrolltrack.services

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.scrolltrack.di.InferredScrollPrefs
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class InferredScrollWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    @InferredScrollPrefs private val prefs: SharedPreferences
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_PACKAGE_NAME = "package_name"
        const val WORK_NAME_PREFIX = "InferredScrollWorker_"
    }

    override suspend fun doWork(): Result {
        val packageName = inputData.getString(KEY_PACKAGE_NAME) ?: return Result.failure()

        return try {
            val currentCount = prefs.getInt(packageName, 0)
            prefs.edit {
                putInt(packageName, currentCount + 1)
            }
            Timber.d("Incremented inferred scroll count for $packageName to ${currentCount + 1}")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Failed to increment inferred scroll count for $packageName")
            Result.failure()
        }
    }
} 