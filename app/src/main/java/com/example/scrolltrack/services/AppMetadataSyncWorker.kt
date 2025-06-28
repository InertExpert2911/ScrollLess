package com.example.scrolltrack.services

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.scrolltrack.data.AppMetadataRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import android.util.Log

@HiltWorker
class AppMetadataSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val appMetadataRepository: AppMetadataRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_PACKAGE_NAME = "PACKAGE_NAME"
        const val KEY_ACTION = "ACTION"

        const val ACTION_INSTALL_OR_UPDATE = "INSTALL_OR_UPDATE"
        const val ACTION_UNINSTALL = "UNINSTALL"
    }

    override suspend fun doWork(): Result {
        val packageName = inputData.getString(KEY_PACKAGE_NAME)
        val action = inputData.getString(KEY_ACTION)

        if (packageName.isNullOrEmpty() || action.isNullOrEmpty()) {
            Log.e("AppMetadataSyncWorker", "Work failed: Missing package name or action.")
            return Result.failure()
        }

        Log.d("AppMetadataSyncWorker", "Starting work for $packageName, action: $action")

        return try {
            when (action) {
                ACTION_INSTALL_OR_UPDATE -> appMetadataRepository.handleAppInstalledOrUpdated(packageName)
                ACTION_UNINSTALL -> appMetadataRepository.handleAppUninstalled(packageName)
                else -> {
                    Log.e("AppMetadataSyncWorker", "Unknown action: $action")
                    return Result.failure()
                }
            }
            Log.d("AppMetadataSyncWorker", "Work finished successfully for $packageName.")
            Result.success()
        } catch (e: Exception) {
            Log.e("AppMetadataSyncWorker", "Work failed for $packageName, will retry.", e)
            Result.retry()
        }
    }
} 