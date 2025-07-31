package com.example.scrolltrack.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.scrolltrack.ScrollTrackApplication
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Timber.d("Boot completed, re-scheduling workers.")
            // We get the application instance and call our public scheduling method.
            (context.applicationContext as? ScrollTrackApplication)?.setupRecurringWork()
        }
    }
}
