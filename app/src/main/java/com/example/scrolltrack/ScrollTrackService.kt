package com.example.scrolltrack

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.example.scrolltrack.db.RawAppEvent
import com.example.scrolltrack.data.SettingsRepository
import com.example.scrolltrack.db.RawAppEventDao
import com.example.scrolltrack.util.DateUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.math.abs
import com.example.scrolltrack.util.AppConstants
import timber.log.Timber
import kotlin.math.sqrt
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class ScrollTrackService : AccessibilityService() {
    private val TAG = "ScrollTrackService"
    private val SERVICE_NOTIFICATION_ID = 1

    private val serviceJob = SupervisorJob()
    internal var serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    @Inject
    lateinit var rawAppEventDao: RawAppEventDao

    @Inject
    lateinit var settingsRepository: SettingsRepository


    private val inferredScrollEventCounter = mutableMapOf<String, Int>()
    private val inferredScrollJobs = mutableMapOf<String, Job>()
    private var currentForegroundPackage: String? = null
    private val lastMeasuredScrollTimestamp = ConcurrentHashMap<String, Long>()
    private val lastTypingEventTimestamp = ConcurrentHashMap<String, Long>()
    private val TYPING_DEBOUNCE_MS = 3000L // 3 seconds
    private val MEASURED_SCROLL_COOLDOWN_MS = 500L // 0.5 seconds

    private var scrollFactor = 1.0f

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_UNLOCKED) {
                Timber.tag(TAG).d("ACTION_USER_UNLOCKED received in-service, logging event.")
                logRawEvent(
                    packageName = "android.system.unlock", // Generic package for system events
                    className = null,
                    eventType = RawAppEvent.EVENT_TYPE_USER_UNLOCKED,
                    value = null
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Timber.tag(TAG).i("ScrollTrackService onCreate.")
        startForeground(SERVICE_NOTIFICATION_ID, createServiceNotification())
        val filter = IntentFilter(Intent.ACTION_USER_UNLOCKED)
        registerReceiver(unlockReceiver, filter)

        serviceScope.launch {
            settingsRepository.screenDpi.collect { dpi ->
                scrollFactor = if (dpi > 0) {
                    160f / dpi 
                } else {
                    1.0f
                }
            }
        }
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
            .setSmallIcon(R.drawable.ic_notification_mono) // Use the new icon here
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_FOCUSED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100 // ms
        }
        this.serviceInfo = info
        Timber.tag(TAG).i("Accessibility service connected and configured.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowStateChange(event)
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> handleMeasuredScroll(event, packageName)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> handleInferredScroll(packageName)
            AccessibilityEvent.TYPE_VIEW_CLICKED -> handleGenericEvent(event, RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_CLICKED, packageName)
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> handleGenericEvent(event, RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_FOCUSED, packageName)
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> handleTypingEvent(event, packageName)
        }
    }

    private fun handleWindowStateChange(event: AccessibilityEvent) {
        val newPackageName = event.packageName?.toString()
        if (newPackageName.isNullOrEmpty()) {
            event.source?.recycle()
            return
        }

        val oldPackageName = currentForegroundPackage
        if (newPackageName != oldPackageName && oldPackageName != null) {
            // Immediately process any pending scroll events for the old app
            processInferredScroll(oldPackageName)
        }

        currentForegroundPackage = newPackageName
        Timber.tag(TAG)
            .d("Foreground package updated to: $currentForegroundPackage from window state change.")
        event.source?.recycle()
    }

    private fun handleMeasuredScroll(event: AccessibilityEvent, packageName: String) {
        if (!isNodeScrollable(event.source)) return

        val activePackage = currentForegroundPackage ?: packageName

        // Always cancel inferred scrolls and update timestamp on a measured scroll.
        inferredScrollJobs[activePackage]?.cancel()
        inferredScrollJobs.remove(activePackage)
        inferredScrollEventCounter.remove(activePackage)
        lastMeasuredScrollTimestamp[activePackage] = System.currentTimeMillis()

        // Only log the event if we can get delta values.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (event.scrollDeltaX == 0 && event.scrollDeltaY == 0) return

            val calibratedDeltaX = (event.scrollDeltaX * scrollFactor).toInt()
            val calibratedDeltaY = (event.scrollDeltaY * scrollFactor).toInt()

            logRawEvent(
                activePackage,
                event.className?.toString(),
                RawAppEvent.EVENT_TYPE_SCROLL_MEASURED,
                null, // The 'value' field is no longer needed for scroll
                calibratedDeltaX,
                calibratedDeltaY
            )
        }
    }

    private fun handleTypingEvent(event: AccessibilityEvent, packageName: String) {
        val currentTime = System.currentTimeMillis()
        val lastTime = lastTypingEventTimestamp[packageName] ?: 0L

        if (currentTime - lastTime > TYPING_DEBOUNCE_MS) {
            val activePackage = currentForegroundPackage ?: packageName
            logRawEvent(
                activePackage,
                event.className?.toString(),
                RawAppEvent.EVENT_TYPE_ACCESSIBILITY_TYPING,
                null
            )
            lastTypingEventTimestamp[packageName] = currentTime
        }
    }

    private fun handleGenericEvent(event: AccessibilityEvent, eventType: Int, packageName: String) {
        val activePackage = currentForegroundPackage ?: packageName
        logRawEvent(
            activePackage,
            event.className?.toString(),
            eventType,
            null
        )
    }

    private fun handleInferredScroll(packageName: String) {
        val activePackage = currentForegroundPackage ?: return

        // Cooldown: If a measured scroll happened recently, ignore this inferred event.
        val lastMeasuredTime = lastMeasuredScrollTimestamp[activePackage] ?: 0L
        if (System.currentTimeMillis() - lastMeasuredTime < MEASURED_SCROLL_COOLDOWN_MS) {
            return
        }

        inferredScrollEventCounter[activePackage] = (inferredScrollEventCounter[activePackage] ?: 0) + 1

        inferredScrollJobs[activePackage]?.cancel()
        inferredScrollJobs[activePackage] = serviceScope.launch {
            delay(AppConstants.INFERRED_SCROLL_DEBOUNCE_MS)
            processInferredScroll(activePackage)
        }
    }

    private fun processInferredScroll(packageName: String) {
        val count = inferredScrollEventCounter.remove(packageName) ?: 0
        if (count > 0) {
            val scrollAmount = (sqrt(count.toDouble()) * AppConstants.INFERRED_SCROLL_MULTIPLIER)
            val calibratedAmount = (scrollAmount * scrollFactor).toLong()
            logRawEvent(
                packageName = packageName,
                className = null,
                eventType = RawAppEvent.EVENT_TYPE_SCROLL_INFERRED,
                value = calibratedAmount,
                scrollDeltaX = 0, // Inferred scroll is vertical only for now
                scrollDeltaY = calibratedAmount.toInt()
            )
        }
        inferredScrollJobs.remove(packageName)
    }

    internal fun isNodeScrollable(node: AccessibilityNodeInfo?): Boolean {
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

    private fun logRawEvent(
        packageName: String,
        className: String?,
        eventType: Int,
        value: Long?,
        scrollDeltaX: Int? = null,
        scrollDeltaY: Int? = null
    ) {
        serviceScope.launch {
            val eventToStore = RawAppEvent(
                packageName = packageName,
                className = className,
                eventType = eventType,
                eventTimestamp = System.currentTimeMillis(),
                eventDateString = DateUtil.getCurrentLocalDateString(),
                source = RawAppEvent.SOURCE_ACCESSIBILITY,
                value = value,
                scrollDeltaX = scrollDeltaX,
                scrollDeltaY = scrollDeltaY
            )
            rawAppEventDao.insertEvent(eventToStore)
        }
    }

    private fun flushAllPendingScrolls() {
        // Make a copy of the keys to avoid ConcurrentModificationException
        val packagesToFlush = inferredScrollEventCounter.keys.toList()
        packagesToFlush.forEach { packageName ->
            processInferredScroll(packageName)
        }
    }

    override fun onInterrupt() {
        Timber.tag(TAG).w("Accessibility service interrupted. Flushing any pending data.")
        flushAllPendingScrolls()
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.tag(TAG).d("ScrollTrackService destroying. Flushing any pending data.")
        flushAllPendingScrolls()
        unregisterReceiver(unlockReceiver)
        serviceJob.cancel()
        Timber.tag(TAG).d("ScrollTrackService destroyed.")
    }
}
