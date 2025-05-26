package com.example.scrolltrack

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class ScrollTrackApplication : Application() {

    companion object {
        const val SERVICE_NOTIFICATION_CHANNEL_ID = "ScrollTrackServiceChannel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                SERVICE_NOTIFICATION_CHANNEL_ID,
                "ScrollTrack Service Channel", // User-visible name
                NotificationManager.IMPORTANCE_DEFAULT // Importance level
            )
            serviceChannel.description = "Channel for ScrollTrack foreground service notification" // User-visible description

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
} 