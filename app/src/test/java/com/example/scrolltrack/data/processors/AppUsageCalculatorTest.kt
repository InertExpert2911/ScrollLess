package com.example.scrolltrack.data.processors

import com.example.scrolltrack.db.RawAppEvent
import com.example.scrolltrack.db.UnlockSessionRecord
import com.example.scrolltrack.util.AppConstants
import com.example.scrolltrack.util.DateUtil
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@ExperimentalCoroutinesApi
class AppUsageCalculatorTest {

    private val calculator = AppUsageCalculator()

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

        val (usageRecords, deviceSummary) = calculator(events, emptySet(), date, unlockSessions, notifications)

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

        val (usageRecords, _) = calculator(events, emptySet(), "2024-01-20", emptyList(), emptyMap())

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

        val (usageRecords, _) = calculator(events, emptySet(), "2024-01-20", emptyList(), emptyMap())

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

        val (usageRecords, _) = calculator(events, emptySet(), "2024-01-20", emptyList(), emptyMap())

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

        val (usageRecords, _) = calculator(events, emptySet(), "2024-01-20", emptyList(), emptyMap())

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
        val (usageRecords, _) = calculator(events, emptySet(), "2024-01-20", emptyList(), emptyMap())
        val appAUsage = usageRecords.find { it.packageName == appA }
        assertThat(appAUsage?.appOpenCount).isEqualTo(1)
    }

    @Test
    fun `invoke - active time - no events - returns zero`() = runTest {
        val (usageRecords, _) = calculator(emptyList(), emptySet(), "2024-01-20", emptyList(), emptyMap())
        assertThat(usageRecords).isEmpty()
    }

    @Test
    fun `invoke - active time - single scroll event - returns correct window`() = runTest {
        val events = listOf(
            createRawEvent("app", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 500),
            createRawEvent("app", RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1000),
            createRawEvent("app", RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, 5000)
        )
        val (usageRecords, _) = calculator(events, emptySet(), "2024-01-20", emptyList(), emptyMap())
        val usage = usageRecords.find{it.packageName == "app"}
        assertThat(usage?.activeTimeMillis).isEqualTo(AppConstants.ACTIVE_TIME_SCROLL_WINDOW_MS)
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
        val expectedTotalTime = 3000L
        val (usageRecords, _) = calculator(events, emptySet(), "2024-01-20", emptyList(), emptyMap())
        val usage = usageRecords.find{it.packageName == "app"}
        assertThat(usage?.activeTimeMillis).isEqualTo(expectedTotalTime)
    }

    @Test
    fun `invoke - active time - session capping - caps time correctly`() = runTest {
        val events = listOf(
            createRawEvent("app", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 500),
            createRawEvent("app", RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1000),
            createRawEvent("app", RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, 5000)
        )
        val (usageRecords, _) = calculator(events, emptySet(), "2024-01-20", emptyList(), emptyMap())
        val usage = usageRecords.find{it.packageName == "app"}
        assertThat(usage).isNotNull()
        assertThat(usage?.activeTimeMillis).isAtMost(AppConstants.ACTIVE_TIME_SCROLL_WINDOW_MS)
    }

    @Test
    fun `invoke - active time - different event types`() = runTest {
        val scrollEvent = createRawEvent("app", RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1000)
        val typeEvent = createRawEvent("app", RawAppEvent.EVENT_TYPE_ACCESSIBILITY_TYPING, 2000)
        val clickEvent = createRawEvent("app", RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_CLICKED, 3000)
        val resumeEvent = createRawEvent("app", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 500)
        val pauseEvent = createRawEvent("app", RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, 5000)

        val (usageRecords, _) = calculator(listOf(resumeEvent, scrollEvent, typeEvent, clickEvent, pauseEvent), emptySet(), "2024-01-20", emptyList(), emptyMap())
        val usage = usageRecords.find{it.packageName == "app"}

        // scroll [1000, 4000], type [2000, 7000], click [3000, 8000] -> merged [1000, 8000]
        // session [500, 5000]. Intersection is [1000, 5000] -> duration 4000
        val expectedTime = 4000L
        assertThat(usage?.activeTimeMillis).isEqualTo(expectedTime)
        @Test
        fun `aggregateUsage - correctly closes previous app session on app switch`() = runTest {
            // Arrange: Simulate a user switching from GitHub to Chrome
            val testEvents = listOf(
                // 1. User opens GitHub at time 1000
                createRawEvent(
                    "com.github.android",
                    RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED,
                    1000L
                ),
                // 2. 10 seconds later, user switches directly to Chrome
                createRawEvent(
                    "com.android.chrome",
                    RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED,
                    11000L // 10,000ms later
                ),
                // 3. 5 seconds later, user pauses Chrome (e.g., goes home)
                createRawEvent(
                    "com.android.chrome",
                    RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED,
                    16000L // 5,000ms later
                )
            )
            val periodEndDate = 20000L
    
            // Act
            val (usageAggregates, _) = calculator.aggregateUsage(testEvents, periodEndDate)
            val githubUsage = usageAggregates["com.github.android"]?.first
            val chromeUsage = usageAggregates["com.android.chrome"]?.first
    
            // Assert: Check for precise, correct values
            // GitHub should have run for exactly 10 seconds (11000 - 1000)
            assertThat(githubUsage).isEqualTo(10000L)
    
            // Chrome should have run for exactly 5 seconds (16000 - 11000)
            assertThat(chromeUsage).isEqualTo(5000L)
        }
    }
    @Test
    fun `invoke - insignificant usage with no notifications - filters out record`() = runTest {
        val events = listOf(
            createRawEvent("app", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 1000),
            createRawEvent("app", RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, 1000 + AppConstants.MINIMUM_SIGNIFICANT_SESSION_DURATION_MS - 1)
        )
        val (usageRecords, _) = calculator(events, emptySet(), "2024-01-01", emptyList(), emptyMap())
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
        val (usageRecords, _) = calculator(events, emptySet(), date, emptyList(), emptyMap())
        val usage = usageRecords.find { it.packageName == "app" }
        assertThat(usage?.usageTimeMillis).isEqualTo(5000)
    }
}
