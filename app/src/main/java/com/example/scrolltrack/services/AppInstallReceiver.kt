package com.example.scrolltrack.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.scrolltrack.data.AppMetadataRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AppInstallReceiver : BroadcastReceiver() {

    @Inject
    lateinit var appMetadataRepository: AppMetadataRepository
    private val scope = CoroutineScope(Dispatchers.IO)

    private val TAG = "AppInstallReceiver"

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || context == null) return

        val packageName = intent.data?.schemeSpecificPart ?: return

        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                // This covers both new installs and updates for apps that were fully uninstalled.
                // The ACTION_PACKAGE_REPLACED is for existing apps being updated.
                val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                if (!isReplacing) {
                    Log.i(TAG, "New package installed: $packageName. Updating metadata.")
                    scope.launch {
                        appMetadataRepository.handleAppInstalledOrUpdated(packageName)
                    }
                }
            }
            Intent.ACTION_PACKAGE_REPLACED -> {
                Log.i(TAG, "Package updated: $packageName. Updating metadata.")
                scope.launch {
                    appMetadataRepository.handleAppInstalledOrUpdated(packageName)
                }
            }
            Intent.ACTION_PACKAGE_REMOVED -> {
                val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                if (!isReplacing) {
                    Log.i(TAG, "Package removed: $packageName. Marking as uninstalled.")
                    scope.launch {
                        appMetadataRepository.handleAppUninstalled(packageName)
                    }
                }
            }
        }
    }
} 