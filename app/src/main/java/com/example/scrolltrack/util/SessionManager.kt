package com.example.scrolltrack.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.scrolltrack.data.DraftRepository
import com.example.scrolltrack.data.ScrollDataRepository // Keep for potential future use, though direct DAO is used now
import com.example.scrolltrack.data.SessionDraft
import com.example.scrolltrack.db.ScrollSessionDao
import com.example.scrolltrack.db.ScrollSessionRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

@Singleton
class SessionManager @Inject constructor(
    // private val context: Context, // Not needed if DraftRepository handles its own context
    private val draftRepository: DraftRepository,
    private val scrollSessionDao: ScrollSessionDao
) {
    private val TAG = "SessionManager"
    private val sessionManagerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
    }

    private var draftSaveJob: Job? = null
    private companion object {
        const val DRAFT_SAVE_INTERVAL_MS = 10000L
    }

    fun startNewSession(packageName: String, activityName: String?, startTime: Long) {
        // Finalize previous session only if there was an active package and it's different from the new one,
        // or if there was scroll accumulated in the previous session.
        if (currentAppPackage != null && (currentAppPackage != packageName || currentAppScrollAccumulator > 0)) {
            finalizeAndSaveCurrentSession(startTime - 1, SessionEndReason.APP_SWITCH)
        }

        // Start new session if package is different or if it's the same but was reset
        if (currentAppPackage != packageName || currentSessionStartTime == 0L) {
            currentAppPackage = packageName
            currentAppActivity = activityName
            currentAppScrollAccumulator = 0L
            currentSessionStartTime = startTime
            isMeasuredScroll = false // Reset for new session
            Log.i(TAG, "NEW SESSION: App: $currentAppPackage, Activity: $currentAppActivity at $startTime. Accumulator reset.")
        }
    }

    fun updateCurrentSessionScroll(scrollDelta: Long, isMeasured: Boolean) {
        if (currentAppPackage == null || currentSessionStartTime == 0L) {
            Log.w(TAG, "Attempted to update scroll but no active session. ScrollDelta: $scrollDelta. This might happen if a scroll event is received before a window change event establishes the session.")
            // Potentially, if determinedPackageName is available here, we could try to start a session.
            // However, onAccessibilityEvent in Service should ideally establish the session first via WINDOW_STATE_CHANGED.
            return
        }
        currentAppScrollAccumulator += scrollDelta
        if (isMeasured) {
            isMeasuredScroll = true // If we get even one measured event, the whole session is measured.
        }
        Log.d(TAG, "Scroll in $currentAppPackage: Added:$scrollDelta, SessionTotal:$currentAppScrollAccumulator")
        sessionManagerScope.launch {
            scheduleDraftSave()
        }
    }

    fun finalizeAndSaveCurrentSession(sessionEndTimeUTC: Long, reason: String) {
        val pkgName = currentAppPackage ?: return
        val startTimeUTC = currentSessionStartTime
        val accumulatedScroll = currentAppScrollAccumulator
        val dataType = if (isMeasuredScroll) "MEASURED" else "INFERRED"

        Log.d(TAG, "Finalizing session for $pkgName. Start: $startTimeUTC, End: $sessionEndTimeUTC, Scroll: $accumulatedScroll, Reason: $reason, Type: $dataType")

        if (startTimeUTC == 0L || accumulatedScroll == 0L) {
            Log.i(TAG, "Skipping save for session ($pkgName) with no start time or zero scroll.")
            if (reason != SessionEndReason.RECOVERED_DRAFT) { // Avoid loop if called from recover
                 sessionManagerScope.launch { draftRepository.clearDraft() }
            }
            resetCurrentSessionState()
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
                        packageName = pkgName, scrollAmount = accumulatedScroll,
                        dataType = dataType,
                        sessionStartTime = startTimeUTC, sessionEndTime = effectiveSessionEndTimeUTC,
                        date = startLocalDateString, sessionEndReason = reason
                    )
                    scrollSessionDao.insertSession(record)
                    Log.i(TAG, "Scroll session for $pkgName saved for date $startLocalDateString. Scroll: $accumulatedScroll. Duration: $totalSessionDuration ms")
                } else {
                    Log.i(TAG, "Session for $pkgName spans midnight: $startLocalDateString to $endLocalDateString. Total Scroll: $accumulatedScroll. Duration: $totalSessionDuration ms. Splitting.")
                    val endOfDayForStartDayUTC = DateUtil.getEndOfDayUtcMillis(startLocalDateString)
                    val effectiveEndOfStartDay = min(endOfDayForStartDayUTC, effectiveSessionEndTimeUTC)
                    val durationInStartDay = (effectiveEndOfStartDay - startTimeUTC + 1).coerceAtLeast(0L)
                    val scrollForStartDay = if (totalSessionDuration > 0) (accumulatedScroll * durationInStartDay.toDouble() / totalSessionDuration.toDouble()).toLong() else if (durationInStartDay > 0) accumulatedScroll else 0L

                    if (scrollForStartDay > 0 || (accumulatedScroll > 0 && durationInStartDay > 0)) {
                        val record1 = ScrollSessionRecord(
                            packageName = pkgName, scrollAmount = scrollForStartDay,
                            dataType = dataType,
                            sessionStartTime = startTimeUTC, sessionEndTime = effectiveEndOfStartDay,
                            date = startLocalDateString, sessionEndReason = reason
                        )
                        scrollSessionDao.insertSession(record1)
                        Log.i(TAG, "Split session (part 1) for $pkgName saved for $startLocalDateString. Scroll: $scrollForStartDay. DurationPart: $durationInStartDay ms. OriginalTotalScroll: $accumulatedScroll, OriginalTotalDuration: $totalSessionDuration ms.")
                    }

                    val startOfDayForEndDayUTC = DateUtil.getStartOfDayUtcMillis(endLocalDateString)
                    val effectiveStartOfEndDay = max(startOfDayForEndDayUTC, startTimeUTC)
                    if (effectiveSessionEndTimeUTC >= effectiveStartOfEndDay) {
                        val scrollForEndDay = accumulatedScroll - scrollForStartDay
                        if (scrollForEndDay > 0 || (accumulatedScroll > 0 && (effectiveSessionEndTimeUTC - effectiveStartOfEndDay) > 0 && scrollForStartDay == 0L) ) {
                             val record2 = ScrollSessionRecord(
                                packageName = pkgName, scrollAmount = scrollForEndDay.coerceAtLeast(0L),
                                dataType = dataType,
                                sessionStartTime = effectiveStartOfEndDay, sessionEndTime = effectiveSessionEndTimeUTC,
                                date = endLocalDateString, sessionEndReason = reason
                            )
                            scrollSessionDao.insertSession(record2)
                             Log.i(TAG, "Split session (part 2) for $pkgName saved for $endLocalDateString. Scroll: ${scrollForEndDay.coerceAtLeast(0L)}. DurationPart: ${(effectiveSessionEndTimeUTC - effectiveStartOfEndDay)} ms. OriginalTotalScroll: $accumulatedScroll, OriginalTotalDuration: $totalSessionDuration ms.")
                        }
                    }
                }
                draftRepository.clearDraft()
                // Notification update will be handled by the service after calling this.
            } catch (e: Exception) {
                Log.e(TAG, "Error saving session for $pkgName to DB. Scroll: $accumulatedScroll. Reason: $reason", e)
            }
        }
        resetCurrentSessionState()
    }

    fun recoverSession() {
        draftRepository.getDraft()?.let { draft ->
            Log.i(TAG, "Recovering draft session for ${draft.packageName}: Scroll=${draft.scrollAmount}, Start=${draft.startTime}, Activity=${draft.activityName}")
            currentAppPackage = draft.packageName
            currentAppActivity = draft.activityName
            currentAppScrollAccumulator = draft.scrollAmount
            currentSessionStartTime = draft.startTime
            finalizeAndSaveCurrentSession(System.currentTimeMillis(), SessionEndReason.RECOVERED_DRAFT)
        } ?: Log.i(TAG, "No draft session to recover.")
    }

    private suspend fun scheduleDraftSave() {
        if (currentAppPackage == null || currentSessionStartTime == 0L) {
             Log.v(TAG, "DRAFT SAVE: Skipped schedule, no active session.")
            return
        }

        draftSaveJob?.cancel() // Cancel any previously scheduled save
        draftSaveJob = sessionManagerScope.launch {
            delay(DRAFT_SAVE_INTERVAL_MS)
            // Ensure these are not null before creating SessionDraft
            val pkg = currentAppPackage
            val startTime = currentSessionStartTime
            if (pkg != null && startTime != 0L) {
                val sessionToSave = SessionDraft(
                    packageName = pkg,
                    activityName = currentAppActivity,
                    scrollAmount = currentAppScrollAccumulator,
                    startTime = startTime,
                    lastUpdateTime = System.currentTimeMillis()
                )
                draftRepository.saveDraft(sessionToSave)
                Log.i(TAG, "DRAFT SAVE: Saved draft for ${sessionToSave.packageName}, Amount: ${sessionToSave.scrollAmount}")
            } else {
                 Log.w(TAG, "DRAFT SAVE: Coroutine executed but session info was null/invalid.")
            }
        }
        Log.v(TAG, "DRAFT SAVE: Scheduled draft save for $currentAppPackage in ${DRAFT_SAVE_INTERVAL_MS}ms")
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
                lastUpdateTime = System.currentTimeMillis()
            )
            draftRepository.saveDraft(sessionToSave)
            Log.i(TAG, "DRAFT SAVE (onStop): Immediate draft saved for ${sessionToSave.packageName}")
        }
        finalizeAndSaveCurrentSession(System.currentTimeMillis(), reason)
    }

    // Getter for current package, useful for service logic
    fun getCurrentAppPackage(): String? = currentAppPackage
}