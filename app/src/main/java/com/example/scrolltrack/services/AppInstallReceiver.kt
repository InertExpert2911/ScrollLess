package com.example.scrolltrack.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AppInstallReceiver : BroadcastReceiver() {

    private val TAG = "AppInstallReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val packageName = intent.data?.schemeSpecificPart ?: run {
            pendingResult.finish()
            return
        }

        val action = when (intent.action) {
            Intent.ACTION_PACKAGE_REMOVED -> if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) AppMetadataSyncWorker.ACTION_UNINSTALL else null
            Intent.ACTION_PACKAGE_ADDED -> if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) AppMetadataSyncWorker.ACTION_INSTALL_OR_UPDATE else null
            Intent.ACTION_PACKAGE_REPLACED -> AppMetadataSyncWorker.ACTION_INSTALL_OR_UPDATE
            else -> null
        }

        if (action != null) {
            Log.d(TAG, "Enqueuing metadata sync work for $packageName, action: $action")
            val workRequest = OneTimeWorkRequestBuilder<AppMetadataSyncWorker>()
                .setInputData(workDataOf(
                    AppMetadataSyncWorker.KEY_PACKAGE_NAME to packageName,
                    AppMetadataSyncWorker.KEY_ACTION to action
                ))
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        }
        
        pendingResult.finish()
    }
} 