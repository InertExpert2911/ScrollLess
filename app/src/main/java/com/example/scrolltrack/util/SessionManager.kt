package com.example.scrolltrack.util

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.scrolltrack.data.DraftRepository
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.data.SessionDraft
import com.example.scrolltrack.db.ScrollSessionRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max

class SessionManager(
    private val scrollDataRepository: ScrollDataRepository,
    private val draftRepository: DraftRepository,
    private val serviceScope: CoroutineScope
) {
    private val TAG = "SessionManager"

    private var currentSession: SessionDraft? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pendingDraftSave: Runnable? = null

    private companion object {
        const val DRAFT_SAVE_INTERVAL_MS = 10000L
    }

    fun startNewSession(packageName: String, activityName: String?, startTime: Long) {
        finalizeAndSaveCurrentSession(startTime - 1, "APP_SWITCH")
        currentSession = SessionDraft(packageName, activityName, 0L, startTime, startTime)
        Log.i(TAG, "NEW SESSION: App: $packageName, Activity: $activityName at $startTime.")
    }

    fun updateCurrentSession(scrollDelta: Int) {
        currentSession?.let {
            currentSession = it.copy(
                scrollAmount = it.scrollAmount + scrollDelta,
                lastUpdateTime = System.currentTimeMillis()
            )
            scheduleDraftSave()
            Log.d(TAG, "Scroll in ${it.packageName}: Added:$scrollDelta, SessionTotal:${currentSession?.scrollAmount}")
        }
    }

    fun finalizeAndSaveCurrentSession(sessionEndTime: Long, reason: String) {
        currentSession?.let { session ->
            if (session.scrollAmount > 0) {
                serviceScope.launch {
                    scrollDataRepository.insertScrollSession(
                        ScrollSessionRecord(
                            packageName = session.packageName,
                            scrollAmount = session.scrollAmount,
                            sessionStartTime = session.startTime,
                            sessionEndTime = max(session.startTime, sessionEndTime),
                            date = DateUtil.formatUtcTimestampToLocalDateString(session.startTime),
                            sessionEndReason = reason
                        )
                    )
                    draftRepository.clearDraft()
                }
            }
        }
        resetCurrentSessionState()
    }

    fun recoverSession() {
        draftRepository.getDraft()?.let { draft ->
            Log.i(TAG, "Recovering draft session for ${draft.packageName}")
            currentSession = draft
            finalizeAndSaveCurrentSession(System.currentTimeMillis(), "RECOVERED_DRAFT")
        }
    }

    private fun scheduleDraftSave() {
        pendingDraftSave?.let { handler.removeCallbacks(it) }
        pendingDraftSave = Runnable {
            currentSession?.let {
                draftRepository.saveDraft(it.copy(lastUpdateTime = System.currentTimeMillis()))
                Log.i(TAG, "Saved draft for ${it.packageName}")
            }
        }
        handler.postDelayed(pendingDraftSave!!, DRAFT_SAVE_INTERVAL_MS)
    }

    private fun resetCurrentSessionState() {
        currentSession = null
        pendingDraftSave?.let { handler.removeCallbacks(it) }
    }
} 