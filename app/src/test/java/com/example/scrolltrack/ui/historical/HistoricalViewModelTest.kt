package com.example.scrolltrack.ui.historical

import app.cash.turbine.test
import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.db.DailyAppUsageRecord
import com.example.scrolltrack.ui.mappers.AppUiModelMapper
import com.example.scrolltrack.ui.model.AppUsageUiItem
import com.example.scrolltrack.util.DateUtil
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

@ExperimentalCoroutinesApi
class HistoricalViewModelTest {

    private lateinit var viewModel: HistoricalViewModel
    private val scrollDataRepository: ScrollDataRepository = mockk()
    private val appMetadataRepository: AppMetadataRepository = mockk()
    private val appUiModelMapper: AppUiModelMapper = mockk()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { scrollDataRepository.getAllDistinctUsageDateStrings() } returns flowOf(emptyList())
        viewModel = HistoricalViewModel(scrollDataRepository, appMetadataRepository, appUiModelMapper)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when viewmodel initializes, it selects today's date`() = runTest {
        val today = DateUtil.getCurrentLocalDateString()
        assertEquals(today, viewModel.selectedDateForHistory.value)
    }

    @Test
    fun `when date is selected, historical data is loaded`() = runTest {
        val date = "2023-01-01"
        val records = listOf(
            DailyAppUsageRecord(1, "app1", date, 1_200_000, 500, 1, 1, 0),
            DailyAppUsageRecord(2, "app2", date, 1_800_000, 1000, 2, 2, 0)
        )
        val uiItems = listOf(
            AppUsageUiItem("app1", "App One", null, 1_200_000, "app1"),
            AppUsageUiItem("app2", "App Two", null, 1_800_000, "app2")
        )

        coEvery { scrollDataRepository.getAppUsageForDate(date) } returns flowOf(records)
        coEvery { appUiModelMapper.mapToAppUsageUiItems(records) } returns uiItems

        viewModel.updateSelectedDateForHistory(DateUtil.getStartOfDayUtcMillis(date))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dailyAppUsageForSelectedDateHistory.test {
            // The flow will emit the initial empty list, then the new list.
            assertEquals(emptyList<AppUsageUiItem>(), awaitItem())
            assertEquals(uiItems.sortedByDescending { it.usageTimeMillis }, awaitItem())
        }
        viewModel.totalUsageTimeForSelectedDateHistoryFormatted.test {
            assertEquals("Loading...", awaitItem())
            assertEquals("50m", awaitItem())
        }
    }

    @Test
    fun `resetSelectedDateToToday updates the date to today`() = runTest {
        val yesterday = DateUtil.getPastDateString(1)
        viewModel.updateSelectedDateForHistory(DateUtil.getStartOfDayUtcMillis(yesterday))
        assertEquals(yesterday, viewModel.selectedDateForHistory.value)

        viewModel.resetSelectedDateToToday()
        val today = DateUtil.getCurrentLocalDateString()
        assertEquals(today, viewModel.selectedDateForHistory.value)
    }
} 