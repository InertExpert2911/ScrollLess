package com.example.scrolltrack.ui.detail

import android.content.Context
import app.cash.turbine.test
import com.example.scrolltrack.data.AppScrollData
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.ui.mappers.AppUiModelMapper
import com.example.scrolltrack.ui.model.AppScrollUiItem
import com.example.scrolltrack.util.ConversionUtil
import com.example.scrolltrack.util.DateUtil
import com.google.common.truth.Truth.assertThat
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
import org.junit.Before
import org.junit.Test
import java.time.ZoneOffset
import kotlinx.coroutines.test.advanceUntilIdle

@ExperimentalCoroutinesApi
class ScrollDetailViewModelTest {

    private lateinit var viewModel: ScrollDetailViewModel
    private val scrollDataRepository: ScrollDataRepository = mockk(relaxed = true)
    private val appUiModelMapper: AppUiModelMapper = mockk(relaxed = true)
    private val conversionUtil: ConversionUtil = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { scrollDataRepository.getAllDistinctUsageDateStrings() } returns flowOf(emptyList())
        // Provide a default return for getScrollDataForDate to avoid errors in init
        every { scrollDataRepository.getScrollDataForDate(any()) } returns flowOf(emptyList())
        viewModel = ScrollDetailViewModel(scrollDataRepository, appUiModelMapper, conversionUtil)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when viewmodel initializes, it selects today's date`() = runTest {
        val today = DateUtil.getCurrentLocalDateString()
        assertThat(viewModel.selectedDateForScrollDetail.value).isEqualTo(today)
    }

    @Test
    fun `when date is selected, scroll data is loaded and sorted`() = runTest {
        val date = "2023-01-01"
        val scrollData = listOf(
            AppScrollData("app2", 200, 100, 100, "INFERRED"),
            AppScrollData("app1", 100, 50, 50, "MEASURED")
        )
        val uiItems = listOf(
            AppScrollUiItem("app2", "App Two", null, 200, 100, 100, "app2", "INFERRED"),
            AppScrollUiItem("app1", "App One", null, 100, 50, 50, "app1", "MEASURED")
        )

        every { scrollDataRepository.getScrollDataForDate(date) } returns flowOf(scrollData)
        coEvery { appUiModelMapper.mapToAppScrollUiItem(scrollData[0]) } returns uiItems[0] // app2
        coEvery { appUiModelMapper.mapToAppScrollUiItem(scrollData[1]) } returns uiItems[1] // app1

        viewModel.aggregatedScrollDataForSelectedDate.test {
            // Initial empty list
            assertThat(awaitItem()).isEmpty()

            viewModel.updateSelectedDateForScrollDetail(DateUtil.getStartOfDayUtcMillis(date))
            advanceUntilIdle()

            val loadedItems = awaitItem()
            assertThat(loadedItems).hasSize(2)
            // The flow in the viewmodel sorts the result, so we expect uiItems to be emitted in that order.
            assertThat(loadedItems).isEqualTo(uiItems)
            cancelAndIgnoreRemainingEvents()
        }
    }


    @Test
    fun `data is fetched and mapped correctly on date change`() = runTest {
        val date = "2024-01-20"
        val scrollData = listOf(
            AppScrollData("com.app.one", 1000, 400, 600, "MEASURED"),
            AppScrollData("com.app.two", 200, 0, 200, "INFERRED")
        )
        val mappedItems = listOf(
             AppScrollUiItem("com.app.one", "AppName", null, 1000, 400, 600, "com.app.one", "MEASURED"),
             AppScrollUiItem("com.app.two", "AppName", null, 200, 0, 200, "com.app.two", "INFERRED")
        )


        every { scrollDataRepository.getScrollDataForDate(date) } returns flowOf(scrollData)
        coEvery { appUiModelMapper.mapToAppScrollUiItem(scrollData[0]) } returns mappedItems[0]
        coEvery { appUiModelMapper.mapToAppScrollUiItem(scrollData[1]) } returns mappedItems[1]

        val newDateMillis = DateUtil.parseLocalDate(date)!!.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        viewModel.aggregatedScrollDataForSelectedDate.test {
            // The initial state from setUp is an empty list
            assertThat(awaitItem()).isEmpty()

            viewModel.updateSelectedDateForScrollDetail(newDateMillis)
            advanceUntilIdle()

            val loadedState = awaitItem()
            assertThat(loadedState).hasSize(2)
            assertThat(loadedState).isEqualTo(mappedItems)
            cancelAndConsumeRemainingEvents()
        }

        // Also verify the date string was updated
        assertThat(viewModel.selectedDateForScrollDetail.value).isEqualTo(date)
    }
} 