package com.example.scrolltrack.util

import android.os.Handler
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.example.scrolltrack.data.DraftRepository
import com.example.scrolltrack.data.ScrollDataRepository // Keep for potential future use, though direct DAO is used now
import com.example.scrolltrack.data.SessionDraft
import com.example.scrolltrack.db.ScrollSessionRecord
import kotlinx.coroutines.*
import java.util.*
import kotlin.math.max
import kotlin.math.min
import com.example.scrolltrack.util.Clock
import com.example.scrolltrack.util.SystemClock
import javax.inject.Inject
import javax.inject.Singleton

class SessionManager(
    private val draftRepository: DraftRepository,
    private val scrollSessionAggregator: ScrollSessionAggregator,
    private val externalScope: CoroutineScope? = null,
    private val clock: Clock = SystemClock()
) {
    companion object {
        private const val DRAFT_SAVE_INTERVAL_MS = 10_000L // 10 seconds
        
        // Test mode flag - should only be true in tests
        @VisibleForTesting
        var testMode: Boolean = false
            private set
            
        // For testing only
        @VisibleForTesting
        fun setTestMode(enabled: Boolean) {
            testMode = enabled
        }
    }
    private val TAG = "SessionManager"
    // Made internal for testing
    internal val sessionManagerScope = externalScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Session state variables
    private var currentAppPackage: String? = null
    private var currentAppActivity: String? = null
    private var currentAppScrollAccumulator: Long = 0L
    private var currentSessionStartTime: Long = 0L
    private var isMeasuredScroll: Boolean = false

    // Session End Reasons
    object SessionEndReason {
        const val APP_SWITCH = "APP_SWITCH"
        const val SCREEN_OFF = "SCREEN_OFF"
        const val SERVICE_INTERRUPT = "SERVICE_INTERRUPT"
        const val SERVICE_DESTROY = "SERVICE_DESTROY"
        const val RECOVERED_DRAFT = "RECOVERED_DRAFT"
        const val SERVICE_RESTART = "SERVICE_RESTART"
    }

    private var draftSaveJob: Job? = null


    fun startNewSession(packageName: String, activityName: String?, startTime: Long) {
        val previousPackage = currentAppPackage
        val hadScroll = currentAppScrollAccumulator > 0

        // Finalize the previous session if it's for a different app and it had scroll data.
        if (previousPackage != null && previousPackage != packageName && hadScroll) {
            // Finalize the old session, but don't reset the state here. This method will handle it.
            finalizeAndSaveCurrentSession(startTime - 1, SessionEndReason.APP_SWITCH, resetState = false)
        }

        // If the app is changing, or if there was no previous session, reset state for the new one.
        if (previousPackage != packageName) {
            resetCurrentSessionState()
            currentAppPackage = packageName
            currentSessionStartTime = startTime
            Log.i(TAG, "NEW SESSION: App: $packageName, Activity: $activityName at $startTime.")
        }
        // Always update the activity name for the current session.
        currentAppActivity = activityName
    }

    fun updateCurrentSessionScroll(scrollDelta: Long, isMeasured: Boolean) {
        if (currentAppPackage == null || currentSessionStartTime == 0L) {
            Log.w(TAG, "Attempted to update scroll but no active session. ScrollDelta: $scrollDelta. This might happen if a scroll event is received before a window change event establishes the session.")
            return
        }
        currentAppScrollAccumulator += scrollDelta
        if (isMeasured) {
            isMeasuredScroll = true // If we get even one measured event, the whole session is measured.
        }
        Log.d(TAG, "Scroll in $currentAppPackage: Added:$scrollDelta, SessionTotal:$currentAppScrollAccumulator")
        scheduleDraftSave()
    }

    fun finalizeAndSaveCurrentSession(sessionEndTimeUTC: Long, reason: String, resetState: Boolean = true) {
        val pkgName = currentAppPackage ?: return
        val startTimeUTC = currentSessionStartTime
        val accumulatedScroll = currentAppScrollAccumulator
        val dataType = if (isMeasuredScroll) "MEASURED" else "INFERRED"

        Log.d(TAG, "Finalizing session for $pkgName. Start: $startTimeUTC, End: $sessionEndTimeUTC, Scroll: $accumulatedScroll, Reason: $reason, Type: $dataType")

        if (startTimeUTC == 0L || accumulatedScroll == 0L) {
            Log.i(TAG, "Skipping save for session ($pkgName) with no start time or zero scroll.")
            sessionManagerScope.launch {
                try {
                    draftRepository.clearDraft()
                } finally {
                    if (resetState) {
                        resetCurrentSessionState()
                    }
                }
            }
            return
        }

        val effectiveSessionEndTimeUTC = max(startTimeUTC, sessionEndTimeUTC)
        val startLocalDateString = DateUtil.formatUtcTimestampToLocalDateString(startTimeUTC)
        val endLocalDateString = DateUtil.formatUtcTimestampToLocalDateString(effectiveSessionEndTimeUTC)
        val totalSessionDuration = (effectiveSessionEndTimeUTC - startTimeUTC).coerceAtLeast(0L)

        sessionManagerScope.launch {
            try {
                if (startLocalDateString == endLocalDateString) {
                    val record = ScrollSessionRecord(
                        packageName = pkgName, 
                        scrollAmount = accumulatedScroll,
                        dataType = dataType,
                        sessionStartTime = startTimeUTC, 
                        sessionEndTime = effectiveSessionEndTimeUTC,
                        date = startLocalDateString, 
                        sessionEndReason = reason
                    )
                    scrollSessionAggregator.addSession(record)
                    Log.i(TAG, "Scroll session for $pkgName passed to aggregator.")
                } else {
                    Log.i(TAG, "Session for $pkgName spans midnight: $startLocalDateString to $endLocalDateString. Total Scroll: $accumulatedScroll. Duration: $totalSessionDuration ms. Splitting.")
                    
                    // Mock these in tests
                    val endOfDayForStartDayUTC = try {
                        DateUtil.getEndOfDayUtcMillis(startLocalDateString)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting end of day UTC millis", e)
                        effectiveSessionEndTimeUTC
                    }
                    
                    val effectiveEndOfStartDay = min(endOfDayForStartDayUTC, effectiveSessionEndTimeUTC)
                    val durationInStartDay = (effectiveEndOfStartDay - startTimeUTC + 1).coerceAtLeast(0L)
                    val scrollForStartDay = if (totalSessionDuration > 0) 
                        (accumulatedScroll * durationInStartDay.toDouble() / totalSessionDuration.toDouble()).toLong() 
                    else if (durationInStartDay > 0) 
                        accumulatedScroll 
                    else 
                        0L

                    if (scrollForStartDay > 0 || (accumulatedScroll > 0 && durationInStartDay > 0)) {
                        val record1 = ScrollSessionRecord(
                            packageName = pkgName, 
                            scrollAmount = scrollForStartDay,
                            dataType = dataType,
                            sessionStartTime = startTimeUTC, 
                            sessionEndTime = effectiveEndOfStartDay,
                            date = startLocalDateString, 
                            sessionEndReason = reason
                        )
                        scrollSessionAggregator.addSession(record1)
                        Log.i(TAG, "Split session (part 1) for $pkgName saved for $startLocalDateString. Scroll: $scrollForStartDay. DurationPart: $durationInStartDay ms. OriginalTotalScroll: $accumulatedScroll, OriginalTotalDuration: $totalSessionDuration ms.")
                    }

                    val startOfDayForEndDayUTC = try {
                        DateUtil.getStartOfDayUtcMillis(endLocalDateString)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting start of day UTC millis", e)
                        startTimeUTC
                    }
                    
                    val effectiveStartOfEndDay = max(startOfDayForEndDayUTC, startTimeUTC)
                    if (effectiveSessionEndTimeUTC >= effectiveStartOfEndDay) {
                        val scrollForEndDay = accumulatedScroll - scrollForStartDay
                        if (scrollForEndDay > 0 || (accumulatedScroll > 0 && (effectiveSessionEndTimeUTC - effectiveStartOfEndDay) > 0 && scrollForStartDay == 0L)) {
                            val record2 = ScrollSessionRecord(
                                packageName = pkgName, 
                                scrollAmount = scrollForEndDay.coerceAtLeast(0L),
                                dataType = dataType,
                                sessionStartTime = effectiveStartOfEndDay, 
                                sessionEndTime = effectiveSessionEndTimeUTC,
                                date = endLocalDateString, 
                                sessionEndReason = reason
                            )
                            scrollSessionAggregator.addSession(record2)
                            Log.i(TAG, "Split session (part 2) for $pkgName saved for $endLocalDateString. Scroll: ${scrollForEndDay.coerceAtLeast(0L)}. DurationPart: ${(effectiveSessionEndTimeUTC - effectiveStartOfEndDay)} ms. OriginalTotalScroll: $accumulatedScroll, OriginalTotalDuration: $totalSessionDuration ms.")
                        }
                    }
                }
                draftRepository.clearDraft()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving session for $pkgName to aggregator. Scroll: $accumulatedScroll. Reason: $reason", e)
                // If adding to buffer fails, we should probably re-save the draft
                // to avoid data loss.
                try {
                    draftRepository.saveDraft(
                        SessionDraft(
                            packageName = pkgName,
                            activityName = null, // Activity name isn't critical for recovery
                            scrollAmount = accumulatedScroll,
                            startTime = startTimeUTC,
                            lastUpdateTime = sessionEndTimeUTC
                        )
                    )
                } catch (saveError: Exception) {
                    Log.e(TAG, "Failed to save draft after session save error", saveError)
                }
            } finally {
                if (resetState) {
                    resetCurrentSessionState()
                }
            }
        }
    }

    fun recoverSession() {
        sessionManagerScope.launch {
            draftRepository.getDraft()?.let { draft ->
                Log.i(TAG, "Recovering draft session for ${draft.packageName}: Scroll=${draft.scrollAmount}, Start=${draft.startTime}, Activity=${draft.activityName}")
                currentAppPackage = draft.packageName
                currentAppActivity = draft.activityName
                currentAppScrollAccumulator = draft.scrollAmount
                currentSessionStartTime = draft.startTime
                finalizeAndSaveCurrentSession(clock.currentTimeMillis(), SessionEndReason.RECOVERED_DRAFT)
            } ?: Log.i(TAG, "No draft session to recover.")
        }
    }

    internal fun scheduleDraftSave() {
        if (currentAppPackage == null || currentSessionStartTime == 0L) {
            Log.v(TAG, "DRAFT SAVE: Skipped schedule, no active session.")
            return
        }

        draftSaveJob?.cancel() // Cancel any previously scheduled save
        draftSaveJob = sessionManagerScope.launch {
            try {
                // Use a shorter delay in test mode
                val delayMs = if (testMode) 100L else DRAFT_SAVE_INTERVAL_MS
                delay(delayMs)
                
                // Ensure these are not null before creating SessionDraft
                val pkg = currentAppPackage
                val startTime = currentSessionStartTime
                if (pkg != null && startTime != 0L) {
                    val sessionToSave = SessionDraft(
                        packageName = pkg,
                        activityName = currentAppActivity,
                        scrollAmount = currentAppScrollAccumulator,
                        startTime = startTime,
                        lastUpdateTime = clock.currentTimeMillis()
                    )
                    draftRepository.saveDraft(sessionToSave)
                    Log.i(TAG, "DRAFT SAVE: Saved draft for ${sessionToSave.packageName}, Amount: ${sessionToSave.scrollAmount}")
                } else {
                    Log.w(TAG, "DRAFT SAVE: Coroutine executed but session info was null/invalid.")
                }
            } catch (e: CancellationException) {
                // Re-throw cancellation to respect coroutine cancellation
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error saving draft", e)
            }
        }
        Log.v(TAG, "DRAFT SAVE: Scheduled draft save for $currentAppPackage")
    }

    fun resetCurrentSessionState() {
        currentAppPackage = null
        currentAppActivity = null
        currentAppScrollAccumulator = 0L
        currentSessionStartTime = 0L
        isMeasuredScroll = false
        draftSaveJob?.cancel()
        Log.d(TAG, "Session state reset by SessionManager.")
    }

    fun handleServiceStop(reason: String) {
        Log.i(TAG, "Handling service stop via SessionManager: $reason. Attempting to save final session data.")
        if (currentAppPackage != null && currentSessionStartTime != 0L && currentAppScrollAccumulator > 0) {
            val pkg = currentAppPackage!!
            val startTime = currentSessionStartTime
            val sessionToSave = SessionDraft(
                packageName = pkg,
                activityName = currentAppActivity,
                scrollAmount = currentAppScrollAccumulator,
                startTime = startTime,
                lastUpdateTime = clock.currentTimeMillis()
            )
            draftRepository.saveDraft(sessionToSave)
            Log.i(TAG, "DRAFT SAVE (onStop): Immediate draft saved for ${sessionToSave.packageName}")
        }
        finalizeAndSaveCurrentSession(clock.currentTimeMillis(), reason)
        resetCurrentSessionState()
    }

    // Getter for current package, useful for service logic
    fun getCurrentAppPackage(): String? = currentAppPackage
}