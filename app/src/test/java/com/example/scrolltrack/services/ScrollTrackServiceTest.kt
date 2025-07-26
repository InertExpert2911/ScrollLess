package com.example.scrolltrack.services

import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.example.scrolltrack.data.SettingsRepository
import com.example.scrolltrack.db.RawAppEventDao
import com.example.scrolltrack.ScrollTrackService
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class ScrollTrackServiceTest {

    private lateinit var service: ScrollTrackService
    private lateinit var rawAppEventDao: RawAppEventDao
    private lateinit var settingsRepository: SettingsRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        rawAppEventDao = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        
        service = ScrollTrackService()
        service.rawAppEventDao = rawAppEventDao
        service.settingsRepository = settingsRepository

        service.serviceScope = CoroutineScope(testDispatcher)

        // Mock the settings repository to return a default DPI
        coEvery { settingsRepository.screenDpi } returns flowOf(160)
        coEvery { rawAppEventDao.insertEvent(any()) } returns Unit
    }

    @Test
    fun `test typing debounce logs only one event`() = runTest(testDispatcher) {
        val appPackage = "com.example.app"
        service.onAccessibilityEvent(mockk(relaxed = true) {
            every { eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            every { packageName } returns appPackage
        })

        val event = mockk<AccessibilityEvent>(relaxed = true)
        every { event.eventType } returns AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        every { event.packageName } returns appPackage

        // Simulate multiple rapid typing events
        service.onAccessibilityEvent(event)
        service.onAccessibilityEvent(event)
        service.onAccessibilityEvent(event)

        // Advance time past the debounce threshold
        advanceTimeBy(3100)
        advanceUntilIdle()

        // Verify that only one event was logged
        coVerify(exactly = 1) { rawAppEventDao.insertEvent(any()) }
    }

    @Test
    fun `test inferred scroll debounce logs only one event`() = runTest(testDispatcher) {
        val appPackage = "com.example.app"
        service.onAccessibilityEvent(mockk(relaxed = true) {
            every { eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            every { packageName } returns appPackage
        })

        val event = mockk<AccessibilityEvent>(relaxed = true)
        every { event.eventType } returns AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        every { event.packageName } returns appPackage

        // Simulate multiple rapid scroll events
        service.onAccessibilityEvent(event)
        service.onAccessibilityEvent(event)
        service.onAccessibilityEvent(event)

        // Advance time past the debounce threshold
        advanceTimeBy(600)
        advanceUntilIdle()

        // Verify that only one event was logged
        coVerify(exactly = 1) { rawAppEventDao.insertEvent(any()) }
    }

    @Test
    fun `test measured scroll is prioritized over inferred`() = runTest(testDispatcher) {
        val appPackage = "com.example.app"
        service.onAccessibilityEvent(mockk(relaxed = true) {
            every { eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            every { packageName } returns appPackage
        })

        val scrollableNode = mockk<android.view.accessibility.AccessibilityNodeInfo>(relaxed = true)
        every { scrollableNode.isScrollable } returns true

        val measuredEvent = mockk<AccessibilityEvent>(relaxed = true)
        every { measuredEvent.eventType } returns AccessibilityEvent.TYPE_VIEW_SCROLLED
        every { measuredEvent.packageName } returns appPackage
        every { measuredEvent.scrollX } returns 0
        every { measuredEvent.scrollY } returns 100
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            every { measuredEvent.scrollDeltaY } returns 100
        }
        every { measuredEvent.source } returns scrollableNode

        val inferredEvent = mockk<AccessibilityEvent>(relaxed = true)
        every { inferredEvent.eventType } returns AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        every { inferredEvent.packageName } returns appPackage

        // Simulate a measured scroll event, followed by inferred scroll events
        service.onAccessibilityEvent(measuredEvent)
        service.onAccessibilityEvent(inferredEvent)
        service.onAccessibilityEvent(inferredEvent)

        // Advance time
        advanceTimeBy(600)
        advanceUntilIdle()

        // Verify that only the measured scroll event was logged
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            coVerify(exactly = 1) { rawAppEventDao.insertEvent(any()) }
        } else {
            coVerify(exactly = 0) { rawAppEventDao.insertEvent(any()) }
        }
    }

    @Test
    fun `test inferred scroll is ignored during cooldown`() = runTest(testDispatcher) {
        val appPackage = "com.example.app"
        service.onAccessibilityEvent(mockk(relaxed = true) {
            every { eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            every { packageName } returns appPackage
        })

        val scrollableNode = mockk<android.view.accessibility.AccessibilityNodeInfo>(relaxed = true)
        every { scrollableNode.isScrollable } returns true

        val measuredEvent = mockk<AccessibilityEvent>(relaxed = true)
        every { measuredEvent.eventType } returns AccessibilityEvent.TYPE_VIEW_SCROLLED
        every { measuredEvent.packageName } returns appPackage
        every { measuredEvent.scrollX } returns 0
        every { measuredEvent.scrollY } returns 100
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            every { measuredEvent.scrollDeltaY } returns 100
        }
        every { measuredEvent.source } returns scrollableNode

        val inferredEvent = mockk<AccessibilityEvent>(relaxed = true)
        every { inferredEvent.eventType } returns AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        every { inferredEvent.packageName } returns "com.example.app"

        // Simulate a measured scroll event, followed immediately by an inferred scroll event
        service.onAccessibilityEvent(measuredEvent)
        service.onAccessibilityEvent(inferredEvent)

        // Advance time
        advanceTimeBy(600)
        advanceUntilIdle()

        // Verify that only the measured scroll event was logged
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            coVerify(exactly = 1) { rawAppEventDao.insertEvent(any()) }
        } else {
            coVerify(exactly = 0) { rawAppEventDao.insertEvent(any()) }
        }
    }

    @Test
    fun `test window state change flushes pending scroll`() = runTest(testDispatcher) {
        val appPackage = "com.example.app"
        service.onAccessibilityEvent(mockk(relaxed = true) {
            every { eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            every { packageName } returns appPackage
        })

        val inferredEvent = mockk<AccessibilityEvent>(relaxed = true)
        every { inferredEvent.eventType } returns AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        every { inferredEvent.packageName } returns appPackage

        val windowEvent = mockk<AccessibilityEvent>(relaxed = true)
        every { windowEvent.eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        every { windowEvent.packageName } returns "com.example.newapp"

        // Simulate an inferred scroll event, then a window state change
        service.onAccessibilityEvent(inferredEvent)
        advanceTimeBy(100) // Allow the debounce job to start
        service.onAccessibilityEvent(windowEvent)
        advanceUntilIdle()

        // Verify that the inferred scroll event was logged immediately
        coVerify(exactly = 1) { rawAppEventDao.insertEvent(any()) }
    }

    @Test
    fun `isNodeScrollable - returns true for scrollable node`() {
        val scrollableNode: android.view.accessibility.AccessibilityNodeInfo = mockk(relaxed = true)
        every { scrollableNode.isScrollable } returns true

        assertThat(service.isNodeScrollable(scrollableNode)).isTrue()
    }

    @Test
    fun `isNodeScrollable - returns true for child of scrollable node`() {
        val childNode: android.view.accessibility.AccessibilityNodeInfo = mockk(relaxed = true)
        val parentNode: android.view.accessibility.AccessibilityNodeInfo = mockk(relaxed = true)

        every { childNode.isScrollable } returns false
        every { childNode.parent } returns parentNode
        every { parentNode.isScrollable } returns true

        assertThat(service.isNodeScrollable(childNode)).isTrue()
    }

    @Test
    fun `isNodeScrollable - returns false for non-scrollable hierarchy`() {
        val node1: android.view.accessibility.AccessibilityNodeInfo = mockk(relaxed = true)
        val node2: android.view.accessibility.AccessibilityNodeInfo = mockk(relaxed = true)
        every { node1.isScrollable } returns false
        every { node1.parent } returns node2
        every { node2.isScrollable } returns false
        every { node2.parent } returns null

        assertThat(service.isNodeScrollable(node1)).isFalse()
    }
}
