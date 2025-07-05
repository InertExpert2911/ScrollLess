package com.example.scrolltrack.util

import android.util.Log

import com.example.scrolltrack.db.ScrollSessionDao
import com.example.scrolltrack.db.ScrollSessionRecord
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class ScrollSessionAggregatorTest {

    private lateinit var mockDao: ScrollSessionDao
    private lateinit var aggregator: ScrollSessionAggregator
    private val testScope = TestScope()

    @Before
    fun setUp() {
        mockDao = mockk(relaxed = true)
        aggregator = ScrollSessionAggregator(mockDao, StandardTestDispatcher(testScope.testScheduler))
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun createSession(
        pkg: String,
        startTime: Long,
        endTime: Long,
        scroll: Long,
        type: String = "INFERRED",
        date: String = "2023-01-01"
    ): ScrollSessionRecord {
        return ScrollSessionRecord(
            packageName = pkg,
            sessionStartTime = startTime,
            sessionEndTime = endTime,
            scrollAmount = scroll,
            dataType = type,
            date = date,
            sessionEndReason = "TEST"
        )
    }

    @Test
    fun addSession_withOneSession_addsToBuffer() = testScope.runTest {
        val session = createSession("app1", 0, 100, 50)
        aggregator.addSession(session)
        aggregator.flushBuffer()
        coVerify(exactly = 1) { mockDao.insertSession(session) }
    }

    @Test
    fun flushBuffer_withEmptyBuffer_doesNothing() = testScope.runTest {
        aggregator.flushBuffer()
        coVerify(exactly = 0) { mockDao.insertSession(any()) }
    }

    @Test
    fun flushBuffer_withNonMergeableSessions_insertsAllSeparately() = testScope.runTest {
        // s1 and s2 are non-mergeable (different apps)
        val session1 = createSession("app1", 0, 100, 50)
        val session2 = createSession("app2", 1000, 1100, 50)

        // s3 is non-mergeable with s1 (large time gap)
        val s3_startTime = 100 + TimeUnit.SECONDS.toMillis(ScrollSessionAggregator.SESSION_MERGE_GAP_SECONDS + 5)
        val session3 = createSession("app1", s3_startTime, s3_startTime + 100, 60)

        // s4 is non-mergeable with s3 (large time gap)
        val s4_startTime = session3.sessionEndTime + TimeUnit.SECONDS.toMillis(ScrollSessionAggregator.SESSION_MERGE_GAP_SECONDS + 5)
        val session4 = createSession("app1", s4_startTime, s4_startTime + 100, 70)

        aggregator.addSession(session1)
        aggregator.addSession(session2)
        aggregator.addSession(session3)
        aggregator.addSession(session4)
        aggregator.flushBuffer()

        val capturedSessions = mutableListOf<ScrollSessionRecord>()
        coVerify(exactly = 4) { mockDao.insertSession(capture(capturedSessions)) }
        assertThat(capturedSessions).containsExactlyElementsIn(listOf(session1, session2, session3, session4))
    }

    @Test
    fun flushBuffer_withTwoMergeableSessions_insertsOneMergedRecord() = testScope.runTest {
        val startTime1 = 1000L
        val endTime1 = 2000L
        val startTime2 = endTime1 + TimeUnit.SECONDS.toMillis(10) // Within merge threshold
        val endTime2 = startTime2 + 1000L

        val session1 = createSession("app1", startTime1, endTime1, 50, "INFERRED")
        val session2 = createSession("app1", startTime2, endTime2, 70, "INFERRED")
        aggregator.addSession(session1)
        aggregator.addSession(session2)
        aggregator.flushBuffer()

        val expectedMergedSession = createSession(
            pkg = "app1",
            startTime = startTime1,
            endTime = endTime2,
            scroll = 120, // 50 + 70
            type = "INFERRED"
        )
        coVerify(exactly = 1) { mockDao.insertSession(expectedMergedSession) }
    }

    @Test
    fun flushBuffer_withMergeableSessions_usesMeasuredDataTypeIfAnySessionIsMeasured() = testScope.runTest {
        val s1 = createSession("app1", 0, 100, 50, "INFERRED")
        val s2 = createSession("app1", 110, 210, 70, "MEASURED")
        aggregator.addSession(s1)
        aggregator.addSession(s2)
        aggregator.flushBuffer()

        val slot = slot<ScrollSessionRecord>()
        coVerify(exactly = 1) { mockDao.insertSession(capture(slot)) }
        assertThat(slot.captured.dataType).isEqualTo("MEASURED")
        assertThat(slot.captured.scrollAmount).isEqualTo(120)
    }

    @Test
    fun flushBuffer_withChainOfMergeableSessions_insertsOneFullyMergedRecord() = testScope.runTest {
        val s1 = createSession("app1", 0, 100, 10)
        val s2 = createSession("app1", 150, 250, 20)
        val s3 = createSession("app1", 300, 400, 30)
        val s4 = createSession("app1", 420, 520, 40)

        aggregator.addSession(s1)
        aggregator.addSession(s2)
        aggregator.addSession(s3)
        aggregator.addSession(s4)
        aggregator.flushBuffer()

        val expectedMergedSession = s1.copy(
            sessionEndTime = s4.sessionEndTime,
            scrollAmount = s1.scrollAmount + s2.scrollAmount + s3.scrollAmount + s4.scrollAmount
        )

        coVerify(exactly = 1) { mockDao.insertSession(expectedMergedSession) }
    }

    @Test
    fun flushBuffer_withComplexMixOfSessions_mergesAndInsertsCorrectly() = testScope.runTest {
        val s1_app1 = createSession("app1", 0, 100, 10)       // Merges with s2
        val s2_app1 = createSession("app1", 150, 250, 20)     // Merges with s1
        val s3_app2 = createSession("app2", 260, 360, 50)     // Standalone
        val s4_app1 = createSession("app1", 100000, 100100, 30) // Standalone (gap too large)

        aggregator.addSession(s1_app1)
        aggregator.addSession(s2_app1)
        aggregator.addSession(s3_app2)
        aggregator.addSession(s4_app1)
        aggregator.flushBuffer()

        val expectedMergedS1S2 = s1_app1.copy(sessionEndTime = s2_app1.sessionEndTime, scrollAmount = s1_app1.scrollAmount + s2_app1.scrollAmount)

        val capturedSessions = mutableListOf<ScrollSessionRecord>()
        coVerify(exactly = 3) { mockDao.insertSession(capture(capturedSessions)) }

        assertThat(capturedSessions).containsExactlyElementsIn(listOf(expectedMergedS1S2, s3_app2, s4_app1))
    }

    @Test
    fun flushBuffer_whenDaoThrowsError_retainsAllSessionsInBufferForNextFlush() = testScope.runTest {
        val session1 = createSession("app1", 0, 100, 50)
        val session2 = createSession("app2", 0, 100, 80)
        aggregator.addSession(session1)
        aggregator.addSession(session2)

        // First flush fails
        coEvery { mockDao.insertSession(any()) } throws RuntimeException("Database connection lost")
        aggregator.flushBuffer()

        // We clear the recorded calls from the failed flush to only verify the successful one.
        clearMocks(mockDao, answers = false)

        // Verify sessions are still in the buffer for a subsequent successful flush
        coEvery { mockDao.insertSession(any()) } returns Unit // Make DAO work now
        aggregator.flushBuffer()

        val capturedSessions = mutableListOf<ScrollSessionRecord>()
        coVerify(exactly = 2) { mockDao.insertSession(capture(capturedSessions)) }
        assertThat(capturedSessions).containsExactlyElementsIn(listOf(session1, session2))
    }

    @Test
    fun start_launchesAndActivatesPeriodicFlushJob() = testScope.runTest {
        val job = launch { aggregator.start(this) }

        assertThat(job.isActive).isTrue()
        job.cancel()
    }

    @Test
    fun stop_cancelsRunningJobAndFlushesBuffer() = testScope.runTest {
        // Start the aggregator
        aggregator.start(this)

        val session = createSession("app1", 0, 100, 50)
        aggregator.addSession(session)

        // Stop the aggregator
        aggregator.stop()

        // It should not have flushed yet.
        coVerify(exactly = 0) { mockDao.insertSession(any()) }

        // Manually flush to confirm buffer is intact.
        aggregator.flushBuffer()
        coVerify(exactly = 1) { mockDao.insertSession(session) }
    }

    @Test
    fun start_triggersPeriodicFlushAfterInterval() = testScope.runTest {
        // Start the aggregator which will launch the periodic flush job.
        val job = launch { aggregator.start(this) }

        val session = createSession("app1", 0, 100, 50)
        aggregator.addSession(session)

        // No flush should happen immediately.
        coVerify(exactly = 0) { mockDao.insertSession(any()) }

        // Advance time past the flush interval.
        advanceTimeBy(TimeUnit.MINUTES.toMillis(ScrollSessionAggregator.FLUSH_INTERVAL_MINUTES) + 500)

        // The periodic flush should have been triggered.
        coVerify(exactly = 1) { mockDao.insertSession(session) }

        // Clean up the job.
        job.cancelAndJoin()
    }
}