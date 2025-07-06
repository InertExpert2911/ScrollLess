package com.example.scrolltrack

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.example.scrolltrack.db.RawAppEvent
import com.example.scrolltrack.db.RawAppEventDao
import com.example.scrolltrack.util.DateUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.math.abs
import com.example.scrolltrack.util.AppConstants
import kotlin.math.sqrt

@AndroidEntryPoint
class ScrollTrackService : AccessibilityService() {
    private val TAG = "ScrollTrackService"
    private val SERVICE_NOTIFICATION_ID = 1

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    @Inject
    lateinit var rawAppEventDao: RawAppEventDao

    private val inferredScrollCounters = ConcurrentHashMap<String, Int>()
    private val inferredScrollJobs = ConcurrentHashMap<String, Job>()

    private var currentForegroundPackage: String? = null

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
            eventTypes = AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100 // ms
        }
        this.serviceInfo = info
        Log.i(TAG, "Accessibility service connected and configured.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowStateChange(event)
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> handleMeasuredScroll(event, packageName)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> handleInferredScroll(event, packageName)
        }
    }

    private fun handleWindowStateChange(event: AccessibilityEvent) {
        val sourceNode = event.source
        if (sourceNode != null && sourceNode.isFocused) {
            currentForegroundPackage = sourceNode.packageName?.toString()
            Log.d(TAG, "Foreground package updated to: $currentForegroundPackage")
        }
        sourceNode?.recycle()
    }

    private fun handleMeasuredScroll(event: AccessibilityEvent, packageName: String) {
        if (!isNodeScrollable(event.source)) return

        val scrollDelta = (abs(event.scrollDeltaX) + abs(event.scrollDeltaY)).toLong()
        if (scrollDelta == 0L) return

        val activePackage = currentForegroundPackage ?: packageName
        logRawEvent(
            activePackage,
            event.className?.toString(),
            RawAppEvent.EVENT_TYPE_SCROLL_MEASURED,
            scrollDelta
        )
    }

    private fun handleInferredScroll(event: AccessibilityEvent, packageName: String) {
        if (!isNodeScrollable(event.source)) return

        val activePackage = currentForegroundPackage ?: return
        inferredScrollCounters.merge(activePackage, 1, Int::plus)

        inferredScrollJobs[activePackage]?.cancel()
        inferredScrollJobs[activePackage] = serviceScope.launch {
            delay(AppConstants.INFERRED_SCROLL_DEBOUNCE_MS)
            val count = inferredScrollCounters.remove(activePackage) ?: 0
            if (count > 0) {
                val inferredValue = (sqrt(count.toDouble()) * AppConstants.INFERRED_SCROLL_MULTIPLIER).toLong()
                logRawEvent(
                    activePackage,
                    event.className?.toString(),
                    RawAppEvent.EVENT_TYPE_SCROLL_INFERRED,
                    inferredValue
                )
            }
            inferredScrollJobs.remove(activePackage)
        }
    }

    private fun isNodeScrollable(node: AccessibilityNodeInfo?): Boolean {
        var currentNode = node
        var depth = 0
        while (currentNode != null && depth < 10) {
            if (currentNode.isScrollable) {
                currentNode.recycle()
                return true
            }
            val parent = currentNode.parent
            currentNode.recycle()
            currentNode = parent
            depth++
        }
        return false
    }

    private fun logRawEvent(packageName: String, className: String?, eventType: Int, value: Long?) {
        serviceScope.launch {
            val eventToStore = RawAppEvent(
                packageName = packageName,
                className = className,
                eventType = eventType,
                eventTimestamp = System.currentTimeMillis(),
                eventDateString = DateUtil.getCurrentLocalDateString(),
                source = RawAppEvent.SOURCE_ACCESSIBILITY,
                value = value
            )
            rawAppEventDao.insertEvent(eventToStore)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        Log.d(TAG, "ScrollTrackService destroyed.")
    }
}