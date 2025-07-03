package com.example.scrolltrack.util

import com.example.scrolltrack.data.DraftRepository
import com.example.scrolltrack.data.SessionDraft
import com.example.scrolltrack.db.ScrollSessionRecord
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {

    private lateinit var mockDraftRepository: DraftRepository
    private lateinit var mockScrollAggregator: ScrollSessionAggregator
    private lateinit var sessionManager: SessionManager

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(testScheduler)
    private val testScope = TestScope(testDispatcher)

    private var originalDefaultTimeZone: TimeZone? = null

    @Before
    fun setUp() {
        originalDefaultTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC")) // For DateUtil consistency

        mockDraftRepository = mockk(relaxUnitFun = true) // relaxUnitFun for clearDraft, saveDraft
        mockScrollAggregator = mockk(relaxUnitFun = true) // relaxUnitFun for addSession

        // Mock DateUtil calls that rely on System.currentTimeMillis if they become problematic
        // For now, we control time by passing it into SessionManager methods where possible,
        // and for internal System.currentTimeMillis, we'll be mindful.

        sessionManager = SessionManager(mockDraftRepository, mockScrollAggregator)
    }

    @After
    fun tearDown() {
        originalDefaultTimeZone?.let { TimeZone.setDefault(it) }
        unmockkAll() // Important for MockK
    }

    private fun testTime(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(year, month - 1, day, hour, minute, second)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // --- startNewSession Tests ---
    @Test
    fun `startNewSession - no previous session - starts new session`() = testScope.runTest {
        val startTime = testTime(2023, 1, 1, 10, 0, 0)
        sessionManager.startNewSession("app1", "activityA", startTime)

        assertThat(sessionManager.getCurrentAppPackage()).isEqualTo("app1")
        // Internal state check not directly possible, verify via interactions if any, or other methods
        coVerify(exactly = 0) { mockScrollAggregator.addSession(any()) } // No finalization
    }

    @Test
    fun `startNewSession - previous session with scroll - finalizes old, starts new`() = testScope.runTest {
        val time1 = testTime(2023, 1, 1, 10, 0, 0)
        sessionManager.startNewSession("app1", "activityA", time1)
        sessionManager.updateCurrentSessionScroll(100, false)
        testScheduler.runCurrent() // Process update's coroutine for draft save scheduling

        val time2 = testTime(2023, 1, 1, 10, 5, 0)
        sessionManager.startNewSession("app2", "activityB", time2)
        testScheduler.runCurrent() // Process startNewSession's coroutine for finalization

        val capturedRecord = slot<ScrollSessionRecord>()
        coVerify(exactly = 1) { mockScrollAggregator.addSession(capture(capturedRecord)) }
        assertThat(capturedRecord.captured.packageName).isEqualTo("app1")
        assertThat(capturedRecord.captured.scrollAmount).isEqualTo(100)
        assertThat(capturedRecord.captured.sessionEndTime).isEqualTo(time2 - 1)

        assertThat(sessionManager.getCurrentAppPackage()).isEqualTo("app2")
        coVerify { mockDraftRepository.clearDraft() }
    }

    @Test
    fun `startNewSession - previous session no scroll - resets state, starts new, no save`() = testScope.runTest {
        val time1 = testTime(2023, 1, 1, 10, 0, 0)
        sessionManager.startNewSession("app1", "activityA", time1)
        // No scroll update

        val time2 = testTime(2023, 1, 1, 10, 5, 0)
        sessionManager.startNewSession("app2", "activityB", time2)
        testScheduler.runCurrent()

        coVerify(exactly = 0) { mockScrollAggregator.addSession(any()) } // No save as no scroll
        assertThat(sessionManager.getCurrentAppPackage()).isEqualTo("app2")
        coVerify(exactly = 0) { mockDraftRepository.clearDraft() } // Not called if no save
    }

    // --- updateCurrentSessionScroll Tests ---
    @Test
    fun `updateCurrentSessionScroll - active session - updates scroll and measured flag`() = testScope.runTest {
        val startTime = testTime(2023, 1, 1, 10, 0, 0)
        sessionManager.startNewSession("app1", "activityA", startTime)

        sessionManager.updateCurrentSessionScroll(50, false)
        // Internal state check: currentAppScrollAccumulator should be 50, isMeasuredScroll false
        // This needs to be verified by a subsequent finalizeAndSaveCurrentSession

        sessionManager.updateCurrentSessionScroll(70, true)
        // Internal state check: currentAppScrollAccumulator should be 120, isMeasuredScroll true
        testScheduler.runCurrent() // For draft save

        val finalTime = testTime(2023, 1, 1, 10, 1, 0)
        sessionManager.finalizeAndSaveCurrentSession(finalTime, "REASON")
        testScheduler.runCurrent()

        val capturedRecord = slot<ScrollSessionRecord>()
        coVerify { mockScrollAggregator.addSession(capture(capturedRecord)) }
        assertThat(capturedRecord.captured.scrollAmount).isEqualTo(120)
        assertThat(capturedRecord.captured.dataType).isEqualTo("MEASURED")
    }

    @Test
    fun `updateCurrentSessionScroll - no active session - does nothing`() = testScope.runTest {
        sessionManager.updateCurrentSessionScroll(100, false)
        // No session started, so no update, no draft save
        coVerify(exactly = 0) { mockDraftRepository.saveDraft(any()) }
        coVerify(exactly = 0) { mockScrollAggregator.addSession(any()) }
    }

    // --- finalizeAndSaveCurrentSession Tests ---
    @Test
    fun `finalizeAndSaveCurrentSession - no scroll - clears draft, resets state, no save`() = testScope.runTest {
        val startTime = testTime(2023, 1, 1, 10, 0, 0)
        sessionManager.startNewSession("app1", "activityA", startTime)
        // No scroll

        val endTime = testTime(2023, 1, 1, 10, 1, 0)
        sessionManager.finalizeAndSaveCurrentSession(endTime, "REASON")
        testScheduler.runCurrent()

        coVerify(exactly = 0) { mockScrollAggregator.addSession(any()) }
        coVerify(exactly = 1) { mockDraftRepository.clearDraft() } // Cleared even if no scroll
        assertThat(sessionManager.getCurrentAppPackage()).isNull()
    }

    @Test
    fun `finalizeAndSaveCurrentSession - session within single day - saves one record`() = testScope.runTest {
        val startTime = testTime(2023, 1, 1, 10, 0, 0) // 2023-01-01
        sessionManager.startNewSession("app1", "activityA", startTime)
        sessionManager.updateCurrentSessionScroll(150, true)
        testScheduler.runCurrent()

        val endTime = testTime(2023, 1, 1, 10, 15, 0) // Still 2023-01-01
        sessionManager.finalizeAndSaveCurrentSession(endTime, "REASON")
        testScheduler.runCurrent()

        val capturedRecord = slot<ScrollSessionRecord>()
        coVerify(exactly = 1) { mockScrollAggregator.addSession(capture(capturedRecord)) }
        assertThat(capturedRecord.captured.packageName).isEqualTo("app1")
        assertThat(capturedRecord.captured.scrollAmount).isEqualTo(150)
        assertThat(capturedRecord.captured.dataType).isEqualTo("MEASURED")
        assertThat(capturedRecord.captured.sessionStartTime).isEqualTo(startTime)
        assertThat(capturedRecord.captured.sessionEndTime).isEqualTo(endTime)
        assertThat(capturedRecord.captured.date).isEqualTo("2023-01-01")
        coVerify { mockDraftRepository.clearDraft() }
    }

    @Test
    fun `finalizeAndSaveCurrentSession - session spans midnight - splits into two records`() = testScope.runTest {
        val startTime = testTime(2023, 1, 1, 23, 50, 0) // Day 1: 2023-01-01, 10 mins left
        sessionManager.startNewSession("app1", "activityA", startTime)
        sessionManager.updateCurrentSessionScroll(300, false) // e.g., 150 per 10 mins
        testScheduler.runCurrent()

        val endTime = testTime(2023, 1, 2, 0, 10, 0)   // Day 2: 2023-01-02, 10 mins in
                                                       // Total duration 20 mins
        sessionManager.finalizeAndSaveCurrentSession(endTime, "REASON_SPLIT")
        testScheduler.runCurrent()

        val capturedRecords = mutableListOf<ScrollSessionRecord>()
        coVerify(exactly = 2) { mockScrollAggregator.addSession(capture(capturedRecords)) }

        val recordDay1 = capturedRecords.find { it.date == "2023-01-01" }
        val recordDay2 = capturedRecords.find { it.date == "2023-01-02" }

        assertThat(recordDay1).isNotNull()
        assertThat(recordDay2).isNotNull()

        // Day 1: 10 mins duration, scroll should be proportional (150)
        // Start time: 2023-01-01 23:50:00
        // End time: 2023-01-01 23:59:59.999
        val endOfDay1 = DateUtil.getEndOfDayUtcMillis("2023-01-01")
        assertThat(recordDay1!!.packageName).isEqualTo("app1")
        assertThat(recordDay1.sessionStartTime).isEqualTo(startTime)
        assertThat(recordDay1.sessionEndTime).isEqualTo(endOfDay1)
        assertThat(recordDay1.scrollAmount).isEqualTo(150) // Duration 10min / 20min total * 300 scroll
        assertThat(recordDay1.dataType).isEqualTo("INFERRED")

        // Day 2: 10 mins duration, scroll should be proportional (150)
        // Start time: 2023-01-02 00:00:00.000
        // End time: 2023-01-02 00:10:00
        val startOfDay2 = DateUtil.getStartOfDayUtcMillis("2023-01-02")
        assertThat(recordDay2!!.packageName).isEqualTo("app1")
        assertThat(recordDay2.sessionStartTime).isEqualTo(startOfDay2)
        assertThat(recordDay2.sessionEndTime).isEqualTo(endTime)
        assertThat(recordDay2.scrollAmount).isEqualTo(150)
        assertThat(recordDay2.dataType).isEqualTo("INFERRED")

        coVerify { mockDraftRepository.clearDraft() }
    }

    @Test
    fun `finalizeAndSaveCurrentSession - aggregator error - draft is re-saved`() = testScope.runTest {
        val startTime = testTime(2023,1,1,10,0,0)
        sessionManager.startNewSession("app1", "activityA", startTime)
        sessionManager.updateCurrentSessionScroll(100, false)
        testScheduler.runCurrent()

        coEvery { mockScrollAggregator.addSession(any()) } throws RuntimeException("Aggregator Boom")

        val endTime = testTime(2023,1,1,10,5,0)
        sessionManager.finalizeAndSaveCurrentSession(endTime, "FAIL_REASON")
        testScheduler.runCurrent()

        val expectedDraft = SessionDraft("app1", "activityA", 100, startTime, endTime)
        val capturedDraft = slot<SessionDraft>()
        coVerify { mockDraftRepository.saveDraft(capture(capturedDraft)) }

        assertThat(capturedDraft.captured.packageName).isEqualTo(expectedDraft.packageName)
        // activityName in draft is null if error occurs during save
        assertThat(capturedDraft.captured.scrollAmount).isEqualTo(expectedDraft.scrollAmount)
        assertThat(capturedDraft.captured.startTime).isEqualTo(expectedDraft.startTime)
        // lastUpdateTime will be current time, so can't directly compare full draft object

        coVerify(exactly = 0) { mockDraftRepository.clearDraft() } // Not cleared on error
    }


    // --- recoverSession Tests ---
    @Test
    fun `recoverSession - no draft - does nothing`() = testScope.runTest {
        every { mockDraftRepository.getDraft() } returns null
        sessionManager.recoverSession()
        testScheduler.runCurrent()
        coVerify(exactly = 0) { mockScrollAggregator.addSession(any()) }
    }

    @Test
    fun `recoverSession - valid draft - finalizes and saves session`() = testScope.runTest {
        val draftStartTime = testTime(2023, 1, 1, 9, 0, 0)
        val draft = SessionDraft("appDraft", "activityDraft", 200, draftStartTime, draftStartTime + 1000)
        every { mockDraftRepository.getDraft() } returns draft

        // Mock System.currentTimeMillis() for predictable sessionEndTime in finalize
        val mockCurrentTime = draftStartTime + 5000 // Some time after draft's last update
        mockkStatic(System::class)
        every { System.currentTimeMillis() } returns mockCurrentTime

        sessionManager.recoverSession()
        testScheduler.runCurrent()

        val capturedRecord = slot<ScrollSessionRecord>()
        coVerify(exactly = 1) { mockScrollAggregator.addSession(capture(capturedRecord)) }
        assertThat(capturedRecord.captured.packageName).isEqualTo("appDraft")
        assertThat(capturedRecord.captured.scrollAmount).isEqualTo(200)
        assertThat(capturedRecord.captured.sessionStartTime).isEqualTo(draftStartTime)
        assertThat(capturedRecord.captured.sessionEndTime).isEqualTo(mockCurrentTime) // Finalized with current time
        assertThat(capturedRecord.captured.sessionEndReason).isEqualTo(SessionManager.SessionEndReason.RECOVERED_DRAFT)
        coVerify { mockDraftRepository.clearDraft() }
    }

    // --- scheduleDraftSave Tests ---
    @Test
    fun `scheduleDraftSave - called on scroll update - saves draft after delay`() = testScope.runTest {
        val startTime = testTime(2023, 1, 1, 10, 0, 0)
        sessionManager.startNewSession("app1", "actA", startTime)

        sessionManager.updateCurrentSessionScroll(10, false)
        testScheduler.runCurrent() // Initial part of update's launch

        // Draft save should not have happened yet
        coVerify(exactly = 0) { mockDraftRepository.saveDraft(any()) }

        // Advance time by DRAFT_SAVE_INTERVAL_MS
        testScheduler.advanceTimeBy(SessionManager.DRAFT_SAVE_INTERVAL_MS + 100)
        testScheduler.runCurrent() // Execute the delayed save

        val capturedDraft = slot<SessionDraft>()
        coVerify(exactly = 1) { mockDraftRepository.saveDraft(capture(capturedDraft)) }
        assertThat(capturedDraft.captured.packageName).isEqualTo("app1")
        assertThat(capturedDraft.captured.scrollAmount).isEqualTo(10)
    }

    @Test
    fun `scheduleDraftSave - multiple scroll updates - only one draft save executes (debounced)`() = testScope.runTest {
        val startTime = testTime(2023, 1, 1, 10, 0, 0)
        sessionManager.startNewSession("app1", "actA", startTime)

        sessionManager.updateCurrentSessionScroll(10, false) // Schedules save A
        testScheduler.advanceTimeBy(1000) // Less than DRAFT_SAVE_INTERVAL_MS
        sessionManager.updateCurrentSessionScroll(20, false) // Cancels A, schedules save B (total scroll 30)
        testScheduler.runCurrent()

        testScheduler.advanceTimeBy(SessionManager.DRAFT_SAVE_INTERVAL_MS + 100)
        testScheduler.runCurrent()

        val capturedDraft = slot<SessionDraft>()
        coVerify(exactly = 1) { mockDraftRepository.saveDraft(capture(capturedDraft)) } // Only one save
        assertThat(capturedDraft.captured.scrollAmount).isEqualTo(30) // With latest scroll amount
    }


    // --- handleServiceStop Tests ---
    @Test
    fun `handleServiceStop - active session with scroll - saves draft and finalizes`() = testScope.runTest {
        val startTime = testTime(2023, 1, 1, 10, 0, 0)
        sessionManager.startNewSession("app1", "activityA", startTime)
        sessionManager.updateCurrentSessionScroll(100, true)
        testScheduler.runCurrent() // For initial draft schedule

        val stopTime = testTime(2023, 1, 1, 10, 2, 0)
        mockkStatic(System::class)
        every { System.currentTimeMillis() } returns stopTime // Mock current time for finalize

        sessionManager.handleServiceStop("SERVICE_KILLED")
        testScheduler.runCurrent() // For finalization and draft save

        val capturedDraft = slot<SessionDraft>()
        coVerify(ordering = Ordering.SEQUENCE) {
            mockDraftRepository.saveDraft(capture(capturedDraft)) // Immediate draft save on stop
            mockScrollAggregator.addSession(any())      // Then finalization
        }

        assertThat(capturedDraft.captured.packageName).isEqualTo("app1")
        assertThat(capturedDraft.captured.scrollAmount).isEqualTo(100)

        val capturedRecord = slot<ScrollSessionRecord>()
        coVerify { mockScrollAggregator.addSession(capture(capturedRecord)) }
        assertThat(capturedRecord.captured.packageName).isEqualTo("app1")
        assertThat(capturedRecord.captured.scrollAmount).isEqualTo(100)
        assertThat(capturedRecord.captured.sessionEndTime).isEqualTo(stopTime)
        assertThat(capturedRecord.captured.sessionEndReason).isEqualTo("SERVICE_KILLED")

        assertThat(sessionManager.getCurrentAppPackage()).isNull() // State reset
        coVerify { mockDraftRepository.clearDraft() } // After successful finalization
    }

    @Test
    fun `handleServiceStop - no active session - does nothing beyond reset`() = testScope.runTest {
        sessionManager.handleServiceStop("SERVICE_KILLED_NO_SESSION")
        testScheduler.runCurrent()

        coVerify(exactly = 0) { mockDraftRepository.saveDraft(any()) }
        coVerify(exactly = 0) { mockScrollAggregator.addSession(any()) }
        assertThat(sessionManager.getCurrentAppPackage()).isNull()
    }
}
