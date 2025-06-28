package com.example.scrolltrack

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.scrolltrack.data.AppMetadataRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ScrollTrackApplication : Application() {

    @Inject
    lateinit var appMetadataRepository: AppMetadataRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val SERVICE_NOTIFICATION_CHANNEL_ID = "scrolltrack_service_channel"
        private const val PREFS_GLOBAL = "ScrollTrackGlobalPrefs"
        private const val KEY_METADATA_SYNC_DONE = "app_metadata_initial_sync_done"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        runInitialAppMetadataSync()
    }

    private fun runInitialAppMetadataSync() {
        val globalPrefs = getSharedPreferences(PREFS_GLOBAL, Context.MODE_PRIVATE)
        val isSyncDone = globalPrefs.getBoolean(KEY_METADATA_SYNC_DONE, false)

        if (!isSyncDone) {
            Log.i("ScrollTrackApplication", "Initial app metadata sync has not been performed. Starting now.")
            applicationScope.launch {
                try {
                    appMetadataRepository.syncAllInstalledApps()
                    globalPrefs.edit().putBoolean(KEY_METADATA_SYNC_DONE, true).apply()
                    Log.i("ScrollTrackApplication", "Initial app metadata sync completed successfully and flag was set.")
                } catch (e: Exception) {
                    Log.e("ScrollTrackApplication", "Initial app metadata sync failed.", e)
                }
            }
        } else {
            Log.d("ScrollTrackApplication", "Initial app metadata sync flag is already set. Skipping.")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "ScrollTrack Service"
            val descriptionText = "Notification channel for the ScrollTrack foreground service."
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(SERVICE_NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
} 