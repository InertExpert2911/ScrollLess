package com.example.scrolltrack

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.util.UsageStatsWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import com.example.scrolltrack.BuildConfig
import com.example.scrolltrack.services.FlushInferredScrollWorker
import com.example.scrolltrack.services.DailyProcessingWorker

@HiltAndroidApp
class ScrollTrackApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var appMetadataRepository: AppMetadataRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val SERVICE_NOTIFICATION_CHANNEL_ID = "scrolltrack_service_channel"
        private const val PREFS_GLOBAL = "ScrollTrackGlobalPrefs"
        private const val KEY_METADATA_SYNC_DONE = "app_metadata_initial_sync_done"
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        createNotificationChannel()
        runInitialAppMetadataSync()
        setupWorkers()
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

    private fun setupWorkers() {
        setupUsageStatsWorker()
        setupFlushInferredScrollWorker()
        setupDailyProcessingWorker()
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

    private fun setupDailyProcessingWorker() {
        val repeatingRequest = PeriodicWorkRequestBuilder<DailyProcessingWorker>(4, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().build()) // Can run even if not idle
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DailyProcessingWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            repeatingRequest
        )
        Timber.d("DailyProcessingWorker scheduled.")
    }

    private fun setupFlushInferredScrollWorker() {
        val repeatingRequest = PeriodicWorkRequestBuilder<FlushInferredScrollWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().build()) // No special constraints
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            FlushInferredScrollWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            repeatingRequest
        )
        Timber.d("FlushInferredScrollWorker scheduled.")
    }

    private fun setupUsageStatsWorker() {
        // This worker runs frequently to pull in the latest system events.
        // It does not need network and should run even if the device is not idle or charging.
        val repeatingRequest = PeriodicWorkRequestBuilder<UsageStatsWorker>(30, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            UsageStatsWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            repeatingRequest
        )
        Timber.d("UsageStatsWorker scheduled.")
    }
} 