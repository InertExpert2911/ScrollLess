package com.example.scrolltrack.data

import android.app.Application
import com.example.scrolltrack.db.RawAppEvent
import com.example.scrolltrack.db.RawAppEventDao
import com.example.scrolltrack.util.DateUtil
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import com.example.scrolltrack.db.DailyAppUsageDao
import com.example.scrolltrack.db.ScrollSessionDao
import java.lang.reflect.Method

// Helper function to create a simple RawAppEvent
private fun createEvent(pkg: String, type: Int, time: Long): RawAppEvent {
    return RawAppEvent(
        packageName = pkg,
        className = "$pkg.MainActivity",
        eventType = type,
        eventTimestamp = time,
        eventDateString = DateUtil.formatUtcTimestampToLocalDateString(time)
    )
}

@RunWith(JUnit4::class)
class ScrollDataRepositoryImplTest {

    private lateinit var repository: ScrollDataRepositoryImpl

    @Before
    fun setUp() {
        val mockScrollSessionDao = mockk<ScrollSessionDao>(relaxed = true)
        val mockDailyAppUsageDao = mockk<DailyAppUsageDao>(relaxed = true)
        val mockRawAppEventDao = mockk<RawAppEventDao>(relaxed = true)
        val mockApplication = mockk<Application>(relaxed = true)

        repository = ScrollDataRepositoryImpl(
            scrollSessionDao = mockScrollSessionDao,
            dailyAppUsageDao = mockDailyAppUsageDao,
            rawAppEventDao = mockRawAppEventDao,
            application = mockApplication
        )
    }

    @Test
    fun `aggregateUsage - passive reading scenario - high usage time, low active time`() {
        // ARRANGE: A 60-second session with one click at the beginning.
        val startTime = 1000000L
        val testEvents = listOf(
            createEvent("com.example.news", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startTime),
            createEvent("com.example.news", RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_CLICKED, startTime + 1000L), // Interaction at 1s
            createEvent("com.example.news", RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, startTime + 60000L) // Session ends at 60s
        )
        val periodEndDate = startTime + 70000L // End of test period

        // ACT: Run the aggregation directly
        val result = repository.aggregateUsage(testEvents, periodEndDate)

        // ASSERT: Verify the results
        val dateString = DateUtil.formatUtcTimestampToLocalDateString(startTime)
        val key = Pair("com.example.news", dateString)

        assertThat(result).containsKey(key)

        val usage = result[key]
        assertThat(usage).isNotNull()

        val usageTime = usage!!.first
        val activeTime = usage.second

        // Note: The session is from RESUME to PAUSE, so its duration is (startTime + 60000) - startTime = 60000ms
        assertThat(usageTime).isEqualTo(60000L)
        // Active time is only the 2-second window from the single click
        assertThat(activeTime).isEqualTo(2000L)
    }

    @Test
    fun `aggregateUsage - fully active scenario - usage and active time are equal`() {
        // ARRANGE: A 30-second session with constant interaction.
        val startTime = 2000000L
        val interactions = (1..20).map { i ->
            // Create an interaction event every 1.5 seconds (1500ms)
            createEvent("com.example.game", RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_CLICKED, startTime + (i * 1500L))
        }
        val testEvents = mutableListOf(
            createEvent("com.example.game", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startTime)
        )
        testEvents.addAll(interactions)
        testEvents.add(createEvent("com.example.game", RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, startTime + 31000L)) // Session ends at 31s

        val periodEndDate = startTime + 40000L

        // ACT
        val result = repository.aggregateUsage(testEvents, periodEndDate)

        // ASSERT
        val dateString = DateUtil.formatUtcTimestampToLocalDateString(startTime)
        val key = Pair("com.example.game", dateString)

        assertThat(result).containsKey(key)

        val usage = result[key]!!
        val usageTime = usage.first
        val activeTime = usage.second

        // Total session time is 31 seconds
        assertThat(usageTime).isEqualTo(31000L)

        // The merged active time window runs from 1500ms to 32000ms. With the fix, this is
        // clipped by the session end at 31000ms. The new interval is [1500, 31000].
        // Duration = 29500ms.
        assertThat(activeTime).isEqualTo(29500L)
    }

    @Test
    fun `aggregateUsage - multi-app switching - ends previous session correctly`() {
        // ARRANGE: User is in App A, then switches directly to App B.
        val startTime = 3000000L
        val testEvents = listOf(
            createEvent("com.app.a", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startTime),
            // User stays in App A for 20 seconds
            createEvent("com.app.b", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startTime + 20000L),
            // User stays in App B for 15 seconds
            createEvent("com.app.b", RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, startTime + 35000L)
        )
        val periodEndDate = startTime + 40000L

        // ACT
        val result = repository.aggregateUsage(testEvents, periodEndDate)

        // ASSERT
        val dateString = DateUtil.formatUtcTimestampToLocalDateString(startTime)
        val keyA = Pair("com.app.a", dateString)
        val keyB = Pair("com.app.b", dateString)

        assertThat(result).hasSize(2)
        assertThat(result).containsKey(keyA)
        assertThat(result).containsKey(keyB)

        // App A's session should be from startTime to just before App B starts.
        val usageA = result[keyA]!!
        assertThat(usageA.first).isEqualTo(19999L) // 20000 - 1ms
        assertThat(usageA.second).isEqualTo(0L) // No interaction

        // App B's session is from its resume to its pause.
        val usageB = result[keyB]!!
        assertThat(usageB.first).isEqualTo(15000L) // 35000 - 20000
        assertThat(usageB.second).isEqualTo(0L) // No interaction
    }

    @Test
    fun `aggregateUsage - passive quick switch - session is ignored`() {
        // ARRANGE: A session that is too short and has no interaction.
        val startTime = 4000000L
        val testEvents = listOf(
            createEvent("com.app.ignored", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startTime),
            createEvent("com.app.ignored", RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, startTime + 1000L) // Only 1s long
        )
        val periodEndDate = startTime + 10000L

        // ACT
        val result = repository.aggregateUsage(testEvents, periodEndDate)

        // ASSERT
        assertThat(result).isEmpty()
    }

    @Test
    fun `aggregateUsage - active quick switch - session is ignored`() {
        // ARRANGE: A session that is too short, even though it has interaction.
        val startTime = 5000000L
        val testEvents = listOf(
            createEvent("com.app.ignored.active", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startTime),
            createEvent("com.app.ignored.active", RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_CLICKED, startTime + 500L),
            createEvent("com.app.ignored.active", RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, startTime + 1000L) // Only 1s long
        )
        val periodEndDate = startTime + 10000L

        // ACT
        val result = repository.aggregateUsage(testEvents, periodEndDate)

        // ASSERT
        // Our current logic checks for significant duration *before* checking for activity.
        // Therefore, this session should also be ignored.
        assertThat(result).isEmpty()
    }

    @Test
    fun `aggregateUsage - active time is correctly capped by session end`() {
        // ARRANGE: A session where an interaction happens right before the session ends.
        // The 2-second active time window for this interaction should be cut off by the PAUSE event.
        val startTime = 6000000L
        val testEvents = listOf(
            createEvent("com.app.capping", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startTime),
            // Interaction happens 500ms before the session ends.
            createEvent("com.app.capping", RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_CLICKED, startTime + 9500L),
            // Session is exactly 10 seconds long.
            createEvent("com.app.capping", RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, startTime + 10000L)
        )
        val periodEndDate = startTime + 20000L

        // ACT
        val result = repository.aggregateUsage(testEvents, periodEndDate)

        // ASSERT
        val dateString = DateUtil.formatUtcTimestampToLocalDateString(startTime)
        val key = Pair("com.app.capping", dateString)

        assertThat(result).containsKey(key)
        val usage = result[key]!!
        val usageTime = usage.first
        val activeTime = usage.second

        // The usage time is exactly 10 seconds.
        assertThat(usageTime).isEqualTo(10000L)

        // The interaction at 9500ms creates a window [9500ms, 11500ms].
        // This should be capped by the session end at 10000ms.
        // The active portion is from 9500ms to 10000ms, which is 500ms.
        assertThat(activeTime).isEqualTo(500L)
    }
} 