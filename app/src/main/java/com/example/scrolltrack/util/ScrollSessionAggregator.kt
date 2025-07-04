package com.example.scrolltrack.util

import android.util.Log
import com.example.scrolltrack.db.ScrollSessionDao
import com.example.scrolltrack.db.ScrollSessionRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentLinkedQueue

@Singleton
class ScrollSessionAggregator @Inject constructor(
    private val scrollSessionDao: ScrollSessionDao,
    private val coroutineDispatcher: CoroutineDispatcher
) {
    private val TAG = "ScrollAggregator"
    private val buffer = ConcurrentLinkedQueue<ScrollSessionRecord>()
    private val mutex = Mutex()
    private var aggregatorJob: Job? = null

    internal companion object {
        val FLUSH_INTERVAL_MINUTES = 2L
        val SESSION_MERGE_GAP_SECONDS = 30L
    }

    fun start(scope: CoroutineScope) {
        if (aggregatorJob?.isActive == true) {
            Log.w(TAG, "Aggregator already running.")
            return
        }
        Log.i(TAG, "Starting periodic scroll session aggregator.")
        aggregatorJob = scope.launch(coroutineDispatcher) {
            while (isActive) {
                delay(TimeUnit.MINUTES.toMillis(FLUSH_INTERVAL_MINUTES))
                Log.d(TAG, "Periodic flush triggered.")
                flushBuffer()
            }
        }
    }

    fun stop() {
        Log.i(TAG, "Stopping aggregator job.")
        aggregatorJob?.cancel()
        aggregatorJob = null
    }

    suspend fun addSession(session: ScrollSessionRecord) {
        mutex.withLock {
            buffer.add(session)
        }
        Log.d(TAG, "Added session to buffer for ${session.packageName}. Buffer size: ${buffer.size}")
    }

    suspend fun flushBuffer() {
        val sessionsToFlush = mutex.withLock {
            if (buffer.isEmpty()) {
                return
            }
            val copy = buffer.toList()
            buffer.clear()
            copy
        }

        if (sessionsToFlush.isEmpty()) {
            Log.d(TAG, "Flush called, but buffer was empty.")
            return
        }

        Log.i(TAG, "Flushing buffer with ${sessionsToFlush.size} sessions.")
        val mergedSessions = mergeSessions(sessionsToFlush)
        Log.i(TAG, "After merging, ${mergedSessions.size} sessions will be written to DB.")

        try {
            mergedSessions.forEach {
                scrollSessionDao.insertSession(it)
            }
            Log.i(TAG, "Successfully flushed ${mergedSessions.size} sessions to the database.")
        } catch (e: Exception) {
            Log.e(TAG, "Error flushing sessions to database", e)
            // If flushing fails, add the un-flushed items back to the buffer for the next attempt.
            mutex.withLock {
                buffer.addAll(sessionsToFlush)
            }
        }
    }

    private fun mergeSessions(sessions: List<ScrollSessionRecord>): List<ScrollSessionRecord> {
        if (sessions.isEmpty()) {
            return emptyList()
        }

        val sessionsByPackage = sessions.groupBy { it.packageName }
        val finalSessions = mutableListOf<ScrollSessionRecord>()

        sessionsByPackage.forEach { (_, pkgSessions) ->
            if (pkgSessions.size <= 1) {
                finalSessions.addAll(pkgSessions)
                return@forEach
            }

            val sortedSessions = pkgSessions.sortedBy { it.sessionStartTime }
            val mergedForPackage = mutableListOf<ScrollSessionRecord>()
            var currentSession = sortedSessions.first()

            for (i in 1 until sortedSessions.size) {
                val nextSession = sortedSessions[i]
                val gapSeconds = TimeUnit.MILLISECONDS.toSeconds(nextSession.sessionStartTime - currentSession.sessionEndTime)

                if (gapSeconds <= SESSION_MERGE_GAP_SECONDS) {
                    currentSession = currentSession.copy(
                        scrollAmount = currentSession.scrollAmount + nextSession.scrollAmount,
                        sessionEndTime = nextSession.sessionEndTime,
                        dataType = if (currentSession.dataType == "MEASURED" || nextSession.dataType == "MEASURED") "MEASURED" else "INFERRED"
                    )
                } else {
                    mergedForPackage.add(currentSession)
                    currentSession = nextSession
                }
            }
            mergedForPackage.add(currentSession)
            finalSessions.addAll(mergedForPackage)
        }
        return finalSessions
    }
} 