package com.example.scrolltrack.data.processors

import com.example.scrolltrack.db.RawAppEvent
import com.example.scrolltrack.util.DateUtil
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@ExperimentalCoroutinesApi
class DailyDataProcessorTest {

    private val unlockCalculator: UnlockSessionCalculator = mockk()
    private val scrollCalculator: ScrollSessionCalculator = mockk()
    private val usageCalculator: AppUsageCalculator = mockk()
    private val insightGenerator: InsightGenerator = mockk()

    private val processor = DailyDataProcessor(
        unlockCalculator,
        scrollCalculator,
        usageCalculator,
        insightGenerator
    )

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
    fun `invoke - passes full event list and filtersets to calculators`() = runTest {
        val date = "2024-01-20"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val visibleApp = "com.app.visible"
        val hiddenApp = "com.app.hidden"
        val filterSet = setOf(hiddenApp)

        val events = listOf(
            createRawEvent(visibleApp, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + 1000),
            createRawEvent(hiddenApp, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + 2000),
            createRawEvent(visibleApp, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, startOfDay + 3000),
            createRawEvent(hiddenApp, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, startOfDay + 4000),
            createRawEvent(visibleApp, RawAppEvent.EVENT_TYPE_USER_UNLOCKED, startOfDay + 5000)
        )

        // Mock calculators to capture the arguments they receive
        val unlockEventsSlot = slot<List<RawAppEvent>>()
        val scrollEventsSlot = slot<List<RawAppEvent>>()
        val usageEventsSlot = slot<List<RawAppEvent>>()
        val insightEventsSlot = slot<List<RawAppEvent>>()
        val scrollFilterSetSlot = slot<Set<String>>()
        val usageFilterSetSlot = slot<Set<String>>()

        coEvery { unlockCalculator.invoke(capture(unlockEventsSlot), any(), any(), any(), any()) } returns emptyList()
        coEvery { scrollCalculator.invoke(capture(scrollEventsSlot), capture(scrollFilterSetSlot)) } returns emptyList()
        coEvery { usageCalculator.invoke(capture(usageEventsSlot), capture(usageFilterSetSlot), any(), any(), any(), any()) } returns Pair(emptyList(), null)
        coEvery { insightGenerator.invoke(any(), any(), capture(insightEventsSlot), any()) } returns emptyList()

        processor.invoke(date, events, emptyList(), filterSet, emptyMap(), null)

        // Verify that the unlock calculator received only unlock-related events
        val unlockRelatedTypes = setOf(
            RawAppEvent.EVENT_TYPE_USER_UNLOCKED,
            RawAppEvent.EVENT_TYPE_KEYGUARD_HIDDEN,
            RawAppEvent.EVENT_TYPE_KEYGUARD_SHOWN,
            RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE,
            RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED,
            RawAppEvent.EVENT_TYPE_SERVICE_STARTED,
            RawAppEvent.EVENT_TYPE_SERVICE_STOPPED
        )
        assertThat(unlockEventsSlot.captured.all { it.eventType in unlockRelatedTypes }).isTrue()
        assertThat(unlockEventsSlot.captured.size).isEqualTo(3) // 2 resumed, 1 unlocked

        // Verify that the other calculators received the *full, unfiltered* event list
        assertThat(scrollEventsSlot.captured).isEqualTo(events)
        assertThat(usageEventsSlot.captured).isEqualTo(events)
        assertThat(insightEventsSlot.captured).isEqualTo(events)

        // Verify that the filter set was passed correctly
        assertThat(scrollFilterSetSlot.captured).isEqualTo(filterSet)
        assertThat(usageFilterSetSlot.captured).isEqualTo(filterSet)
    }

    @Test
    fun `invoke - assembles results from all calculators`() = runTest {
        val date = "2024-01-20"
        val mockUnlockSessions = listOf(mockk<com.example.scrolltrack.db.UnlockSessionRecord>())
        val mockScrollSessions = listOf(mockk<com.example.scrolltrack.db.ScrollSessionRecord>())
        val mockUsageRecords = listOf(mockk<com.example.scrolltrack.db.DailyAppUsageRecord>())
        val mockDeviceSummary = mockk<com.example.scrolltrack.db.DailyDeviceSummary>()
        val mockInsights = listOf(mockk<com.example.scrolltrack.db.DailyInsight>())

        coEvery { unlockCalculator.invoke(any(), any(), any(), any(), any()) } returns mockUnlockSessions
        coEvery { scrollCalculator.invoke(any(), any()) } returns mockScrollSessions
        coEvery { usageCalculator.invoke(any(), any(), any(), any(), any(), any()) } returns Pair(mockUsageRecords, mockDeviceSummary)
        coEvery { insightGenerator.invoke(any(), any(), any(), any()) } returns mockInsights

        val result = processor.invoke(date, emptyList(), emptyList(), emptySet(), emptyMap(), null)

        assertThat(result.unlockSessions).isEqualTo(mockUnlockSessions)
        assertThat(result.scrollSessions).isEqualTo(mockScrollSessions)
        assertThat(result.usageRecords).isEqualTo(mockUsageRecords)
        assertThat(result.deviceSummary).isEqualTo(mockDeviceSummary)
        assertThat(result.insights).isEqualTo(mockInsights)
    }
    @Test
    fun `invoke - handles empty results from calculators`() = runTest {
        val date = "2024-01-20"
        coEvery { unlockCalculator.invoke(any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { scrollCalculator.invoke(any(), any()) } returns emptyList()
        coEvery { usageCalculator.invoke(any(), any(), any(), any(), any(), any()) } returns Pair(emptyList(), null)
        coEvery { insightGenerator.invoke(any(), any(), any(), any()) } returns emptyList()

        val result = processor.invoke(date, emptyList(), emptyList(), emptySet(), emptyMap(), null)

        assertThat(result.unlockSessions).isEmpty()
        assertThat(result.scrollSessions).isEmpty()
        assertThat(result.usageRecords).isEmpty()
        assertThat(result.deviceSummary).isNull()
        assertThat(result.insights).isEmpty()
    }
}
