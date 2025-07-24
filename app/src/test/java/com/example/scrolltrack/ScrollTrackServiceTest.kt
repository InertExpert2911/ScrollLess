package com.example.scrolltrack

import android.app.Application
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.example.scrolltrack.db.RawAppEvent
import com.example.scrolltrack.db.RawAppEventDao
import com.example.scrolltrack.util.AppConstants
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
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
        // Clear previous interactions on mocks before each test
        clearMocks(rawAppEventDao, workManager, answers = false)
    }

    @Test
    fun `onAccessibilityEvent - logs measured scroll with correct deltas`() = runTest {
        val packageName = "com.example.testapp"
        val mockNode: AccessibilityNodeInfo = mockk(relaxed = true)
        every { mockNode.isScrollable } returns true

        // Use a relaxed mock to avoid calling real methods on AccessibilityEvent
        val event: AccessibilityEvent = mockk(relaxed = true)
        every { event.eventType } returns AccessibilityEvent.TYPE_VIEW_SCROLLED
        every { event.packageName } returns packageName
        every { event.className } returns "android.widget.ScrollView"
        every { event.source } returns mockNode
        every { event.scrollDeltaX } returns 50
        every { event.scrollDeltaY } returns 100

        service.onAccessibilityEvent(event)
        
        val slot = slot<RawAppEvent>()
        coVerify { rawAppEventDao.insertEvent(capture(slot)) }
        
        val capturedEvent = slot.captured
        assertThat(capturedEvent.packageName).isEqualTo(packageName)
        assertThat(capturedEvent.eventType).isEqualTo(RawAppEvent.EVENT_TYPE_SCROLL_MEASURED)
        assertThat(capturedEvent.scrollDeltaX).isEqualTo(50)
        assertThat(capturedEvent.scrollDeltaY).isEqualTo(100)
    }

    @Test
    fun `isNodeScrollable - returns true for scrollable node`() {
        val scrollableNode: AccessibilityNodeInfo = mockk(relaxed = true)
        every { scrollableNode.isScrollable } returns true

        assertThat(service.isNodeScrollable(scrollableNode)).isTrue()
    }

    @Test
    fun `isNodeScrollable - returns true for child of scrollable node`() {
        val childNode: AccessibilityNodeInfo = mockk(relaxed = true)
        val parentNode: AccessibilityNodeInfo = mockk(relaxed = true)

        every { childNode.isScrollable } returns false
        every { childNode.parent } returns parentNode
        every { parentNode.isScrollable } returns true

        assertThat(service.isNodeScrollable(childNode)).isTrue()
        verify { parentNode.recycle() } // Ensure nodes are recycled
    }

    @Test
    fun `isNodeScrollable - returns false for non-scrollable hierarchy`() {
        val node1: AccessibilityNodeInfo = mockk(relaxed = true)
        val node2: AccessibilityNodeInfo = mockk(relaxed = true)
        every { node1.isScrollable } returns false
        every { node1.parent } returns node2
        every { node2.isScrollable } returns false
        every { node2.parent } returns null

        assertThat(service.isNodeScrollable(node1)).isFalse()
        verify { node1.recycle() }
        verify { node2.recycle() }
    }

    @Test
    fun `onAccessibilityEvent - ignores events from filtered packages`() = runTest {
        val event: AccessibilityEvent = mockk(relaxed = true)
        every { event.eventType } returns AccessibilityEvent.TYPE_VIEW_SCROLLED
        every { event.packageName } returns "com.android.systemui" // A filtered package
        every { event.scrollDeltaY } returns 100

        service.onAccessibilityEvent(event)

        coVerify(exactly = 0) { rawAppEventDao.insertEvent(any()) }
    }
}
