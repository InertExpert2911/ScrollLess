package com.example.scrolltrack.util

import com.example.scrolltrack.util.Clock
import com.example.scrolltrack.data.DraftRepository
import com.example.scrolltrack.data.SessionDraft
import com.example.scrolltrack.db.ScrollSessionRecord
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.*
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {

    // A simple test clock that uses the TestScope's virtual time
    class TestClock(private val testScope: TestScope) : Clock {
        override fun currentTimeMillis(): Long = testScope.currentTime
    }

    private lateinit var sessionManager: SessionManager
    private lateinit var mockDraftRepository: DraftRepository
    private lateinit var mockScrollAggregator: ScrollSessionAggregator
    private lateinit var testScope: TestScope

    @Before
    fun setUp() {
        // Enable test mode
        SessionManager.setTestMode(true)
        
        // Set test scope
        testScope = TestScope()

        // Set up mocks
        mockDraftRepository = mockk(relaxUnitFun = true)
        mockScrollAggregator = mockk(relaxUnitFun = true)
        
        // Create test instance of SessionManager, passing the test scope and a test clock
        sessionManager = SessionManager(mockDraftRepository, mockScrollAggregator, testScope, TestClock(testScope))

        // Mock the DateUtil companion object
        mockkObject(DateUtil)

        // Mock date formatting
        every { DateUtil.formatUtcTimestampToLocalDateString(any<Long>()) } answers {
            val timestamp = firstArg<Long>()
            val date = Date(timestamp)
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.format(date)
        }

        // Mock date calculations
        every { DateUtil.getEndOfDayUtcMillis(any()) } answers { 
            val date = firstArg<String>()
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val parsed = sdf.parse(date)!!
            parsed.time + TimeUnit.DAYS.toMillis(1) - 1
        }

        every { DateUtil.getStartOfDayUtcMillis(any()) } answers {
            val date = firstArg<String>()
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse(date)!!.time
        }
        
        // Clear any previous mocks
        clearAllMocks()
    }
    
    @After
    fun tearDown() {
        // Reset test mode
        SessionManager.setTestMode(false)
        // Clean up mocks
        unmockkAll()
    }

    // --- Test Data ---
    private val app1 = "com.app.one"
    private val app2 = "com.app.two"
    private val activityA = "ActivityA"
    private val activityB = "ActivityB"

    private val time1 = 1704105600000L // 2024-01-01 10:00:00 UTC
    private val time2 = 1704105900000L // 2024-01-01 10:05:00 UTC
    private val time3 = 1704106200000L // 2024-01-01 10:10:00 UTC

    private val timeAcrossMidnightStart = 1704153000000L // 2024-01-01 23:50:00 UTC
    private val timeAcrossMidnightEnd = 1704154200000L   // 2024-01-02 00:10:00 UTC
    private val endOfDay1 = 1704153599999L // 2024-01-01 23:59:59.999 UTC
    private val startOfDay2 = 1704153600000L // 2024-01-02 00:00:00.000 UTC

    private val DRAFT_SAVE_INTERVAL_MS = 10000L

    // --- startNewSession ---

    @Test
    fun startNewSession_withNoActiveSession_startsTrackingNewApp() = testScope.runTest {
        sessionManager.startNewSession(app1, activityA, time1)

        assertThat(sessionManager.getCurrentAppPackage()).isEqualTo(app1)
        coVerify(exactly = 0) { mockScrollAggregator.addSession(any()) }
        coVerify(exactly = 0) { mockDraftRepository.saveDraft(any()) }
    }

    @Test
    fun startNewSession_withExistingSessionWithScroll_finalizesOldAndStartsNew() = testScope.runTest {
        sessionManager.startNewSession(app1, activityA, time1)
        sessionManager.updateCurrentSessionScroll(100, true)
        testScope.runCurrent() // Ensure draft save job is scheduled

        sessionManager.startNewSession(app2, activityB, time2)
        testScope.advanceUntilIdle()

        val capturedRecord = slot<ScrollSessionRecord>()
        coVerify(exactly = 1) { mockScrollAggregator.addSession(capture(capturedRecord)) }
        assertThat(capturedRecord.captured.packageName).isEqualTo(app1)
        assertThat(capturedRecord.captured.scrollAmount).isEqualTo(100)
        assertThat(capturedRecord.captured.sessionEndTime).isEqualTo(time2 - 1)
        assertThat(capturedRecord.captured.sessionEndReason).isEqualTo(SessionManager.SessionEndReason.APP_SWITCH)

        coVerify(exactly = 1) { mockDraftRepository.clearDraft() }
        assertThat(sessionManager.getCurrentAppPackage()).isEqualTo(app2)
    }

    @Test
    fun startNewSession_withExistingSessionWithoutScroll_resetsAndStartsNewWithoutSaving() = testScope.runTest {
        sessionManager.startNewSession(app1, activityA, time1)
        sessionManager.startNewSession(app2, activityB, time2)
        testScope.advanceUntilIdle()

        coVerify(exactly = 0) { mockScrollAggregator.addSession(any()) }
        assertThat(sessionManager.getCurrentAppPackage()).isEqualTo(app2)
    }

    @Test
    fun startNewSession_forSameApp_doesNothing() = testScope.runTest {
        sessionManager.startNewSession(app1, activityA, time1)
        sessionManager.updateCurrentSessionScroll(100, true)

        sessionManager.startNewSession(app1, activityB, time2)

        coVerify(exactly = 0) { mockScrollAggregator.addSession(any()) }
        assertThat(sessionManager.getCurrentAppPackage()).isEqualTo(app1)
    }

    // --- updateCurrentSessionScroll ---

    @Test
    fun updateCurrentSessionScroll_withActiveSession_updatesScrollAndDataType() = testScope.runTest {
        sessionManager.startNewSession(app1, activityA, time1)
        sessionManager.updateCurrentSessionScroll(50, false)
        sessionManager.updateCurrentSessionScroll(70, true)

        sessionManager.finalizeAndSaveCurrentSession(time2, "TEST")
        testScope.runCurrent()

        val capturedRecord = slot<ScrollSessionRecord>()
        coVerify { mockScrollAggregator.addSession(capture(capturedRecord)) }
        assertThat(capturedRecord.captured.scrollAmount).isEqualTo(120)
        assertThat(capturedRecord.captured.dataType).isEqualTo("MEASURED")
    }

    @Test
    fun updateCurrentSessionScroll_withActiveSession_savesDraftAfterInterval() = testScope.runTest {
        val testDraftSaveInterval = 100L // from SessionManager testMode
        sessionManager.startNewSession(app1, activityA, time1)

        // First scroll - should schedule but not save yet
        sessionManager.updateCurrentSessionScroll(10, true)
        advanceTimeBy(testDraftSaveInterval - 10)
        coVerify(exactly = 0) { mockDraftRepository.saveDraft(any()) }

        // Second scroll - should reset timer
        sessionManager.updateCurrentSessionScroll(20, true)
        advanceTimeBy(testDraftSaveInterval - 10)
        coVerify(exactly = 0) { mockDraftRepository.saveDraft(any()) }

        // Advance past the save interval
        advanceTimeBy(20)
        advanceUntilIdle() // Wait for save to complete

        // Verify draft was saved with correct amount
        val capturedDraft = slot<SessionDraft>()
        coVerify(exactly = 1) { mockDraftRepository.saveDraft(capture(capturedDraft)) }
        assertThat(capturedDraft.captured.scrollAmount).isEqualTo(30)
        assertThat(capturedDraft.captured.packageName).isEqualTo(app1)
    }

    @Test
    fun updateCurrentSessionScroll_withNoActiveSession_doesNothing() = testScope.runTest {
        sessionManager.updateCurrentSessionScroll(100, false)
        coVerify(exactly = 0) { mockDraftRepository.saveDraft(any()) }
        coVerify(exactly = 0) { mockScrollAggregator.addSession(any()) }
    }

    // --- finalizeAndSaveCurrentSession ---

    @Test
    fun finalizeAndSaveCurrentSession_withScrollData_savesRecordAndClearsDraft() = testScope.runTest {
        sessionManager.startNewSession(app1, activityA, time1)
        sessionManager.updateCurrentSessionScroll(150, true)
        testScope.runCurrent()

        sessionManager.finalizeAndSaveCurrentSession(time2, SessionManager.SessionEndReason.SCREEN_OFF)
        testScope.runCurrent()

        val capturedRecord = slot<ScrollSessionRecord>()
        coVerify(exactly = 1) { mockScrollAggregator.addSession(capture(capturedRecord)) }
        assertThat(capturedRecord.captured.packageName).isEqualTo(app1)
        assertThat(capturedRecord.captured.scrollAmount).isEqualTo(150)
        assertThat(capturedRecord.captured.sessionStartTime).isEqualTo(time1)
        assertThat(capturedRecord.captured.sessionEndTime).isEqualTo(time2)
        assertThat(capturedRecord.captured.sessionEndReason).isEqualTo(SessionManager.SessionEndReason.SCREEN_OFF)

        coVerify(exactly = 1) { mockDraftRepository.clearDraft() }
        assertThat(sessionManager.getCurrentAppPackage()).isNull()
    }

    @Test
    fun finalizeAndSaveCurrentSession_withSessionSpanningMidnight_splitsIntoTwoRecords() = testScope.runTest {
        every { DateUtil.getEndOfDayUtcMillis("2024-01-01") } returns endOfDay1
        every { DateUtil.getStartOfDayUtcMillis("2024-01-02") } returns startOfDay2

        sessionManager.startNewSession(app1, activityA, timeAcrossMidnightStart)
        sessionManager.updateCurrentSessionScroll(300, false)
        testScope.runCurrent()

        sessionManager.finalizeAndSaveCurrentSession(timeAcrossMidnightEnd, "SPLIT_TEST")
        testScope.advanceUntilIdle()

        val capturedRecords = mutableListOf<ScrollSessionRecord>()
        coVerify(exactly = 2) { mockScrollAggregator.addSession(capture(capturedRecords)) }

        val recordDay1 = capturedRecords.find { it.date == "2024-01-01" }!!
        val recordDay2 = capturedRecords.find { it.date == "2024-01-02" }!!

        assertThat(recordDay1.sessionStartTime).isEqualTo(timeAcrossMidnightStart)
        assertThat(recordDay1.sessionEndTime).isEqualTo(endOfDay1)
        assertThat(recordDay1.scrollAmount).isEqualTo(150)

        assertThat(recordDay2.sessionStartTime).isEqualTo(startOfDay2)
        assertThat(recordDay2.sessionEndTime).isEqualTo(timeAcrossMidnightEnd)
        assertThat(recordDay2.scrollAmount).isEqualTo(150)
    }

    @Test
    fun finalizeAndSaveCurrentSession_whenAggregatorFails_resavesDraftAndDoesNotClear() = testScope.runTest {
        val testDraftSaveInterval = 100L // from SessionManager testMode
        sessionManager.startNewSession(app1, activityA, time1)
        sessionManager.updateCurrentSessionScroll(100, false)

        // Ensure the initial draft save completes
        advanceTimeBy(testDraftSaveInterval + 1)
        advanceUntilIdle()
        coVerify(exactly = 1) { mockDraftRepository.saveDraft(any()) } // Initial save

        // Setup aggregator to fail
        coEvery { mockScrollAggregator.addSession(any()) } throws RuntimeException("DB insertion failed")

        // Trigger finalization
        sessionManager.finalizeAndSaveCurrentSession(time2, "FAIL_TEST")
        advanceUntilIdle()

        // Verify draft was saved again
        val capturedDrafts = mutableListOf<SessionDraft>()
        coVerify(exactly = 2) { mockDraftRepository.saveDraft(capture(capturedDrafts)) }
        assertThat(capturedDrafts[1].scrollAmount).isEqualTo(100)
        assertThat(capturedDrafts[1].packageName).isEqualTo(app1)
        
        // Verify clearDraft was not called
        coVerify(exactly = 0) { mockDraftRepository.clearDraft() }
    }

    // --- recoverSession ---

    @Test
    fun recoverSession_withNoDraft_doesNothing() = testScope.runTest {
        coEvery { mockDraftRepository.getDraft() } returns null
        sessionManager.recoverSession()
        testScope.runCurrent()
        assertThat(sessionManager.getCurrentAppPackage()).isNull()
    }

    @Test
    fun recoverSession_withValidDraft_restoresSessionStateAndFinalizes() = testScope.runTest {
        // Setup test data
        val validDraft = SessionDraft(app1, activityA, 123, time1, time2)
        coEvery { mockDraftRepository.getDraft() } returns validDraft

        // Mock date formatting for the recovery process
        every { DateUtil.formatUtcTimestampToLocalDateString(any()) } answers { "2024-01-01" }

        // Set the current time for recovery
        advanceTimeBy(time2)

        // Trigger recovery
        sessionManager.recoverSession()
        advanceUntilIdle()

        // Verify the session was properly finalized
        val capturedRecord = slot<ScrollSessionRecord>()
        coVerify(exactly = 1) { mockScrollAggregator.addSession(capture(capturedRecord)) }

        // Verify session details
        with(capturedRecord.captured) {
            assertThat(packageName).isEqualTo(app1)
            assertThat(scrollAmount).isEqualTo(123)
            assertThat(sessionStartTime).isEqualTo(time1)
            assertThat(sessionEndTime).isEqualTo(time2)
            assertThat(sessionEndReason).isEqualTo(SessionManager.SessionEndReason.RECOVERED_DRAFT)
        }

        // Verify cleanup
        coVerify(exactly = 1) { mockDraftRepository.clearDraft() }
        assertThat(sessionManager.getCurrentAppPackage()).isNull()
    }
}
