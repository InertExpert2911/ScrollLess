package com.example.scrolltrack.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.scrolltrack.db.NotificationDao
import com.example.scrolltrack.db.NotificationRecord
import com.example.scrolltrack.util.DateUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationListener : NotificationListenerService() {

    private val TAG = "NotificationListener"
    private val job = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + job)

    @Inject
    lateinit var notificationDao: NotificationDao

    override fun onCreate() {
        super.onCreate()
        // Hilt injects notificationDao before onCreate is called.
        Log.i(TAG, "NotificationListenerService created and dao component initialized by Hilt.")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // The lateinit var is guaranteed to be initialized here in a Hilt entry point.
        if (sbn == null) return

        val packageName = sbn.packageName
        if (packageName == applicationContext.packageName) {
            return // Don't log our own notifications
        }

        val notification = sbn.notification
        val title = notification.extras.getString("android.title")
        val text = notification.extras.getCharSequence("android.text")?.toString()
        val postTime = sbn.postTime
        val category = notification.category

        Log.d(TAG, "Notification Posted: Pkg=$packageName, Category=$category, Title='$title'")

        serviceScope.launch {
            try {
                val record = NotificationRecord(
                    packageName = packageName,
                    postTimeUTC = postTime,
                    title = title,
                    text = text,
                    category = category,
                    dateString = DateUtil.formatUtcTimestampToLocalDateString(postTime)
                )
                notificationDao.insertNotification(record)
                Log.d(TAG, "Notification from $packageName saved to database.")

            } catch (e: Exception) {
                Log.e(TAG, "Error processing notification for $packageName", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        Log.i(TAG, "NotificationListenerService destroyed.")
    }
} 