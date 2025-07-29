package com.example.scrolltrack.ui.phoneusage

import app.cash.turbine.test
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.db.DailyAppUsageRecord
import com.example.scrolltrack.ui.mappers.AppUiModelMapper
import com.example.scrolltrack.ui.model.AppUsageUiItem
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.TimeZone

@ExperimentalCoroutinesApi
class PhoneUsageViewModelTest {

    private lateinit var viewModel: PhoneUsageViewModel
    private val repository: ScrollDataRepository = mockk(relaxed = true)
    private val mapper: AppUiModelMapper = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private val heatmapDataFlow = MutableStateFlow<Map<LocalDate, Int>>(emptyMap())
    private val dailyUsageFlow = MutableStateFlow<List<DailyAppUsageRecord>>(emptyList())

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Dispatchers.setMain(testDispatcher)
        every { repository.getTotalUsageTimePerDay() } returns heatmapDataFlow
        every { repository.getAppUsageForDate(any()) } returns dailyUsageFlow
        every { repository.getAppUsageForDateRange(any(), any()) } returns dailyUsageFlow
        viewModel = PhoneUsageViewModel(repository, mapper)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `heatmap data is loaded and triggers daily usage update`() = runTest {
        val data = mapOf(LocalDate.now() to 100)
        
        viewModel.uiState.test {
            awaitItem() // Initial state

            heatmapDataFlow.value = data
            
            val finalState = awaitItem()
            assertThat(finalState.heatmapData).isEqualTo(data)
            assertThat(finalState.monthsWithData).isNotEmpty()
        }
    }

    @Test
    fun `period changes calculate usage correctly`() = runTest {
        val date = LocalDate.of(2023, 10, 26) // A Thursday in Week 43, October has 31 days
        val dailyRecords = listOf(DailyAppUsageRecord(packageName = "com.app", dateString = "2023-10-26", usageTimeMillis = 3000, appOpenCount = 1))
        val weeklyRecords = listOf(DailyAppUsageRecord(packageName = "com.app", dateString = "2023-10-23", usageTimeMillis = 70000, appOpenCount = 1))
        val monthlyRecords = listOf(DailyAppUsageRecord(packageName = "com.app", dateString = "2023-10-01", usageTimeMillis = 310000, appOpenCount = 1))

        coEvery { mapper.mapToAppUsageUiItems(any(), any()) } answers {
            val records = firstArg<List<DailyAppUsageRecord>>()
            val days = secondArg<Int>()
            records.map { AppUsageUiItem(it.packageName, "Mapped", null, it.usageTimeMillis / days, it.packageName) }
        }
        coEvery { mapper.mapToAppUsageUiItems(any()) } answers {
            val records = firstArg<List<DailyAppUsageRecord>>()
            records.map { AppUsageUiItem(it.packageName, "Mapped", null, it.usageTimeMillis, it.packageName) }
        }

        viewModel.uiState.test {
            awaitItem() // Initial state

            // Test Daily
            dailyUsageFlow.value = dailyRecords
            viewModel.onDateSelected(date)
            viewModel.onPeriodChanged(PhoneUsagePeriod.Daily)
            val dailyState = awaitItem()
            assertThat(dailyState.usageStat).isEqualTo("< 1m") // 3000ms
            assertThat(dailyState.periodDisplay).isEqualTo("Oct 26, 2023")
            assertThat(dailyState.appUsage).hasSize(1)

            // Test Weekly
            dailyUsageFlow.value = weeklyRecords
            viewModel.onPeriodChanged(PhoneUsagePeriod.Weekly)
            val weeklyState = awaitItem()
            assertThat(weeklyState.usageStat).isEqualTo("< 1m") // 70000ms / 7 = 10000ms
            assertThat(weeklyState.periodDisplay).contains("Week 43")
            assertThat(weeklyState.appUsage).hasSize(1)

            // Test Monthly
            dailyUsageFlow.value = monthlyRecords
            viewModel.onPeriodChanged(PhoneUsagePeriod.Monthly)
            val monthlyState = awaitItem()
            assertThat(monthlyState.usageStat).isEqualTo("< 1m") // 310000ms / 31 = 10000ms
            assertThat(monthlyState.periodDisplay).isEqualTo("October 2023")
            assertThat(monthlyState.appUsage).hasSize(1)
        }
    }
}