package com.example.scrolltrack

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.example.scrolltrack.db.RawAppEvent
import com.example.scrolltrack.db.RawAppEventDao
import com.example.scrolltrack.util.DateUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class ScrollTrackService : AccessibilityService() {
    private val TAG = "ScrollTrackService"
    private val SERVICE_NOTIFICATION_ID = 1

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    @Inject
    lateinit var rawAppEventDao: RawAppEventDao

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "ScrollTrackService onCreate.")
        startForeground(SERVICE_NOTIFICATION_ID, createServiceNotification())
    }

    private fun createServiceNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        return NotificationCompat.Builder(this, ScrollTrackApplication.SERVICE_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("ScrollTrack is running")
            .setContentText("Actively monitoring scroll events.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100 // ms
        }
        this.serviceInfo = info
        Log.i(TAG, "Accessibility service connected and configured to listen for scroll events.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            return
        }

        val packageName = event.packageName?.toString()
        if (packageName.isNullOrEmpty()) {
            return
        }

        val scrollDelta = (abs(event.scrollDeltaX) + abs(event.scrollDeltaY)).toLong()
        if (scrollDelta == 0L) return

        val timestamp = System.currentTimeMillis()

        serviceScope.launch {
            try {
                val rawEvent = RawAppEvent(
                    packageName = packageName,
                    className = event.className?.toString(),
                    eventType = RawAppEvent.EVENT_TYPE_SCROLL,
                    eventTimestamp = timestamp,
                    eventDateString = DateUtil.formatUtcTimestampToLocalDateString(timestamp),
                    source = RawAppEvent.SOURCE_ACCESSIBILITY,
                    value = scrollDelta
                )
                rawAppEventDao.insertEvent(rawEvent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert scroll event into database.", e)
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.i(TAG, "ScrollTrackService destroyed.")
    }
}