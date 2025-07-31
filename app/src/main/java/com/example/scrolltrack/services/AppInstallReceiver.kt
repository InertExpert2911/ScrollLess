package com.example.scrolltrack.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.scrolltrack.data.LimitsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class AppInstallReceiver : BroadcastReceiver() {

    @Inject
    lateinit var limitsRepository: LimitsRepository
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        // We only care about the package removed event
        if (intent.action == Intent.ACTION_PACKAGE_REMOVED) {
            // The uninstalled app's package name is in the intent data
            val packageName = intent.data?.schemeSpecificPart ?: return

            Timber.d("Package removed: $packageName. Removing from any limit groups.")
            // Launch a coroutine to call the suspend function in the repository
            scope.launch {
                limitsRepository.removeAppLimit(packageName)
            }
        }
    }
}