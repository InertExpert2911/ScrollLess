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
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.data.ScrollDataRepositoryImpl
import com.example.scrolltrack.db.AppDatabase
import com.example.scrolltrack.db.DailyAppUsageDao
import com.example.scrolltrack.db.ScrollSessionDao
import com.example.scrolltrack.db.ScrollSessionRecord
import com.example.scrolltrack.util.ConversionUtil
import com.example.scrolltrack.util.DateUtil
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
    private val SERVICE_NOTIFICATION_ID = 1 // Notification ID for the foreground service
    // private val NOTIFICATION_UPDATE_INTERVAL_MS = 5 * 60 * 1000L // Removed

    // CoroutineScope for database operations
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var scrollSessionDao: ScrollSessionDao
    private lateinit var dailyAppUsageDao: DailyAppUsageDao
    private lateinit var dataRepository: ScrollDataRepository
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var notificationManager: NotificationManager
    // private val notificationUpdateHandler = Handler(Looper.getMainLooper()) // Removed

    // Session tracking variables
    private var currentAppPackage: String? = null
    private var currentAppActivity: String? = null
    private var currentAppScrollAccumulator: Long = 0L
    private var currentSessionStartTime: Long = 0L

    // --- SharedPreferences Draft Constants ---
    private companion object {
        const val PREFS_NAME = "ScrollTrackPrefs"
        const val KEY_DRAFT_PKG = "draft_package_name"
        const val KEY_DRAFT_ACTIVITY = "draft_activity_name"
        const val KEY_DRAFT_SCROLL = "draft_scroll_amount"
        const val KEY_DRAFT_START_TIME = "draft_start_time"
        const val KEY_DRAFT_LAST_UPDATE = "draft_last_update_time"
    }
    // --- End SharedPreferences Draft Constants ---

    // --- Session End Reasons ---
    object SessionEndReason {
        const val APP_SWITCH = "APP_SWITCH"
        const val SCREEN_OFF = "SCREEN_OFF"
        const val SERVICE_INTERRUPT = "SERVICE_INTERRUPT"
        const val SERVICE_DESTROY = "SERVICE_DESTROY"
        const val RECOVERED_DRAFT = "RECOVERED_DRAFT"
    }
    // --- End Session End Reasons ---

    // --- Screen On/Off Receiver ---
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.i(TAG, "Screen OFF detected.")
                    finalizeAndSaveCurrentSession(System.currentTimeMillis(), SessionEndReason.SCREEN_OFF)
                    currentAppPackage = null
                    currentAppActivity = null
                    // Draft is cleared by finalizeAndSaveCurrentSession upon successful DB write
                }
                Intent.ACTION_SCREEN_ON -> {
                    Log.i(TAG, "Screen ON detected.")
                    // New session will likely start via a subsequent window change event
                }
            }
        }
    }
    // --- End Screen On/Off Receiver ---

    // --- Periodic SharedPreferences Save ---
    private val sharedPrefsHandler = Handler(Looper.getMainLooper())
    private var pendingSharedPrefsWrite: Runnable? = null
    private val SHARED_PREFS_SAVE_INTERVAL_MS = 30000L // 30 seconds
    // --- End Periodic SharedPreferences Save ---

    override fun onCreate() {
        super.onCreate()
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        try {
            val db = AppDatabase.getDatabase(applicationContext)
            scrollSessionDao = db.scrollSessionDao()
            dailyAppUsageDao = db.dailyAppUsageDao()
            dataRepository = ScrollDataRepositoryImpl(scrollSessionDao, dailyAppUsageDao, application)

            Log.i(TAG, "ScrollTrackService onCreate: DAO and Repository initialized.")
            recoverSessionFromPrefs() // Attempt to recover any draft session
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing DAO/Repository in onCreate", e)
            // Consider how to handle this critical failure
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenStateReceiver, filter)
        }
        Log.i(TAG, "Screen state receiver registered.")

        // Initial notification creation
        updateNotificationTextAndStartForeground()
        // Log.i(TAG, "Service started in foreground and notification updater scheduled.") // Comment removed
        Log.i(TAG, "Service started in foreground with initial notification.")
    }

    private fun updateNotificationTextAndStartForeground() {
        serviceScope.launch {
            val todayDateString = DateUtil.getCurrentDateString()
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
                val todayUsageMillis = dataRepository.getTotalUsageTimeMillisForDate(todayDateString) ?: 0L
                usageText = "Total Usage: ${DateUtil.formatDuration(todayUsageMillis)}"
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching usage data for notification", e)
            }
            
            withContext(Dispatchers.Main) {
                // distanceText is the title, usageText is the content.
                // BigTextStyle will ensure usageText is displayed fully when expanded.
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
            .setContentTitle(titleLine)      // "Distance Scrolled: X m"
            .setContentText(contentLine)     // "Total Usage: Y hr Z min"
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentLine)) // Ensures full contentLine is visible when expanded
            .setSmallIcon(R.drawable.ic_launcher_foreground) 
            .setContentIntent(pendingIntent)
            .setOngoing(false) // Remains dismissable
            .build()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "ScrollTrackService connected")
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 50 // Kept at 50ms as requested
        }
        this.serviceInfo = info
        Log.i(TAG, "Service configured.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val eventPackageName = event.packageName?.toString()
        val eventClassName = event.className?.toString()
        val eventTime = System.currentTimeMillis()

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (eventPackageName == null) return

                if (currentAppPackage == null || eventPackageName != currentAppPackage) {
                    finalizeAndSaveCurrentSession(eventTime, SessionEndReason.APP_SWITCH)
                    startNewSession(eventPackageName, eventClassName, eventTime)
                }

                if (eventPackageName == currentAppPackage) {
                    val deltaY = event.scrollDeltaY
                    val deltaX = event.scrollDeltaX

                    val verticalScroll = if (deltaY != -1) abs(deltaY) else 0
                    val horizontalScroll = if (deltaX != -1) abs(deltaX) else 0
                    val totalDelta = verticalScroll + horizontalScroll

                    if (totalDelta > SCROLL_THRESHOLD) {
                        currentAppScrollAccumulator += totalDelta
                        Log.d(
                            TAG,
                            "Scroll in $eventPackageName ($currentAppActivity): Delta(X:$deltaX,Y:$deltaY), Added:$totalDelta, SessionTotal:$currentAppScrollAccumulator"
                        )
                        scheduleSharedPrefsWrite()
                    }
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                val sourceNodeInfo = event.source
                val activeWindowIdentifier = sourceNodeInfo?.paneTitle?.toString() ?: eventClassName
                sourceNodeInfo?.recycle()

                Log.i(
                    TAG,
                    "Window Change: Pkg=$eventPackageName, Class/Title=$activeWindowIdentifier, EventType=${AccessibilityEvent.eventTypeToString(event.eventType)}"
                )

                if (eventPackageName != null && eventPackageName != currentAppPackage) {
                    finalizeAndSaveCurrentSession(eventTime, SessionEndReason.APP_SWITCH)
                    startNewSession(eventPackageName, eventClassName, eventTime)
                } else if (eventPackageName != null && eventPackageName == currentAppPackage && eventClassName != null && eventClassName != currentAppActivity) {
                    Log.d(TAG, "Activity/Window change within $currentAppPackage: from $currentAppActivity to $eventClassName")
                    currentAppActivity = eventClassName
                    scheduleSharedPrefsWrite()
                }
            }
        }
    }

    private fun startNewSession(packageName: String, activityName: String?, startTime: Long) {
        // Ensure any truly previous session is saved before starting a new one
        if (currentAppPackage != null && currentAppPackage != packageName && currentAppScrollAccumulator > 0) {
            finalizeAndSaveCurrentSession(startTime -1 , SessionEndReason.APP_SWITCH)
        }

        currentAppPackage = packageName
        currentAppActivity = activityName
        currentAppScrollAccumulator = 0L
        currentSessionStartTime = startTime
        Log.i(TAG, "NEW SESSION: App: $currentAppPackage, Activity: $currentAppActivity at $startTime. Accumulator reset.")
        // Initial draft save for the new session (optional, could wait for first scroll)
        // saveDraftToPrefs() // Comment and call removed
    }

    private fun finalizeAndSaveCurrentSession(sessionEndTime: Long, reason: String) {
        val pkgToSave = currentAppPackage
        val activityToSave = currentAppActivity
        val amountToSave = currentAppScrollAccumulator
        val startTimeToSave = currentSessionStartTime

        // Reset trackers before potential async operation to avoid race conditions
        // but only if we are certain this session is ending.
        // If a new session starts immediately, new values will be set.
        if (pkgToSave != null) { // Only proceed if there was an active package
            currentAppPackage = null // Mark session as ended for internal logic
            currentAppActivity = null
            currentAppScrollAccumulator = 0L
            currentSessionStartTime = 0L
            pendingSharedPrefsWrite?.let { sharedPrefsHandler.removeCallbacks(it) }
        }


        if (pkgToSave != null && amountToSave > 0 && startTimeToSave > 0) {
            val sessionRecord = ScrollSessionRecord(
                packageName = pkgToSave,
                scrollAmount = amountToSave,
                sessionStartTime = startTimeToSave,
                sessionEndTime = sessionEndTime,
                date = DateUtil.formatDate(startTimeToSave),
                sessionEndReason = reason
            )
            Log.i(TAG, "SESSION END ($reason): App: $pkgToSave, Activity: $activityToSave, Scroll: $amountToSave. Saving.")
            serviceScope.launch {
                try {
                    scrollSessionDao.insertSession(sessionRecord)
                    Log.i(TAG, "DB WRITE: Success for $pkgToSave, Amount: $amountToSave, Reason: $reason")
                    clearDraftFromPrefs() // Clear draft AFTER successful DB write
                } catch (e: Exception) {
                    Log.e(TAG, "DB WRITE: Error for $pkgToSave. Draft remains in SharedPreferences.", e)
                }
            }
        } else if (pkgToSave != null) { // Session ended, but no (or not enough) scroll
            Log.i(TAG, "SESSION END ($reason): App: $pkgToSave, Activity: $activityToSave. No scroll data to save (Amount: $amountToSave). Clearing any draft.")
            clearDraftFromPrefs() // Clear any existing draft for this package if it wasn't saved
        }
    }

    private fun scheduleSharedPrefsWrite() {
        if (currentAppPackage == null || currentSessionStartTime == 0L) {
            // No active session to save a draft for
            return
        }
        pendingSharedPrefsWrite?.let { sharedPrefsHandler.removeCallbacks(it) }
        pendingSharedPrefsWrite = Runnable { saveDraftToPrefs() }
        sharedPrefsHandler.postDelayed(pendingSharedPrefsWrite!!, SHARED_PREFS_SAVE_INTERVAL_MS)
        Log.v(TAG, "PREFS SAVE: Scheduled draft save for $currentAppPackage in ${SHARED_PREFS_SAVE_INTERVAL_MS}ms")
    }

    private fun saveDraftToPrefs() {
        if (currentAppPackage != null && currentSessionStartTime != 0L) {
            Log.i(TAG, "PREFS SAVE: Saving draft for $currentAppPackage, Amount: $currentAppScrollAccumulator, Start: $currentSessionStartTime")
            with(sharedPreferences.edit()) {
                putString(KEY_DRAFT_PKG, currentAppPackage)
                putString(KEY_DRAFT_ACTIVITY, currentAppActivity)
                putLong(KEY_DRAFT_SCROLL, currentAppScrollAccumulator)
                putLong(KEY_DRAFT_START_TIME, currentSessionStartTime)
                putLong(KEY_DRAFT_LAST_UPDATE, System.currentTimeMillis())
                apply() // Use apply() for asynchronous save
            }
        } else {
            Log.d(TAG, "PREFS SAVE: Skipped, no active session or start time.")
        }
    }

    private fun recoverSessionFromPrefs() {
        val draftPkg = sharedPreferences.getString(KEY_DRAFT_PKG, null)
        if (draftPkg != null) {
            val draftActivity = sharedPreferences.getString(KEY_DRAFT_ACTIVITY, null)
            val draftScroll = sharedPreferences.getLong(KEY_DRAFT_SCROLL, 0L)
            val draftStartTime = sharedPreferences.getLong(KEY_DRAFT_START_TIME, 0L)
            val draftLastUpdate = sharedPreferences.getLong(KEY_DRAFT_LAST_UPDATE, System.currentTimeMillis()) // Default to now if not found

            if (draftScroll > 0 && draftStartTime > 0) { // Only recover if there's actual scroll and start time
                Log.i(TAG, "PREFS RECOVERY: Found draft for $draftPkg, Scroll: $draftScroll. Recovering.")
                val recoveredSession = ScrollSessionRecord(
                    packageName = draftPkg,
                    scrollAmount = draftScroll,
                    sessionStartTime = draftStartTime,
                    sessionEndTime = draftLastUpdate,
                    date = DateUtil.formatDate(draftStartTime),
                    sessionEndReason = SessionEndReason.RECOVERED_DRAFT
                )
                serviceScope.launch {
                    try {
                        scrollSessionDao.insertSession(recoveredSession)
                        Log.i(TAG, "DB WRITE (Recovery): Success for $draftPkg")
                        clearDraftFromPrefs() // Clear draft after successful recovery and DB save
                    } catch (e: Exception) {
                        Log.e(TAG, "DB WRITE (Recovery): Error for $draftPkg. Draft remains.", e)
                    }
                }
            } else {
                Log.i(TAG, "PREFS RECOVERY: Draft found for $draftPkg but was empty/invalid. Clearing draft.")
                clearDraftFromPrefs()
            }
        } else {
            Log.i(TAG, "PREFS RECOVERY: No draft session found.")
        }
    }

    private fun clearDraftFromPrefs() {
        Log.i(TAG, "PREFS CLEAR: Clearing draft session from SharedPreferences for package: ${sharedPreferences.getString(KEY_DRAFT_PKG, "N/A")}")
        with(sharedPreferences.edit()) {
            remove(KEY_DRAFT_PKG)
            remove(KEY_DRAFT_ACTIVITY)
            remove(KEY_DRAFT_SCROLL)
            remove(KEY_DRAFT_START_TIME)
            remove(KEY_DRAFT_LAST_UPDATE)
            apply()
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted. Attempting to save pending data.")
        finalizeAndSaveCurrentSession(System.currentTimeMillis(), SessionEndReason.SERVICE_INTERRUPT)
        // super.onInterrupt() // Removed as it's abstract in API 35
    }

    override fun onDestroy() {
        Log.i(TAG, "Service destroying. Attempting to save final pending data & cleaning up.")
        // notificationUpdateHandler.removeCallbacks(notificationUpdateRunnable) // Removed
        // Before finalizing, make one last attempt to write current state to prefs,
        // in case finalizeAndSaveCurrentSession's DB write fails and service is killed.
        if (currentAppPackage != null && currentAppScrollAccumulator > 0 && currentSessionStartTime > 0) {
            saveDraftToPrefs()
        }
        finalizeAndSaveCurrentSession(System.currentTimeMillis(), SessionEndReason.SERVICE_DESTROY)

        try {
            unregisterReceiver(screenStateReceiver)
            Log.i(TAG, "Screen state receiver unregistered.")
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering screen state receiver: ${e.message}")
        }

        pendingSharedPrefsWrite?.let { sharedPrefsHandler.removeCallbacks(it) }
        serviceJob.cancel()
        Log.i(TAG, "Service scope cancelled.")
        stopForeground(STOP_FOREGROUND_REMOVE) // Use STOP_FOREGROUND_REMOVE to remove the notification
        super.onDestroy()
    }
}