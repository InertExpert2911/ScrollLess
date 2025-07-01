
package com.example.scrolltrack.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.scrolltrack.ScrollTrackService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "Boot completed event received. Starting services.")
            startServices(context)
        }
    }

    private fun startServices(context: Context) {
        // Start the main Accessibility Service
        try {
            val serviceIntent = Intent(context, ScrollTrackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.i(TAG, "Attempted to start ScrollTrackService.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ScrollTrackService on boot.", e)
        }

        // The NotificationListenerService is enabled by the user in settings and managed by the system.
        // We don't need to explicitly start it here. The system will rebind to it as needed.
    }
}
