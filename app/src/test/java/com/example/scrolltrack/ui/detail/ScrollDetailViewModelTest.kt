package com.example.scrolltrack.ui.detail

import app.cash.turbine.test
import com.example.scrolltrack.data.AppScrollData
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.ui.mappers.AppUiModelMapper
import com.example.scrolltrack.ui.model.AppScrollUiItem
import com.example.scrolltrack.util.ConversionUtil
import com.example.scrolltrack.util.DateUtil
import io.mockk.coEvery
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

@ExperimentalCoroutinesApi
class ScrollDetailViewModelTest {

    private lateinit var viewModel: ScrollDetailViewModel
    private val scrollDataRepository: ScrollDataRepository = mockk()
    private val appUiModelMapper: AppUiModelMapper = mockk()
    private val conversionUtil: ConversionUtil = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { scrollDataRepository.getAllDistinctUsageDateStrings() } returns flowOf(emptyList())
        viewModel = ScrollDetailViewModel(scrollDataRepository, appUiModelMapper, conversionUtil)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when viewmodel initializes, it selects today's date`() = runTest {
        val today = DateUtil.getCurrentLocalDateString()
        assertEquals(today, viewModel.selectedDateForScrollDetail.value)
    }

    @Test
    fun `when date is selected, scroll data is loaded`() = runTest {
        val date = "2023-01-01"
        val scrollData = listOf(
            AppScrollData("app1", 100, 50, 50, "MEASURED"),
            AppScrollData("app2", 200, 100, 100, "INFERRED")
        )
        val uiItems = listOf(
            AppScrollUiItem("app1", "App One", null, 100, 50, 50, "app1", "MEASURED"),
            AppScrollUiItem("app2", "App Two", null, 200, 100, 100, "app2", "INFERRED")
        )

        coEvery { scrollDataRepository.getScrollDataForDate(date) } returns flowOf(scrollData)
        coEvery { appUiModelMapper.mapToAppScrollUiItem(scrollData[0]) } returns uiItems[0]
        coEvery { appUiModelMapper.mapToAppScrollUiItem(scrollData[1]) } returns uiItems[1]

        viewModel.updateSelectedDateForScrollDetail(DateUtil.getStartOfDayUtcMillis(date))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.aggregatedScrollDataForSelectedDate.test {
            // Skip initial empty state
            assertEquals(emptyList<AppScrollUiItem>(), awaitItem())
            val finalState = awaitItem()
            assertEquals(uiItems.sortedByDescending { it.totalScroll }, finalState)
        }
    }
} 