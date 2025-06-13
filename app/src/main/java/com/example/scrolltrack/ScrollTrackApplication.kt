package com.example.scrolltrack

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.scrolltrack.data.DefaultScrollDataRepository
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.data.UsageStatsProcessor
import com.example.scrolltrack.db.AppDatabase

class ScrollTrackApplication : Application() {

    // Using by lazy so the database and the repository are only created when they're needed
    // rather than when the application starts
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository: ScrollDataRepository by lazy {
        DefaultScrollDataRepository(
            database.scrollSessionDao(),
            database.dailyAppUsageDao(),
            UsageStatsProcessor(this),
            this
        )
    }

    companion object {
        const val SERVICE_NOTIFICATION_CHANNEL_ID = "ScrollTrackServiceChannel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "ScrollTrack Service"
            val descriptionText = "Notifications for the ScrollTrack foreground service"
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