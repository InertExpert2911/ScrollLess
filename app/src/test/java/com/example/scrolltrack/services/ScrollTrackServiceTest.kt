package com.example.scrolltrack.services

import android.view.accessibility.AccessibilityEvent
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.example.scrolltrack.ScrollTrackService
import com.example.scrolltrack.db.RawAppEvent
import com.example.scrolltrack.db.RawAppEventDao
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject

@HiltAndroidTest
@Config(application = HiltTestApplication::class, sdk = [28])
@RunWith(RobolectricTestRunner::class)
@ExperimentalCoroutinesApi
class ScrollTrackServiceTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var rawAppEventDao: RawAppEventDao

    @Inject
    lateinit var workManager: WorkManager

    private lateinit var service: ScrollTrackService

    @Before
    fun setUp() {
        hiltRule.inject()
        service = Robolectric.buildService(ScrollTrackService::class.java).create().get()
        clearMocks(rawAppEventDao, workManager, answers = false)
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
}
