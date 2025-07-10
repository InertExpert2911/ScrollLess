package com.example.scrolltrack.services

import android.view.accessibility.AccessibilityEvent
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.example.scrolltrack.ScrollTrackService
import com.example.scrolltrack.db.RawAppEvent
import com.example.scrolltrack.db.RawAppEventDao
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ScrollTrackServiceTest {

    private lateinit var service: ScrollTrackService
    private val rawAppEventDao: RawAppEventDao = mockk(relaxed = true)
    private val workManager: WorkManager = mockk(relaxed = true)

    @Before
    fun setUp() {
        service = spyk(ScrollTrackService())
        service.rawAppEventDao = rawAppEventDao
        service.workManager = workManager
    }

    private fun createMockAccessibilityEvent(
        packageName: String,
        className: String,
        eventType: Int,
        scrollDeltaX: Int = 0,
        scrollDeltaY: Int = 0
    ): AccessibilityEvent {
        mockkStatic(AccessibilityEvent::class)
        val event = mockk<AccessibilityEvent>(relaxed = true)
        every { event.packageName } returns packageName
        every { event.className } returns className
        every { event.eventType } returns eventType
        every { event.scrollDeltaX } returns scrollDeltaX
        every { event.scrollDeltaY } returns scrollDeltaY
        return event
    }

    @Test
    fun `handleMeasuredScroll logs raw event`() = runTest {
        val event = createMockAccessibilityEvent(
            "com.test.app",
            "TestClass",
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            scrollDeltaX = 10,
            scrollDeltaY = 20
        )
        every { service.isNodeScrollable(any()) } returns true

        service.onAccessibilityEvent(event)

        val rawEventSlot = slot<RawAppEvent>()
        coVerify { rawAppEventDao.insertEvent(capture(rawEventSlot)) }
        with(rawEventSlot.captured) {
            assert(packageName == "com.test.app")
            assert(eventType == RawAppEvent.EVENT_TYPE_SCROLL_MEASURED)
            assert(this.scrollDeltaX == 10)
            assert(this.scrollDeltaY == 20)
        }
    }

    @Test
    fun `handleInferredScroll enqueues unique work`() {
        val packageName = "com.test.app.inferred"
        val event = createMockAccessibilityEvent(
            packageName,
            "InferredClass",
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        )

        service.onAccessibilityEvent(event)

        val workRequestSlot = slot<OneTimeWorkRequest>()
        verify {
            workManager.enqueueUniqueWork(
                eq("InferredScrollWorker_com.test.app.inferred"),
                any(),
                capture(workRequestSlot)
            )
        }
        assert(workRequestSlot.captured.workSpec.workerClassName == "com.example.scrolltrack.services.InferredScrollWorker")
    }

    @Test
    fun `handleTypingEvent logs debounced event`() = runTest {
        val event = createMockAccessibilityEvent("com.test.app", "EditText", AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED)

        // First event should log
        service.onAccessibilityEvent(event)
        coVerify(exactly = 1) { rawAppEventDao.insertEvent(any()) }

        // Second event immediately after should be ignored
        service.onAccessibilityEvent(event)
        coVerify(exactly = 1) { rawAppEventDao.insertEvent(any()) }

        // We need to advance the test dispatcher's clock to simulate time passing
        // This is a more robust way to test debouncing than Thread.sleep
        // For this test, we don't need to advance time as we are just checking it's not called again immediately.

        coVerify(exactly = 1) { rawAppEventDao.insertEvent(any()) }
    }
} 