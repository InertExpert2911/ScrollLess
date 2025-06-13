package com.example.scrolltrack

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.example.scrolltrack.data.DraftRepository
import com.example.scrolltrack.data.DraftRepositoryImpl
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.util.ConversionUtil
import com.example.scrolltrack.util.DateUtil
import com.example.scrolltrack.util.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class ScrollTrackService : AccessibilityService() {

    private val TAG = "ScrollTrackService"
    private val SCROLL_THRESHOLD = 5 // Ignore scrolls smaller than this many combined (X+Y) pixels
    private val SERVICE_NOTIFICATION_ID = 1

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var dataRepository: ScrollDataRepository
    private lateinit var draftRepository: DraftRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var notificationManager: NotificationManager

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.i(TAG, "Screen OFF detected.")
                    sessionManager.finalizeAndSaveCurrentSession(System.currentTimeMillis(), "SCREEN_OFF")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        dataRepository = (application as ScrollTrackApplication).repository
        draftRepository = DraftRepositoryImpl(applicationContext)
        sessionManager = SessionManager(dataRepository, draftRepository, serviceScope)

        sessionManager.recoverSession()

        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            registerReceiver(screenStateReceiver, filter)

        updateNotificationTextAndStartForeground()
    }

    private fun updateNotificationTextAndStartForeground() {
        serviceScope.launch {
            val todayDateString = DateUtil.getCurrentLocalDateString()
            var distanceText = "Distance Scrolled: N/A"
            var usageText = "Total Usage: N/A"

            try {
                val todayScrollUnits = dataRepository.getTotalScrollForDate(todayDateString).firstOrNull() ?: 0L
                val formattedDistancePair = ConversionUtil.formatScrollDistance(todayScrollUnits, applicationContext)
                distanceText = "Distance Scrolled: ${formattedDistancePair.first}"
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching scroll data for notification", e)
            }

            try {
                val todayUsageMillis = dataRepository.getTotalUsageTimeMillisForDate(todayDateString) ?: 0L
                usageText = "Total Usage: ${DateUtil.formatDuration(todayUsageMillis)}"
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching usage data for notification", e)
            }
            
            withContext(Dispatchers.Main) {
                val notification = createServiceNotification(titleLine = distanceText, contentLine = usageText)
                startForeground(SERVICE_NOTIFICATION_ID, notification)
            }
        }
    }

    private fun createServiceNotification(titleLine: String, contentLine: String): Notification { 
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        return NotificationCompat.Builder(this, ScrollTrackApplication.SERVICE_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(titleLine)
            .setContentText(contentLine)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentLine))
            .setSmallIcon(R.drawable.ic_launcher_foreground) 
            .setContentIntent(pendingIntent)
            .setOngoing(false)
            .build()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_SCROLLED or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 50
        }
        this.serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                    val deltaY = event.scrollDeltaY
                    val deltaX = event.scrollDeltaX
                val totalDelta = abs(deltaY) + abs(deltaX)

                    if (totalDelta > SCROLL_THRESHOLD) {
                    sessionManager.updateCurrentSession(totalDelta)
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                sessionManager.startNewSession(packageName, event.className?.toString(), System.currentTimeMillis())
            }
        }
    }

    override fun onInterrupt() {
        sessionManager.finalizeAndSaveCurrentSession(System.currentTimeMillis(), "SERVICE_INTERRUPT")
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionManager.finalizeAndSaveCurrentSession(System.currentTimeMillis(), "SERVICE_DESTROY")
            unregisterReceiver(screenStateReceiver)
        serviceJob.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}