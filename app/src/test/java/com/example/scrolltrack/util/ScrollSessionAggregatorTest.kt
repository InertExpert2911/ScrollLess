package com.example.scrolltrack.util

import com.example.scrolltrack.db.ScrollSessionDao
import com.example.scrolltrack.db.ScrollSessionRecord
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class ScrollSessionAggregatorTest {

    private lateinit var mockDao: ScrollSessionDao
    private lateinit var aggregator: ScrollSessionAggregator
    private val testDispatcher = StandardTestDispatcher(TestCoroutineScheduler())
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        mockDao = mockk<ScrollSessionDao>(relaxed = true) // relaxed = true for coEvery { mockDao.insertSession(any()) } justRuns
        aggregator = ScrollSessionAggregator(mockDao)
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
    fun `addSession - adds session to internal buffer`() = testScope.runTest {
        val session = createSession("app1", 0, 100, 50)
        aggregator.addSession(session)
        // Internal buffer isn't directly accessible for assertion without reflection or modification.
        // We'll verify by flushing.
        aggregator.flushBuffer()
        coVerify(exactly = 1) { mockDao.insertSession(session) }
    }

    @Test
    fun `flushBuffer - empty buffer - does nothing`() = testScope.runTest {
        aggregator.flushBuffer()
        coVerify(exactly = 0) { mockDao.insertSession(any()) }
    }

    @Test
    fun `flushBuffer - single session - inserts session directly`() = testScope.runTest {
        val session = createSession("app1", 0, 100, 50)
        aggregator.addSession(session)
        aggregator.flushBuffer()
        coVerify(exactly = 1) { mockDao.insertSession(session) }
    }

    @Test
    fun `flushBuffer - multiple sessions, no merge (different packages) - inserts all`() = testScope.runTest {
        val session1 = createSession("app1", 0, 100, 50)
        val session2 = createSession("app2", 0, 100, 50)
        aggregator.addSession(session1)
        aggregator.addSession(session2)
        aggregator.flushBuffer()
        coVerify(exactly = 1) { mockDao.insertSession(session1) }
        coVerify(exactly = 1) { mockDao.insertSession(session2) }
    }

    @Test
    fun `flushBuffer - multiple sessions, no merge (gap too large) - inserts all`() = testScope.runTest {
        val startTime1 = 0L
        val endTime1 = 100L
        val startTime2 = endTime1 + TimeUnit.SECONDS.toMillis(ScrollSessionAggregator.SESSION_MERGE_GAP_SECONDS + 1)
        val endTime2 = startTime2 + 100L

        val session1 = createSession("app1", startTime1, endTime1, 50)
        val session2 = createSession("app1", startTime2, endTime2, 70)

        aggregator.addSession(session1)
        aggregator.addSession(session2)
        aggregator.flushBuffer()

        coVerify(exactly = 1) { mockDao.insertSession(session1) }
        coVerify(exactly = 1) { mockDao.insertSession(session2) }
    }

    @Test
    fun `flushBuffer - multiple sessions, merge (gap within threshold) - inserts merged`() = testScope.runTest {
        val startTime1 = 0L
        val endTime1 = 100L
        val startTime2 = endTime1 + TimeUnit.SECONDS.toMillis(ScrollSessionAggregator.SESSION_MERGE_GAP_SECONDS - 1)
        val endTime2 = startTime2 + 100L

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
    fun `flushBuffer - merge dataType becomes MEASURED if one part is MEASURED`() = testScope.runTest {
        val session1 = createSession("app1", 0, 100, 50, "INFERRED")
        val session2 = createSession("app1", 100 + TimeUnit.SECONDS.toMillis(10), 200 + TimeUnit.SECONDS.toMillis(10), 70, "MEASURED")
        aggregator.addSession(session1)
        aggregator.addSession(session2)
        aggregator.flushBuffer()

        val slot = slot<ScrollSessionRecord>()
        coVerify { mockDao.insertSession(capture(slot)) }
        assertThat(slot.captured.dataType).isEqualTo("MEASURED")
        assertThat(slot.captured.scrollAmount).isEqualTo(120)
    }

    @Test
    fun `flushBuffer - three sessions, two merge, one separate - inserts two records`() = testScope.runTest {
        val s1 = createSession("app1", 0, 100, 10) // Merges with s2
        val s2 = createSession("app1", 150, 250, 20) // Gap from s1 is 50ms (mergeable)
        val s3 = createSession("app1", 100000, 100100, 30) // Gap from s2 is large

        aggregator.addSession(s1)
        aggregator.addSession(s2)
        aggregator.addSession(s3)
        aggregator.flushBuffer()

        val expectedMergedS1S2 = s1.copy(sessionEndTime = s2.sessionEndTime, scrollAmount = s1.scrollAmount + s2.scrollAmount)

        val capturedSessions = mutableListOf<ScrollSessionRecord>()
        coVerify(exactly = 2) { mockDao.insertSession(capture(capturedSessions)) }

        assertThat(capturedSessions).containsExactly(expectedMergedS1S2, s3)
    }


    @Test
    fun `flushBuffer - DAO error - sessions are added back to buffer`() = testScope.runTest {
        val session1 = createSession("app1", 0, 100, 50)
        aggregator.addSession(session1)

        coEvery { mockDao.insertSession(any()) } throws RuntimeException("DB error")

        aggregator.flushBuffer() // First flush, fails

        // Verify sessions are still there for a subsequent successful flush
        coEvery { mockDao.insertSession(any()) } returns Unit // Make DAO succeed now
        aggregator.flushBuffer() // Second flush

        coVerify(exactly = 1) { mockDao.insertSession(session1) } // Should be called once in the successful flush
    }

    @Test
    fun `start - launches aggregator job`() {
        var job: Job? = null
        // Use a new TestScope for this to control its lifecycle for job cancellation
        val localTestScope = TestScope(testDispatcher)
        job = localTestScope.launch { aggregator.start(this) }

        assertThat(job.isActive).isTrue()
        job.cancel() // Clean up
    }

    @Test
    fun `stop - cancels aggregator job`() {
        var job: Job? = null
        val localTestScope = TestScope(testDispatcher) // New scope for this test
        job = localTestScope.launch { aggregator.start(this) }

        aggregator.stop()
        assertThat(job.isCancelled).isTrue()
    }

    @Test
    fun `start - periodic flush is triggered`() = testScope.runTest {
        val job = launch { aggregator.start(this) } // Start aggregator in testScope's context

        val session = createSession("app1", 0, 100, 50)
        aggregator.addSession(session)

        // Advance time by slightly more than FLUSH_INTERVAL_MINUTES
        advanceTimeBy(TimeUnit.MINUTES.toMillis(ScrollSessionAggregator.FLUSH_INTERVAL_MINUTES) + 100)
        runCurrent() // Ensure scheduled tasks execute

        coVerify(exactly = 1) { mockDao.insertSession(session) }

        job.cancelAndJoin() // Clean up the aggregator job
    }
}
