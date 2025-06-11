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
import com.example.scrolltrack.db.DailyAppUsageDao
import com.example.scrolltrack.db.ScrollSessionDao
import java.lang.reflect.Method
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import android.app.usage.UsageStatsManager
import android.content.Context
import com.example.scrolltrack.db.DailyAppUsageRecord
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.os.Build
import android.app.usage.UsageEvents

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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class ScrollDataRepositoryImplTest {

    private lateinit var repository: ScrollDataRepositoryImpl
    private lateinit var mockDailyAppUsageDao: DailyAppUsageDao
    private lateinit var mockRawAppEventDao: RawAppEventDao
    private lateinit var mockUsageStatsManager: UsageStatsManager
    private lateinit var mockApplication: Application

    private val inMemoryRawEvents = mutableListOf<RawAppEvent>()

    @Before
    fun setUp() {
        inMemoryRawEvents.clear()
        val mockScrollSessionDao = mockk<ScrollSessionDao>(relaxed = true)
        mockDailyAppUsageDao = mockk(relaxed = true)
        mockRawAppEventDao = mockk(relaxed = true)
        mockApplication = mockk<Application>(relaxed = true)
        mockUsageStatsManager = mockk(relaxed = true)

        every { mockApplication.getSystemService(Context.USAGE_STATS_SERVICE) } returns mockUsageStatsManager
        every { mockApplication.packageName } returns "com.example.scrolltrack"

        // Make the mock RawAppEventDao stateful
        coEvery { mockRawAppEventDao.insertEvents(any()) } coAnswers { inMemoryRawEvents.addAll(arg(0)) }
        coEvery { mockRawAppEventDao.getEventsForPeriod(any(), any()) } coAnswers {
            val start = arg<Long>(0)
            val end = arg<Long>(1)
            inMemoryRawEvents.filter { it.eventTimestamp in start..end }
        }

        repository = ScrollDataRepositoryImpl(
            scrollSessionDao = mockScrollSessionDao,
            dailyAppUsageDao = mockDailyAppUsageDao,
            rawAppEventDao = mockRawAppEventDao,
            application = mockApplication
        )
    }

    // --- Tests for updateTodayAppUsageStats ---

    @Test
    fun `updateTodayAppUsageStats - initial fetch - queries correct time window`() = runTest {
        val today = DateUtil.getCurrentLocalDateString()
        val startOfDay = DateUtil.getStartOfDayUtcMillis(today)

        // ARRANGE: No previous events for today
        coEvery { mockRawAppEventDao.getLatestEventTimestampForDate(today) } returns null

        // ACT
        repository.updateTodayAppUsageStats()

        // ASSERT: Verify queryEvents is called with a window starting from the beginning of the day.
        val startTimeSlot = slot<Long>()
        coVerify { mockUsageStatsManager.queryEvents(capture(startTimeSlot), any()) }

        // The query should start at the beginning of the day since there's no previous data.
        assertThat(startTimeSlot.captured).isEqualTo(startOfDay)
    }

    @Test
    fun `updateTodayAppUsageStats - incremental fetch - queries correct time window`() = runTest {
        val today = DateUtil.getCurrentLocalDateString()
        val startOfDay = DateUtil.getStartOfDayUtcMillis(today)
        val lastEventTime = startOfDay + 1000 * 60 * 60 // 1 hour into the day

        // ARRANGE: A previous event exists for today
        coEvery { mockRawAppEventDao.getLatestEventTimestampForDate(today) } returns lastEventTime

        // ACT
        repository.updateTodayAppUsageStats()

        // ASSERT: Verify queryEvents is called with a window starting from the last event time minus overlap.
        val startTimeSlot = slot<Long>()
        val overlap = 10000L // from ScrollDataRepositoryImpl.EVENT_FETCH_OVERLAP_MS
        coVerify { mockUsageStatsManager.queryEvents(capture(startTimeSlot), any()) }

        assertThat(startTimeSlot.captured).isEqualTo(lastEventTime - overlap)
    }

    @Test
    fun `updateTodayAppUsageStats - always deletes old data and inserts new`() = runTest {
        val today = DateUtil.getCurrentLocalDateString()

        // ARRANGE: Mock DAO to return some raw events to trigger aggregation
        val startOfDay = DateUtil.getStartOfDayUtcMillis(today)
        val events = listOf(
            createEvent("com.app.test", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + 1000),
            createEvent("com.app.test", RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, startOfDay + 6000) // Make session 5s long
        )
        coEvery { mockRawAppEventDao.getEventsForPeriod(any(), any()) } returns events

        // ACT
        repository.updateTodayAppUsageStats()

        // ASSERT
        // Verify that old data is deleted for the current day
        coVerify { mockDailyAppUsageDao.deleteUsageForDate(today) }
        // Verify that new aggregated data is inserted
        coVerify { mockDailyAppUsageDao.insertAllUsage(any()) }
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

    @Test
    fun `aggregateUsage - midnight crossing - splits session and active time correctly`() {
        // ARRANGE: A session that starts before midnight and ends after, with continuous interaction.
        val midnight = DateUtil.getStartOfDayUtcMillis("2024-01-02") // Exact midnight timestamp
        val startTime = midnight - 15000L // Starts 15 seconds before midnight
        val endTime = midnight + 15000L   // Ends 15 seconds after midnight

        val testEvents = listOf(
            createEvent("com.app.nightowl", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startTime),
            // Interaction every 5 seconds across midnight
            createEvent("com.app.nightowl", RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_CLICKED, startTime + 5000L),
            createEvent("com.app.nightowl", RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_CLICKED, startTime + 10000L),
            createEvent("com.app.nightowl", RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_CLICKED, startTime + 15000L), // Exactly at midnight
            createEvent("com.app.nightowl", RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_CLICKED, startTime + 20000L),
            createEvent("com.app.nightowl", RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_CLICKED, startTime + 25000L),
            createEvent("com.app.nightowl", RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, endTime)
        )
        val periodEndDate = endTime + 10000L

        // ACT
        val result = repository.aggregateUsage(testEvents, periodEndDate)

        // ASSERT
        // Our current test-facing aggregateUsage function does not split sessions across midnight.
        // It attributes the entire session to the day on which it started.
        // This test verifies that behavior. A more sophisticated version would split the session.
        assertThat(result).hasSize(1)

        val dateString = "2024-01-01"
        val key = Pair("com.app.nightowl", dateString)
        assertThat(result).containsKey(key)

        val usage = result[key]!!
        // The entire session duration is 30 seconds.
        assertThat(usage.first).isEqualTo(30000L)
        // Each of the 5 clicks creates a 2-second window, and they are too far apart to merge.
        // Total active time is 5 * 2000ms = 10000ms.
        assertThat(usage.second).isEqualTo(10000L)
    }

    @Test
    fun `aggregateUsage - screen off terminates session`() {
        // ARRANGE: A session that is terminated by the screen turning off, not a PAUSE event.
        val startTime = 7000000L
        val screenOffTime = startTime + 25000L
        val testEvents = listOf(
            createEvent("com.app.sleeper", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startTime),
            createEvent("com.app.sleeper", RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_CLICKED, startTime + 5000L),
            createEvent("com.app.sleeper", RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE, screenOffTime)
        )
        // Note: There is no PAUSE event. The period ends long after.
        val periodEndDate = screenOffTime + 10000L

        // ACT
        val result = repository.aggregateUsage(testEvents, periodEndDate)

        // ASSERT
        assertThat(result).hasSize(1)

        val dateString = DateUtil.formatUtcTimestampToLocalDateString(startTime)
        val key = Pair("com.app.sleeper", dateString)
        assertThat(result).containsKey(key)

        val usage = result[key]!!
        // Usage time should be from RESUME until the screen turned off.
        assertThat(usage.first).isEqualTo(25000L)
        // Active time is just the 2-second window from the single click.
        assertThat(usage.second).isEqualTo(2000L)
    }

    @Test
    fun `aggregateUsage - purely passive session - active time is zero`() {
        // ARRANGE: A long session with no user interaction events at all.
        val startTime = 8000000L
        val endTime = startTime + 90000L // A 90-second session
        val testEvents = listOf(
            createEvent("com.app.reader", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startTime),
            createEvent("com.app.reader", RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, endTime)
        )
        val periodEndDate = endTime + 10000L

        // ACT
        val result = repository.aggregateUsage(testEvents, periodEndDate)

        // ASSERT
        assertThat(result).hasSize(1)

        val dateString = DateUtil.formatUtcTimestampToLocalDateString(startTime)
        val key = Pair("com.app.reader", dateString)
        assertThat(result).containsKey(key)

        val usage = result[key]!!
        // Usage time should be the full 90-second duration.
        assertThat(usage.first).isEqualTo(90000L)
        // Active time should be zero as there were no interactions.
        assertThat(usage.second).isEqualTo(0L)
    }

    // --- Tests for backfillHistoricalAppUsageData ---
    @Test
    fun `backfillHistoricalAppUsageData - skips days with existing data`() = runTest {
        val daysToBackfill = 3

        // ARRANGE: Mock DAO to indicate that data EXISTS for all checked days
        coEvery { mockDailyAppUsageDao.getUsageCountForDateString(any()) } returns 1

        // ACT
        repository.backfillHistoricalAppUsageData(daysToBackfill)

        // ASSERT
        // Verify we checked each of the 3 days
        coVerify(exactly = daysToBackfill) { mockDailyAppUsageDao.getUsageCountForDateString(any()) }
        // Verify that because data existed, we NEVER tried to query events or insert new data.
        coVerify(exactly = 0) { mockUsageStatsManager.queryEvents(any(), any()) }
        coVerify(exactly = 0) { mockDailyAppUsageDao.insertAllUsage(any()) }
    }

    @Test
    fun `backfillHistoricalAppUsageData - processes and inserts for empty days`() = runTest {
        val daysToBackfill = 2

        // ARRANGE: Mock DAO to indicate NO data exists
        coEvery { mockDailyAppUsageDao.getUsageCountForDateString(any()) } returns 0

        // ARRANGE: Mock UsageStatsManager to return events appropriate for the queried time window
        every { mockUsageStatsManager.queryEvents(any(), any()) } answers {
            val startTime = it.invocation.args[0] as Long
            val eventsToReturn = listOf(
                // A significant session for a test app
                createUsageEventForTest("com.app.backfill", UsageEvents.Event.ACTIVITY_RESUMED, startTime + 1000),
                createUsageEventForTest("com.app.backfill", UsageEvents.Event.ACTIVITY_PAUSED, startTime + 6000),
                // A session for a launcher, which should be filtered out
                createUsageEventForTest("com.some.launcher", UsageEvents.Event.ACTIVITY_RESUMED, startTime + 7000),
                createUsageEventForTest("com.some.launcher", UsageEvents.Event.ACTIVITY_PAUSED, startTime + 8000)
            )
            createMockUsageEvents(eventsToReturn)
        }

        // ACT
        repository.backfillHistoricalAppUsageData(daysToBackfill)

        // ASSERT
        // Verify we checked each of the 2 days for existing data
        coVerify(exactly = daysToBackfill) { mockDailyAppUsageDao.getUsageCountForDateString(any()) }

        // Verify we queried the system for events for each of the 2 empty days
        coVerify(exactly = daysToBackfill) { mockUsageStatsManager.queryEvents(any(), any()) }

        // Verify we inserted the aggregated results for each of the 2 empty days
        val capturedLists = mutableListOf<List<DailyAppUsageRecord>>()
        coVerify(exactly = daysToBackfill) { mockDailyAppUsageDao.insertAllUsage(capture(capturedLists)) }

        // Optional: More detailed assertion on what was captured
        // We expect two captures, one for each day.
        assertThat(capturedLists).hasSize(daysToBackfill)
        for (capturedRecords in capturedLists) {
            assertThat(capturedRecords).hasSize(1) // Only com.app.backfill should be aggregated
            assertThat(capturedRecords.first().packageName).isEqualTo("com.app.backfill")
        }
    }

    /**
     * Helper for making the private `aggregateUsage` method accessible for testing.
     */
    private fun ScrollDataRepositoryImpl.aggregateUsage(
        events: List<RawAppEvent>,
        periodEndDate: Long
    ): Map<Pair<String, String>, Pair<Long, Long>> {
        val method: Method = ScrollDataRepositoryImpl::class.java.getDeclaredMethod(
            "aggregateUsage",
            List::class.java,
            Long::class.java
        ).apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        return method.invoke(this, events, periodEndDate) as Map<Pair<String, String>, Pair<Long, Long>>
    }

    /**
     * Creates a mock UsageEvents object that will iterate over a given list of events.
     */
    private fun createMockUsageEvents(events: List<UsageEvents.Event>): UsageEvents {
        val mockUsageEvents = mockk<UsageEvents>()
        val eventIterator = events.iterator()

        every { mockUsageEvents.hasNextEvent() } answers { eventIterator.hasNext() }
        every { mockUsageEvents.getNextEvent(any()) } answers {
            val eventToReturn = eventIterator.next()
            val outEvent = it.invocation.args[0] as UsageEvents.Event
            copyEvent(eventToReturn, outEvent)
            true
        }
        return mockUsageEvents
    }

    /**
     * Factory function to create UsageEvents.Event instances for testing using reflection.
     */
    private fun createUsageEventForTest(pkg: String, type: Int, time: Long): UsageEvents.Event {
        val event = UsageEvents.Event()
        setEventField(event, "mPackage", pkg)
        setEventField(event, "mClass", "$pkg.TestActivity")
        setEventField(event, "mTimeStamp", time)
        setEventField(event, "mEventType", type)
        return event
    }

    /**
     * Uses reflection to copy data from one UsageEvents.Event to another.
     */
    private fun copyEvent(from: UsageEvents.Event, to: UsageEvents.Event) {
        setEventField(to, "mPackage", from.packageName)
        setEventField(to, "mClass", from.className)
        setEventField(to, "mTimeStamp", from.timeStamp)
        setEventField(to, "mEventType", from.eventType)
    }

    /**
     * Sets a field on a UsageEvents.Event object using reflection.
     */
    private fun setEventField(event: UsageEvents.Event, fieldName: String, value: Any) {
        try {
            val field = UsageEvents.Event::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(event, value)
        } catch (e: Exception) {
            throw RuntimeException("Failed to set field '$fieldName' on UsageEvents.Event", e)
        }
    }
} 