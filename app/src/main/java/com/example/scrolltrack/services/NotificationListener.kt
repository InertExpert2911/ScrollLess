package com.example.scrolltrack.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.scrolltrack.ScrollTrackApplication
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.db.AppDatabase
import com.example.scrolltrack.db.NotificationDao
import com.example.scrolltrack.db.NotificationRecord
import com.example.scrolltrack.util.DateUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NotificationListener : NotificationListenerService() {

    private val TAG = "NotificationListener"
    private val job = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + job)

    private lateinit var notificationDao: NotificationDao
    private lateinit var repository: ScrollDataRepository

    override fun onCreate() {
        super.onCreate()
        try {
            // Use the application-level repository instance
            repository = (application as ScrollTrackApplication).repository
            notificationDao = AppDatabase.getDatabase(applicationContext).notificationDao()
            Log.i(TAG, "NotificationListenerService created and components initialized.")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing components in NotificationListenerService", e)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || !::notificationDao.isInitialized || !::repository.isInitialized) return

        val packageName = sbn.packageName
        if (packageName == applicationContext.packageName) {
            return // Don't log our own notifications
        }

        val notification = sbn.notification
        val title = notification.extras.getString("android.title")
        val text = notification.extras.getCharSequence("android.text")?.toString()
        val postTime = sbn.postTime

        Log.d(TAG, "Notification Posted: Pkg=$packageName, Title='$title', Text='$text'")

        serviceScope.launch {
            try {
                val record = NotificationRecord(
                    packageName = packageName,
                    postTimeUTC = postTime,
                    title = title,
                    text = text,
                    dateString = DateUtil.formatUtcTimestampToLocalDateString(postTime)
                )
                notificationDao.insertNotification(record)
                Log.d(TAG, "Notification from $packageName saved to database.")

                // Increment the daily counter
                repository.incrementNotificationCount(packageName)
                Log.i(TAG, "Incremented notification count for $packageName.")

            } catch (e: Exception) {
                Log.e(TAG, "Error processing notification for $packageName", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // We can add logic here later if we want to track dismissals
        super.onNotificationRemoved(sbn)
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        Log.i(TAG, "NotificationListenerService destroyed.")
    }
} 