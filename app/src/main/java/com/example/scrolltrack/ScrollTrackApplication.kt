package com.example.scrolltrack

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.room.Room
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.data.ScrollDataRepositoryImpl
import com.example.scrolltrack.db.AppDatabase

class ScrollTrackApplication : Application() {

    companion object {
        const val SERVICE_NOTIFICATION_CHANNEL_ID = "scrolltrack_service_channel"
    }

    // Database instance
    private val database: AppDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "scroll_track_database"
        ).fallbackToDestructiveMigration() // Handle schema changes by destroying and recreating the DB
         .build()
    }

    // Repository instance
    val repository: ScrollDataRepository by lazy {
        ScrollDataRepositoryImpl(
            database.scrollSessionDao(),
            database.dailyAppUsageDao(),
            database.rawAppEventDao(),
            database.notificationDao(),
            this
        )
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
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