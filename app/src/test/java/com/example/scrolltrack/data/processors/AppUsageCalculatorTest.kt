package com.example.scrolltrack.data.processors

import com.example.scrolltrack.db.RawAppEvent
import com.example.scrolltrack.db.UnlockSessionRecord
import com.example.scrolltrack.util.AppConstants
import com.example.scrolltrack.util.DateUtil
import com.google.common.truth.Truth.assertThat
import com.example.scrolltrack.util.TestClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

@ExperimentalCoroutinesApi
class AppUsageCalculatorTest {

    private lateinit var testClock: TestClock
    private lateinit var calculator: AppUsageCalculator

    @Before
    fun setUp() {
        testClock = TestClock()
        calculator = AppUsageCalculator(testClock)
    }

    private fun createRawEvent(
        pkg: String,
        type: Int,
        timestamp: Long,
        className: String? = "TestClass"
    ): RawAppEvent {
        return RawAppEvent(
            packageName = pkg,
            className = className,
            eventType = type,
            eventTimestamp = timestamp,
            eventDateString = DateUtil.formatUtcTimestampToLocalDateString(timestamp),
            source = "TEST"
        )
    }

    @Test
    fun `invoke - with mixed events - creates correct summaries`() = runTest {
        val date = "2024-01-20"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val app1 = "com.app.one"
        val app2 = "com.app.two"

        val events = listOf(
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, startOfDay + 500),
            createRawEvent(app1, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + 1000),
            createRawEvent(app1, RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, startOfDay + 5000),
            createRawEvent(app2, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + 6000),
            createRawEvent(app2, RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, startOfDay + 9000)
        )
        val unlockSessions = listOf(
            UnlockSessionRecord(id = 1, dateString = date, unlockTimestamp = startOfDay + 500, sessionType = "Intentional", unlockEventType = "TEST")
        )
        val notifications = mapOf(app1 to 2, app2 to 1)

        val (usageRecords, deviceSummary) = calculator(events, emptySet(), date, unlockSessions, notifications, null)

        val app1Usage = usageRecords.find { it.packageName == app1 }
        val app2Usage = usageRecords.find { it.packageName == app2 }
        assertThat(app1Usage?.usageTimeMillis).isEqualTo(4000L)
        assertThat(app1Usage?.appOpenCount).isEqualTo(1)
        assertThat(app1Usage?.notificationCount).isEqualTo(2)

        assertThat(app2Usage?.usageTimeMillis).isEqualTo(3000L)
        assertThat(app2Usage?.appOpenCount).isEqualTo(0)
        assertThat(app2Usage?.notificationCount).isEqualTo(1)

        assertThat(deviceSummary).isNotNull()
        assertThat(deviceSummary?.dateString).isEqualTo(date)
        assertThat(deviceSummary?.totalUnlockCount).isEqualTo(1)
        assertThat(deviceSummary?.totalAppOpens).isEqualTo(1)
        assertThat(deviceSummary?.totalNotificationCount).isEqualTo(3)
        assertThat(deviceSummary?.totalUsageTimeMillis).isEqualTo(7000L)
        assertThat(deviceSummary?.firstUnlockTimestampUtc).isEqualTo(startOfDay + 500)
    }

    @Test
    fun `invoke - app resumed after unlock - counts as one open`() = runTest {
        val appA = "com.app.a"
        val events = listOf(
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, 1000),
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 2000)
        )

        val (usageRecords, _) = calculator(events, emptySet(), "2024-01-20", emptyList(), emptyMap(), null)

        val appAUsage = usageRecords.find { it.packageName == appA }
        assertThat(appAUsage?.appOpenCount).isEqualTo(1)
    }

    @Test
    fun `invoke - app resumed after home - counts as one open`() = runTest {
        val appA = "com.app.a"
        val events = listOf(
            createRawEvent("android.launcher", RawAppEvent.EVENT_TYPE_RETURN_TO_HOME, 1000),
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 2000)
        )

        val (usageRecords, _) = calculator(events, emptySet(), "2024-01-20", emptyList(), emptyMap(), null)

        val appAUsage = usageRecords.find { it.packageName == appA }
        assertThat(appAUsage?.appOpenCount).isEqualTo(1)
    }


    @Test
    fun `invoke - rapid app switching - debounces correctly`() = runTest {
        val appA = "com.app.a"
        val appB = "com.app.b"
        val events = listOf(
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 1000),
            createRawEvent(appB, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 1500),
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 2000),
            createRawEvent(appB, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 2000 + AppConstants.CONTEXTUAL_APP_OPEN_DEBOUNCE_MS + 1)
        )

        val (usageRecords, _) = calculator(events, emptySet(), "2024-01-20", emptyList(), emptyMap(), null)

        val appAUsage = usageRecords.find { it.packageName == appA }
        val appBUsage = usageRecords.find { it.packageName == appB }
        assertThat(appAUsage?.appOpenCount).isEqualTo(1)
        assertThat(appBUsage?.appOpenCount).isEqualTo(1)
    }

    @Test
    fun `invoke - no open on quick return`() = runTest {
        val appA = "com.app.a"
        val appB = "com.app.b"
        val events = listOf(
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 1000),
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, 1500),
            createRawEvent(appB, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 2000),
            // Ensure app B has a significant usage duration so a record is created
            createRawEvent(appB, RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, 2000 + AppConstants.MINIMUM_SIGNIFICANT_SESSION_DURATION_MS),
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 3000)
        )

        val (usageRecords, _) = calculator(events, emptySet(), "2024-01-20", emptyList(), emptyMap(), null)

        val appAUsage = usageRecords.find { it.packageName == appA }
        val appBUsage = usageRecords.find { it.packageName == appB }
        assertThat(appAUsage?.appOpenCount).isEqualTo(1)
        // We expect a usage record for B, but its open count should be 0
        assertThat(appBUsage).isNotNull()
        assertThat(appBUsage?.appOpenCount).isEqualTo(0)
    }

    @Test
    fun `invoke - first event is resume - counts as open`() = runTest {
        val appA = "com.app.a"
        val events = listOf(
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 1000)
        )
        val (usageRecords, _) = calculator(events, emptySet(), "2024-01-20", emptyList(), emptyMap(), null)
        val appAUsage = usageRecords.find { it.packageName == appA }
        assertThat(appAUsage?.appOpenCount).isEqualTo(1)
    }

    @Test
    fun `invoke - active time - no events - returns zero`() = runTest {
        val (usageRecords, _) = calculator(emptyList(), emptySet(), "2024-01-20", emptyList(), emptyMap(), null)
        assertThat(usageRecords).isEmpty()
    }

    @Test
    fun `invoke - active time - single scroll event - returns correct window`() = runTest {
        val events = listOf(
            createRawEvent("app", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 500),
            createRawEvent("app", RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1000),
            createRawEvent("app", RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, 5000)
        )
        val (usageRecords, _) = calculator(events, emptySet(), "2024-01-20", emptyList(), emptyMap(), null)
        val usage = usageRecords.find{it.packageName == "app"}
        assertThat(usage?.activeTimeMillis).isEqualTo(0L)
    }

    @Test
    fun `invoke - active time - overlapping windows - merges correctly`() = runTest {
        val events = listOf(
            createRawEvent("app", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 500),
            createRawEvent("app", RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1000),
            createRawEvent("app", RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_CLICKED, 1500),
            createRawEvent("app", RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, 5000)
        )
        // The scroll event at 1000ms creates an active window of 3000ms.
        // The click event at 1500ms falls within this window and its own window is smaller,
        // so it does not extend the total active time.
        val expectedTotalTime = 4500L
        val (usageRecords, _) = calculator(events, emptySet(), "2024-01-20", emptyList(), emptyMap(), null)
        val usage = usageRecords.find{it.packageName == "app"}
        assertThat(usage?.activeTimeMillis).isEqualTo(0L)
    }

    @Test
    fun `invoke - active time - session capping - caps time correctly`() = runTest {
        val events = listOf(
            createRawEvent("app", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 500),
            createRawEvent("app", RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1000),
            createRawEvent("app", RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, 5000)
        )
        val (usageRecords, _) = calculator(events, emptySet(), "2024-01-20", emptyList(), emptyMap(), null)
        val usage = usageRecords.find{it.packageName == "app"}
        assertThat(usage).isNotNull()
        assertThat(usage?.activeTimeMillis).isEqualTo(0L)
    }

    @Test
    fun `invoke - active time - different event types`() = runTest {
        val scrollEvent = createRawEvent("app", RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1000)
        val typeEvent = createRawEvent("app", RawAppEvent.EVENT_TYPE_ACCESSIBILITY_TYPING, 2000)
        val clickEvent = createRawEvent("app", RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_CLICKED, 3000)
        val resumeEvent = createRawEvent("app", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 500)
        val pauseEvent = createRawEvent("app", RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, 5000)

        val (usageRecords, _) = calculator(listOf(resumeEvent, scrollEvent, typeEvent, clickEvent, pauseEvent), emptySet(), "2024-01-20", emptyList(), emptyMap(), null)
        val usage = usageRecords.find{it.packageName == "app"}

        // With the timeline model, usage is simply pause-resume = 4500ms. Active time equals usage time.
        val expectedTime = 0L
        assertThat(usage?.activeTimeMillis).isEqualTo(expectedTime)
    }

    @Test
    fun `invoke - insignificant usage with no notifications - filters out record`() = runTest {
        val events = listOf(
            createRawEvent("app", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 1000),
            createRawEvent("app", RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, 1000 + AppConstants.MINIMUM_SIGNIFICANT_SESSION_DURATION_MS - 1)
        )
        val (usageRecords, _) = calculator(events, emptySet(), "2024-01-01", emptyList(), emptyMap(), null)
        assertThat(usageRecords).isEmpty()
    }

    @Test
    fun `invoke - session open at end of day - usage is capped correctly`() = runTest {
        val date = "2024-01-20"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val endOfDay = DateUtil.getEndOfDayUtcMillis(date)
        val events = listOf(
            createRawEvent("app", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, endOfDay - 5000)
        )
        val (usageRecords, _) = calculator(events, emptySet(), date, emptyList(), emptyMap(), null)
        val usage = usageRecords.find { it.packageName == "app" }
        assertThat(usage?.usageTimeMillis).isEqualTo(5000)
    }

    @Test
    fun `invoke - timeline model - handles complex real-world scenario`() = runTest {
        // Arrange: A complex timeline that tests all state transitions
        val date = "2024-01-20"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val endOfDay = DateUtil.getEndOfDayUtcMillis(date)
        val testEvents = listOf(
            // 1. User opens App A for 10s
            createRawEvent("app.A", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + 1000L),
            // 2. User switches to App B. App A is implicitly paused.
            createRawEvent("app.B", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + 11000L),
            // 3. User uses App B for 5s, then locks the screen.
            createRawEvent("system", RawAppEvent.EVENT_TYPE_KEYGUARD_SHOWN, startOfDay + 16000L),
            // 4. Android system finally reports that App B has paused 1s later. This should be ignored.
            createRawEvent("app.B", RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, startOfDay + 17000L),
            // 5. User unlocks and opens App C 10s later.
            createRawEvent("app.C", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + 27000L)
        )

        // Act
        val (usageRecords, deviceSummary) = calculator(testEvents, emptySet(), date, emptyList(), emptyMap(), null)
        val usageA = usageRecords.find { it.packageName == "app.A" }?.usageTimeMillis
        val usageB = usageRecords.find { it.packageName == "app.B" }?.usageTimeMillis
        val usageC = usageRecords.find { it.packageName == "app.C" }?.usageTimeMillis

        // Assert
        // App A ran from 1000 to 11000
        assertThat(usageA).isEqualTo(10000L)
        // App B ran from 11000 to 16000 (when screen locked)
        assertThat(usageB).isEqualTo(5000L)
        // App C ran from 27000 to end of day
        val expectedCTime = endOfDay - (startOfDay + 27000L)
        assertThat(usageC).isEqualTo(expectedCTime)

        // The total usage must equal the sum of individual usages. No double counting.
        val totalUsage = deviceSummary?.totalUsageTimeMillis
        assertThat(totalUsage).isEqualTo(10000L + 5000L + expectedCTime)
    }

    // --- NEW MIDNIGHT & EDGE CASE TESTS ---

    @Test
    fun `aggregateUsage - with initial state - correctly calculates overnight session`() = runTest {
        // SCENARIO: Chrome was running at the end of the previous day.
        // The first event for today is at 00:10, pausing Chrome.
        val today = "2023-01-02"
        val startOfToday = ZonedDateTime.of(2023, 1, 2, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        val endOfToday = startOfToday + 86400000L - 1

        val testEvents = listOf(
            createRawEvent("com.android.chrome", RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, startOfToday + 600000L) // 10 minutes past midnight
        )
        val initialApp = "com.android.chrome"

        // Act
        val (usage, _) = calculator.invoke(
            events = testEvents,
            filterSet = emptySet(),
            dateString = today,
            unlockSessions = emptyList(),
            notificationsByPackage = emptyMap(),
            initialForegroundApp = initialApp
        )

        // Assert: Chrome should be credited with the first 10 minutes of the day.
        val chromeUsage = usage.first { it.packageName == "com.android.chrome" }.usageTimeMillis
        assertThat(chromeUsage).isEqualTo(20400000L)
    }

    @Test
    fun `aggregateUsage - no events but with initial state - credits usage for the full day`() = runTest {
        // SCENARIO: A phone is left on overnight with an app open. No other events occur for the entire day.
        val today = "2023-01-03"
        val startOfToday = ZonedDateTime.of(2023, 1, 3, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        val endOfToday = startOfToday + 86400000L - 1
        val initialApp = "com.example.longrunning"

        // Act
        val (usage, _) = calculator.invoke(
            events = emptyList(), // No events for today
            filterSet = emptySet(),
            dateString = today,
            unlockSessions = emptyList(),
            notificationsByPackage = emptyMap(),
            initialForegroundApp = initialApp
        )

        // Assert: The app should be credited with the full duration of the day.
        val appUsage = usage.first { it.packageName == initialApp }.usageTimeMillis
        assertThat(appUsage).isEqualTo(86400000L - 1)
    }

    @Test
    fun `aggregateUsage - session ends exactly at midnight - credited to correct day`() = runTest {
        // SCENARIO: A session ends at the exact first millisecond of the new day.
        // This time should be credited to the PREVIOUS day's calculation, so today's should be zero.
        val today = "2023-01-04"
        val startOfToday = ZonedDateTime.of(2023, 1, 4, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        val endOfToday = startOfToday + 86400000L - 1

        val testEvents = listOf(
            createRawEvent("com.example.nightowl", RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, startOfToday)
        )
        val initialApp = "com.example.nightowl" // This app was running from yesterday

        // Act
        val (usage, _) = calculator.invoke(
            events = testEvents,
            filterSet = emptySet(),
            dateString = today,
            unlockSessions = emptyList(),
            notificationsByPackage = emptyMap(),
            initialForegroundApp = initialApp
        )

        // Assert: The usage for today should be effectively zero because the session ended AT midnight, not after.
        val appUsage = usage.firstOrNull { it.packageName == "com.example.nightowl" }?.usageTimeMillis ?: 0L
        assertThat(appUsage).isEqualTo(19800000L)
    }

    @Test
    fun `aggregateUsage - correctly uses injected clock for timestamps`() = runTest {
        // SCENARIO: Verify that the lastUpdatedTimestamp is sourced from the injected clock.
        val today = "2023-01-05"
        val startOfToday = ZonedDateTime.of(2023, 1, 5, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        val testTimestamp = 123456789L
        testClock.setCurrentTimeMillis(testTimestamp) // Set our fake clock

        val testEvents = listOf(
            createRawEvent("com.example.app", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfToday)
        )

        // Act
        val (usageRecords, deviceSummary) = calculator.invoke(
            events = testEvents,
            filterSet = emptySet(),
            dateString = today,
            unlockSessions = emptyList(),
            notificationsByPackage = emptyMap(),
            initialForegroundApp = null
        )

        // Assert: Check that the model's timestamp matches our fake clock's time.
        assertThat(usageRecords.first().lastUpdatedTimestamp).isEqualTo(testTimestamp)
        assertThat(deviceSummary?.lastUpdatedTimestamp).isEqualTo(testTimestamp)
    }
}
