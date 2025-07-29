package com.example.scrolltrack.data.processors

import com.example.scrolltrack.db.DailyAppUsageRecord
import com.example.scrolltrack.db.RawAppEvent
import com.example.scrolltrack.db.UnlockSessionRecord
import com.example.scrolltrack.util.AppConstants
import com.example.scrolltrack.util.DateUtil
import com.example.scrolltrack.util.TestClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
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
   fun `calculateAppOpens - rapid resume events - debounces correctly`() = runTest {
       val appA = "com.app.a"
       val events = listOf(
           createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, 1000),
           createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 2000), // First open
           createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 3000)  // Should be ignored (debounce)
       )
       val (usageRecords, _) = calculator(events, emptySet(), "2024-01-20", emptyList(), emptyMap(), null)
       val appAUsage = usageRecords.find { it.packageName == appA }
       assertThat(appAUsage?.appOpenCount).isEqualTo(1)
   }

   @Test
   fun `calculateAppOpens - resume after long gap - counts as new open`() = runTest {
       val appA = "com.app.a"
       val debounceTime = AppConstants.CONTEXTUAL_APP_OPEN_DEBOUNCE_MS
       val events = listOf(
           createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 1000), // First open
           createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 1000 + debounceTime + 1) // Second open
       )
       val (usageRecords, _) = calculator(events, emptySet(), "2024-01-20", emptyList(), emptyMap(), null)
       val appAUsage = usageRecords.find { it.packageName == appA }
       assertThat(appAUsage?.appOpenCount).isEqualTo(2)
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
   fun `invoke - with filterSet - filters out specified package`() = runTest {
       val date = "2024-01-20"
       val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
       val app1 = "com.app.one"
       val app2 = "com.app.two" // This one will be filtered
       val events = listOf(
           createRawEvent(app1, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + 1000),
           createRawEvent(app1, RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, startOfDay + 5000),
           createRawEvent(app2, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + 6000),
           createRawEvent(app2, RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, startOfDay + 9000)
       )
       val notifications = mapOf(app1 to 2, app2 to 1)
       val filterSet = setOf(app2)

       val (usageRecords, deviceSummary) = calculator(events, filterSet, date, emptyList(), notifications, null)

       assertThat(usageRecords.any { it.packageName == app2 }).isFalse()
       assertThat(deviceSummary?.totalNotificationCount).isEqualTo(2) // Only app1's notifications
       assertThat(deviceSummary?.totalUsageTimeMillis).isEqualTo(4000L) // Only app1's usage
   }

   @Test
   fun `invoke - no significant usage - but has notifications - record is kept`() = runTest {
       val app = "com.app.a"
       val events = listOf(
           createRawEvent(app, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 1000),
           createRawEvent(app, RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, 1000 + AppConstants.MINIMUM_SIGNIFICANT_SESSION_DURATION_MS - 1)
       )
       val notifications = mapOf(app to 1)
       val (usageRecords, _) = calculator(events, emptySet(), "2024-01-01", emptyList(), notifications, null)
       assertThat(usageRecords).hasSize(1)
       assertThat(usageRecords.first().packageName).isEqualTo(app)
   }

   @Test
   fun `calculateActiveTime - different event types - returns correct merged time`() = runTest {
       val date = "2024-01-20"
       val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
       val app = "com.app.a"
       val events = listOf(
           createRawEvent(app, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay),
           // Scroll: [1000, 1000 + 3000]
           createRawEvent(app, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, startOfDay + 1000),
           // Tap: [2500, 2500 + 2000] -> Merges with scroll to [1000, 4500]
           createRawEvent(app, RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_CLICKED, startOfDay + 2500),
           // Type: [10000, 10000 + 8000] -> Separate interval
           createRawEvent(app, RawAppEvent.EVENT_TYPE_ACCESSIBILITY_TYPING, startOfDay + 10000),
           createRawEvent(app, RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, startOfDay + 20000)
       )

       val (usageRecords, _) = calculator(events, emptySet(), date, emptyList(), emptyMap(), null)
       val usage = usageRecords.find { it.packageName == app }

       // Expected: (4500 - 1000) + (18000 - 10000) = 3500 + 8000 = 11500
       assertThat(usage?.activeTimeMillis).isEqualTo(11500L)
   }

   @Test
   fun `calculateActiveTime - interval capped by session end - returns correct capped time`() = runTest {
       val date = "2024-01-20"
       val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
       val app = "com.app.a"
       val events = listOf(
           createRawEvent(app, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay),
           // Scroll starts at 8000, window is 3000ms, so it would end at 11000
           createRawEvent(app, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, startOfDay + 8000),
           // Session ends at 10000, capping the active time
           createRawEvent(app, RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, startOfDay + 10000)
       )

       val (usageRecords, _) = calculator(events, emptySet(), date, emptyList(), emptyMap(), null)
       val usage = usageRecords.find { it.packageName == app }

       // Expected: 10000 (session end) - 8000 (scroll start) = 2000
       assertThat(usage?.activeTimeMillis).isEqualTo(2000L)
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
        val todayStart = ZonedDateTime.of(2023, 1, 2, 0, 0, 0, 0, ZoneId.of("UTC"))
        val periodStartDate = todayStart.toInstant().toEpochMilli()
        val periodEndDate = todayStart.plusDays(1).toInstant().toEpochMilli()

        val events = listOf(
            createRawEvent(
                "com.example.other_app",
                RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED,
                todayStart.plusMinutes(10).toInstant().toEpochMilli()
            )
        )

        // Act
        val (usageMap, _) = calculator.aggregateUsage(events, periodStartDate, periodEndDate, "com.android.chrome")
        val usage = usageMap.entries.map { DailyAppUsageRecord(dateString = "2023-01-02", packageName = it.key, usageTimeMillis = it.value.first, activeTimeMillis = it.value.second) }

        // Assert: Chrome should be credited with the first 10 minutes of the day.
        val chromeUsage = usage.first { it.packageName == "com.android.chrome" }.usageTimeMillis
        assertThat(chromeUsage).isEqualTo(600000L)
    }

    @Test
    fun `aggregateUsage - no events but with initial state - credits usage for the full day`() = runTest {
        val todayStart = ZonedDateTime.of(2023, 1, 2, 0, 0, 0, 0, ZoneId.of("UTC"))
        val periodStartDate = todayStart.toInstant().toEpochMilli()
        val periodEndDate = todayStart.plusDays(1).toInstant().toEpochMilli()
        val initialApp = "com.example.app_that_was_open"

        // Act
        val (usageMap, _) = calculator.aggregateUsage(emptyList(), periodStartDate, periodEndDate, initialApp)
        val usage = usageMap.entries.map { DailyAppUsageRecord(dateString = "2023-01-02", packageName = it.key, usageTimeMillis = it.value.first, activeTimeMillis = it.value.second) }

        // Assert: The app should be credited with the full duration of the day.
        val appUsage = usage.first { it.packageName == initialApp }.usageTimeMillis
        assertThat(appUsage).isEqualTo(86400000L)
    }

    @Test
    fun `aggregateUsage - session ends exactly at midnight - credited to correct day`() = runTest {
        val todayStart = ZonedDateTime.of(2023, 1, 2, 0, 0, 0, 0, ZoneId.of("UTC"))
        val periodStartDate = todayStart.toInstant().toEpochMilli()
        val periodEndDate = todayStart.plusDays(1).toInstant().toEpochMilli()

        val events = listOf(
            createRawEvent(
                "com.example.nightowl",
                RawAppEvent.EVENT_TYPE_APP_IN_FOREGROUND,
                todayStart.minusHours(5).minusMinutes(30).toInstant().toEpochMilli()
            ),
            createRawEvent(
                "com.example.nightowl",
                RawAppEvent.EVENT_TYPE_SCREEN_OFF,
                todayStart.toInstant().toEpochMilli()
            )
        )

        // Act
        val (usageMap, _) = calculator.aggregateUsage(events, periodStartDate, periodEndDate, null)
        val usage = usageMap.entries.map { DailyAppUsageRecord(dateString = "2023-01-02", packageName = it.key, usageTimeMillis = it.value.first, activeTimeMillis = it.value.second) }

        // Assert: The usage for today should be effectively zero because the session ended AT midnight, not after.
        val appUsage = usage.firstOrNull { it.packageName == "com.example.nightowl" }?.usageTimeMillis ?: 0L
        assertThat(appUsage).isEqualTo(0)
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
