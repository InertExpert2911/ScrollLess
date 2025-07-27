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
    fun `invoke - filters events correctly before passing to calculators`() = runTest {
        val date = "2024-01-20"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val visibleApp = "com.app.visible"
        val hiddenApp = "com.app.hidden"
        val filterSet = setOf(hiddenApp)

        val events = listOf(
            createRawEvent(visibleApp, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + 1000),
            createRawEvent(hiddenApp, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + 2000),
            createRawEvent(visibleApp, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, startOfDay + 3000),
            createRawEvent(hiddenApp, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, startOfDay + 4000)
        )

        // Mock calculators to capture the event lists they receive
        val scrollEventsSlot = slot<List<RawAppEvent>>()
        val usageEventsSlot = slot<List<RawAppEvent>>()
        val insightEventsSlot = slot<List<RawAppEvent>>()

        coEvery { unlockCalculator.invoke(any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { scrollCalculator.invoke(capture(scrollEventsSlot), any()) } returns emptyList()
        coEvery { usageCalculator.invoke(capture(usageEventsSlot), any(), any(), any(), any()) } returns Pair(emptyList(), null)
        coEvery { insightGenerator.invoke(any(), any(), capture(insightEventsSlot), any()) } returns emptyList()

        processor.invoke(date, events, emptyList(), filterSet, emptyMap())

        // Verify that the scroll calculator received only the filtered events
        assertThat(scrollEventsSlot.captured.all { it.packageName !in filterSet }).isTrue()
        assertThat(scrollEventsSlot.captured.size).isEqualTo(2) // 2 visible events

        // Verify that the usage and insight calculators received the correct unlock-related events
        // The scroll-related events are not unlock-related, so they should be filtered out.
        assertThat(usageEventsSlot.captured.size).isEqualTo(2)
        assertThat(insightEventsSlot.captured.size).isEqualTo(2)
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
        coEvery { usageCalculator.invoke(any(), any(), any(), any(), any()) } returns Pair(mockUsageRecords, mockDeviceSummary)
        coEvery { insightGenerator.invoke(any(), any(), any(), any()) } returns mockInsights

        val result = processor.invoke(date, emptyList(), emptyList(), emptySet(), emptyMap())

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
        coEvery { usageCalculator.invoke(any(), any(), any(), any(), any()) } returns Pair(emptyList(), null)
        coEvery { insightGenerator.invoke(any(), any(), any(), any()) } returns emptyList()

        val result = processor.invoke(date, emptyList(), emptyList(), emptySet(), emptyMap())

        assertThat(result.unlockSessions).isEmpty()
        assertThat(result.scrollSessions).isEmpty()
        assertThat(result.usageRecords).isEmpty()
        assertThat(result.deviceSummary).isNull()
        assertThat(result.insights).isEmpty()
    }
}