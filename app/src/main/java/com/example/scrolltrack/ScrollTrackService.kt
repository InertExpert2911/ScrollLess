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
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.db.RawAppEvent
import com.example.scrolltrack.db.RawAppEventDao
import com.example.scrolltrack.util.ConversionUtil
import com.example.scrolltrack.util.DateUtil
import com.example.scrolltrack.util.SessionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class ScrollTrackService : AccessibilityService() {
    private val TAG = "ScrollTrackService"
    private val SCROLL_THRESHOLD = 5 // Ignore scrolls smaller than this many combined (X+Y) pixels
    private val SERVICE_NOTIFICATION_ID = 1 // Notification ID for the foreground service

    // CoroutineScope for notification updates. The service manages its own lifecycle for this.
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    // DAOs and Repositories will be initialized in onCreate
    @Inject
    lateinit var rawAppEventDao: RawAppEventDao
    @Inject
    lateinit var dataRepository: ScrollDataRepository // For notification updates
    @Inject
    lateinit var sessionManager: SessionManager
    private lateinit var notificationManager: NotificationManager

    // Screen On/Off Receiver
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.i(TAG, "Screen OFF detected.")
                    sessionManager.finalizeAndSaveCurrentSession(System.currentTimeMillis(), SessionManager.SessionEndReason.SCREEN_OFF)
                    sessionManager.resetCurrentSessionState() // Ensure service's view of current app is also reset
                }
                Intent.ACTION_SCREEN_ON -> {
                    Log.i(TAG, "Screen ON detected.")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        Log.i(TAG, "ScrollTrackService onCreate: Components injected by Hilt.")
        sessionManager.recoverSession()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag") // For older APIs
            registerReceiver(screenStateReceiver, filter)
        }
        Log.i(TAG, "Screen state receiver registered.")

        updateNotificationTextAndStartForeground()
        Log.i(TAG, "Service started in foreground with initial notification.")
    }

    // This function remains in the service as it directly interacts with its DataRepository
    // and NotificationManager for UI updates.
    private fun updateNotificationTextAndStartForeground() {
        serviceScope.launch {
            val todayDateString = DateUtil.getCurrentLocalDateString()
            var distanceText = "Distance Scrolled: N/A" // This will be the notification's main title
            var usageText = "Total Usage: N/A"    // This will be the content line below the title

            try {
                val todayScrollUnits = dataRepository.getTotalScrollForDate(todayDateString).firstOrNull() ?: 0L
                val formattedDistancePair = ConversionUtil.formatScrollDistance(todayScrollUnits, applicationContext)
                distanceText = "Distance Scrolled: ${formattedDistancePair.first}"
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching scroll data for notification", e)
            }

            try {
                val todayUsageMillis = dataRepository.getTotalUsageTimeMillisForDate(todayDateString).firstOrNull() ?: 0L
                usageText = "Total Usage: ${DateUtil.formatDuration(todayUsageMillis)}"
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching usage data for notification", e)
            }
            
            withContext(Dispatchers.Main) {
                val notification = createServiceNotification(titleLine = distanceText, 
                                                           contentLine = usageText)
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
        Log.i(TAG, "ScrollTrackService connected")
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_FOCUSED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 50
        }
        this.serviceInfo = info
        Log.i(TAG, "Service configured.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) {
            return
        }

        val actualEventTimeUTC = DateUtil.getUtcTimestamp()
        val sourceNodePackageName = event.source?.packageName?.toString()
        val directEventPackageName = event.packageName?.toString()
        val determinedPackageName = sourceNodePackageName ?: directEventPackageName
        val eventClassName = event.className?.toString()

        // Helper function to log RawAppEvents (remains in service, uses service's rawAppEventDao)
        fun logAccessibilityRawEventToDb(eventTypeConst: Int, pkgName: String?, clsName: String?, time: Long) {
            if (pkgName.isNullOrEmpty()) {
                Log.w(TAG, "Cannot log accessibility event to DB, package name is null or empty. Type: $eventTypeConst")
                return
            }
            serviceScope.launch {
                try {
                    val rawEvent = RawAppEvent(
                        packageName = pkgName, className = clsName,
                        eventType = eventTypeConst, eventTimestamp = time,
                        eventDateString = DateUtil.formatUtcTimestampToLocalDateString(time)
                    )
                    rawAppEventDao.insertEvent(rawEvent)
                    Log.d(TAG, "Logged RawAppEvent to DB: Pkg=$pkgName, Cls=$clsName, Type=$eventTypeConst, Time=$time")
                } catch (e: Exception) {
                    Log.e(TAG, "Error logging RawAppEvent to DB", e)
                }
            }
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (determinedPackageName == null) return

                val currentPkg = sessionManager.getCurrentAppPackage()
                if (currentPkg == null || determinedPackageName != currentPkg) {
                    sessionManager.startNewSession(determinedPackageName, eventClassName, actualEventTimeUTC)
                }

                // Check again if the package matches after potential new session start
                if (determinedPackageName == sessionManager.getCurrentAppPackage()) {
                    val deltaY = event.scrollDeltaY
                    val deltaX = event.scrollDeltaX
                    val totalDelta = abs(deltaY) + abs(deltaX)

                    if (totalDelta > SCROLL_THRESHOLD) {
                        sessionManager.updateCurrentSessionScroll(totalDelta.toLong())
                        logAccessibilityRawEventToDb(RawAppEvent.EVENT_TYPE_ACCESSIBILITY_SCROLLED, determinedPackageName, eventClassName, actualEventTimeUTC)
                        // SessionManager's updateCurrentSessionScroll now handles draft saving
                    } else if (deltaY != 0 || deltaX != 0) {
                        Log.v(TAG, "Scroll in $determinedPackageName ($eventClassName) below threshold: Delta(X:$deltaX,Y:$deltaY)")
                    }
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                val activeWindowIdentifier = event.source?.paneTitle?.toString() ?: eventClassName
                Log.i(TAG, "Window Change: Pkg=$determinedPackageName, Class/Title=$activeWindowIdentifier, EventType=${AccessibilityEvent.eventTypeToString(event.eventType)}")

                if (determinedPackageName != null) {
                    sessionManager.startNewSession(determinedPackageName, eventClassName, actualEventTimeUTC)
                }
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (determinedPackageName != null) {
                    Log.d(TAG, "VIEW_CLICKED in $determinedPackageName. Class: $eventClassName")
                    logAccessibilityRawEventToDb(RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_CLICKED, determinedPackageName, eventClassName, actualEventTimeUTC)
                }
            }
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                if (determinedPackageName != null) {
                    Log.d(TAG, "VIEW_FOCUSED in $determinedPackageName. Class: $eventClassName")
                    logAccessibilityRawEventToDb(RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_FOCUSED, determinedPackageName, eventClassName, actualEventTimeUTC)
                }
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                if (determinedPackageName != null) {
                    Log.d(TAG, "VIEW_TEXT_CHANGED in $determinedPackageName. Class: $eventClassName")
                    logAccessibilityRawEventToDb(RawAppEvent.EVENT_TYPE_ACCESSIBILITY_TYPING, determinedPackageName, eventClassName, actualEventTimeUTC)
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted.")
        sessionManager.handleServiceStop(SessionManager.SessionEndReason.SERVICE_INTERRUPT)
    }

    override fun onDestroy() {
        Log.i(TAG, "Service destroying.")
        sessionManager.handleServiceStop(SessionManager.SessionEndReason.SERVICE_DESTROY)

        try {
            unregisterReceiver(screenStateReceiver)
            Log.i(TAG, "Screen state receiver unregistered.")
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering screen state receiver: ${e.message}")
        }

        serviceJob.cancel()
        Log.i(TAG, "Service scope cancelled.")
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}