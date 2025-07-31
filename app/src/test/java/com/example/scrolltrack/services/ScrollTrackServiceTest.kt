package com.example.scrolltrack.services

import android.app.Application
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import androidx.test.core.app.ApplicationProvider
import com.example.scrolltrack.ScrollTrackService
import com.example.scrolltrack.data.LimitsRepository
import com.example.scrolltrack.data.SettingsRepository
import com.example.scrolltrack.db.DailyAppUsageDao
import com.example.scrolltrack.services.LimitMonitor
import com.example.scrolltrack.db.RawAppEvent
import com.example.scrolltrack.db.RawAppEventDao
import com.example.scrolltrack.util.DateUtil
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class ScrollTrackServiceTest {

    private lateinit var service: ScrollTrackService
    private lateinit var controller: ServiceController<ScrollTrackService>
    private lateinit var rawAppEventDao: RawAppEventDao
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var limitsRepository: LimitsRepository
    private lateinit var dailyAppUsageDao: DailyAppUsageDao
    private lateinit var dateUtil: DateUtil
    private lateinit var limitMonitor: LimitMonitor
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        rawAppEventDao = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        limitsRepository = mockk(relaxed = true)
        dailyAppUsageDao = mockk(relaxed = true)
        dateUtil = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
        limitMonitor = mockk(relaxed = true)

        // Use buildService().get() to instantiate the service without calling onCreate.
        controller = Robolectric.buildService(ScrollTrackService::class.java)
        service = controller.get()

        // Now, inject the mocks BEFORE onCreate is called.
        service.rawAppEventDao = rawAppEventDao
        service.settingsRepository = settingsRepository
        service.limitMonitor = limitMonitor
        service.serviceScope = CoroutineScope(testDispatcher)

        // Mock settingsRepository because it's used in onCreate
        coEvery { settingsRepository.screenDpi } returns flowOf(160)

        coEvery { rawAppEventDao.insertEvent(any()) } returns Unit
    }

    @After
    fun tearDown() {
        if (::controller.isInitialized) {
            controller.destroy()
        }
        unmockkAll()
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

    @Test
    fun `onCreate - registers unlock receiver and logs event`() {
        controller.create()
        val shadowApp = org.robolectric.Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>())
        assertThat(shadowApp.registeredReceivers.any { it.broadcastReceiver == service.unlockReceiver }).isTrue()
        coVerify { rawAppEventDao.insertEvent(match { it.eventType == com.example.scrolltrack.db.RawAppEvent.EVENT_TYPE_SERVICE_STARTED }) }
    }

    @Test
    fun `onDestroy - unregisters receiver, flushes scrolls, and logs event`() = runTest(testDispatcher) {
        controller.create() // We need to create before we can destroy

        // Create a pending scroll event to test flushing
        val inferredEvent = mockk<AccessibilityEvent>(relaxed = true) {
            every { eventType } returns AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            every { packageName } returns "com.example.app"
        }
        service.onAccessibilityEvent(inferredEvent)
        advanceTimeBy(100) // Allow debounce job to start

        controller.destroy()

        val shadowApp = org.robolectric.Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>())
        assertThat(shadowApp.registeredReceivers.none { it.broadcastReceiver == service.unlockReceiver }).isTrue()

        // Verify that the pending scroll was flushed (logged)
        coVerify { rawAppEventDao.insertEvent(match { it.eventType == RawAppEvent.EVENT_TYPE_SCROLL_INFERRED }) }

        // Verify service stop event was logged
        coVerify { rawAppEventDao.insertEvent(match { it.eventType == com.example.scrolltrack.db.RawAppEvent.EVENT_TYPE_SERVICE_STOPPED }) }
    }

    @Test
    fun `onInterrupt - flushes pending scrolls`() = runTest(testDispatcher) {
        controller.create()

        // Create a pending scroll event
        val inferredEvent = mockk<AccessibilityEvent>(relaxed = true) {
            every { eventType } returns AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            every { packageName } returns "com.example.app"
        }
        service.onAccessibilityEvent(inferredEvent)
        advanceTimeBy(100)

        service.onInterrupt()
        advanceUntilIdle()

        // Verify that the pending scroll was flushed (logged)
        coVerify { rawAppEventDao.insertEvent(match { it.eventType == RawAppEvent.EVENT_TYPE_SCROLL_INFERRED }) }
    }

    @Test
    fun `handleGenericEvent - logs click and focus events`() {
        val clickEvent = mockk<AccessibilityEvent>(relaxed = true) {
            every { eventType } returns AccessibilityEvent.TYPE_VIEW_CLICKED
            every { packageName } returns "com.example.app"
        }
        service.onAccessibilityEvent(clickEvent)
        coVerify { rawAppEventDao.insertEvent(match { it.eventType == com.example.scrolltrack.db.RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_CLICKED }) }

        val focusEvent = mockk<AccessibilityEvent>(relaxed = true) {
            every { eventType } returns AccessibilityEvent.TYPE_VIEW_FOCUSED
            every { packageName } returns "com.example.app"
        }
        service.onAccessibilityEvent(focusEvent)
        coVerify { rawAppEventDao.insertEvent(match { it.eventType == com.example.scrolltrack.db.RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_FOCUSED }) }
    }

    @Test
    fun `handleWindowStateChange - flushes pending scrolls and updates foreground app`() = runTest(testDispatcher) {
        controller.create()

        // Create a pending scroll event to be flushed
        val inferredEvent = mockk<AccessibilityEvent>(relaxed = true) {
            every { eventType } returns AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            every { packageName } returns "com.example.app"
        }
        service.onAccessibilityEvent(inferredEvent)
        advanceTimeBy(100) // Let debounce job start

        val windowEvent = mockk<AccessibilityEvent>(relaxed = true) {
            every { eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            every { packageName } returns "com.example.newapp"
        }
        service.onAccessibilityEvent(windowEvent)
        advanceUntilIdle()

        // Verify scroll was flushed
        coVerify { rawAppEventDao.insertEvent(match { it.eventType == RawAppEvent.EVENT_TYPE_SCROLL_INFERRED }) }

        // Verify foreground app was updated
        assertThat(service.currentForegroundApp).isEqualTo("com.example.newapp")
    }

    @Test
    fun `unlockReceiver - logs user unlocked event`() {
        val intent = mockk<android.content.Intent> {
            every { action } returns android.content.Intent.ACTION_USER_UNLOCKED
        }
        service.unlockReceiver.onReceive(mockContext, intent)
        coVerify { rawAppEventDao.insertEvent(match { it.eventType == com.example.scrolltrack.db.RawAppEvent.EVENT_TYPE_USER_UNLOCKED }) }
    }
}
