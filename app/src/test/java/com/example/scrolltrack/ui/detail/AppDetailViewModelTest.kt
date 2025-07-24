package com.example.scrolltrack.ui.detail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.db.AppMetadata
import com.example.scrolltrack.db.AppScrollDataPerDate
import com.example.scrolltrack.db.DailyAppUsageRecord
import com.example.scrolltrack.util.ConversionUtil
import com.example.scrolltrack.util.DateUtil
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class AppDetailViewModelTest {

    private lateinit var viewModel: AppDetailViewModel
    private val scrollDataRepository: ScrollDataRepository = mockk(relaxed = true)
    private val appMetadataRepository: AppMetadataRepository = mockk(relaxed = true)
    private val conversionUtil: ConversionUtil = mockk(relaxed = true)
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(mapOf("packageName" to "app1"))
    private val context: Context = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val packageName = "app1"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { appMetadataRepository.getAppMetadata(packageName) } returns AppMetadata(packageName, "App One", "1.0", 1, false, true, true, 0, true, null, 0, 0)
        coEvery { appMetadataRepository.getIconFile(packageName) } returns null
        coEvery { conversionUtil.formatScrollDistance(any(), any()) } returns ("0" to "m")

        coEvery { scrollDataRepository.getUsageForPackageAndDates(any(), any()) } returns emptyList()
        coEvery { scrollDataRepository.getAggregatedScrollForPackageAndDates(any(), any()) } returns emptyList()

        viewModel = AppDetailViewModel(scrollDataRepository, appMetadataRepository, conversionUtil, savedStateHandle, context, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when viewmodel initializes, it loads app info and daily data`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { scrollDataRepository.getUsageForPackageAndDates(eq(packageName), any()) }
        coVerify { scrollDataRepository.getAggregatedScrollForPackageAndDates(eq(packageName), any()) }

        viewModel.appDetailAppName.test {
            assertThat(awaitItem()).isEqualTo("App One")
            cancelAndConsumeRemainingEvents()
        }
    }


    @Test
    fun `when chart period changes, data is reloaded`() = runTest {
        viewModel.changeChartPeriod(ChartPeriodType.WEEKLY)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(atLeast = 2) { scrollDataRepository.getUsageForPackageAndDates(eq(packageName), any()) }
        assertThat(viewModel.currentChartPeriodType.value).isEqualTo(ChartPeriodType.WEEKLY)

        viewModel.changeChartPeriod(ChartPeriodType.MONTHLY)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(atLeast = 3) { scrollDataRepository.getUsageForPackageAndDates(eq(packageName), any()) }
        assertThat(viewModel.currentChartPeriodType.value).isEqualTo(ChartPeriodType.MONTHLY)
    }

    @Test
    fun `date navigation updates chart data`() = runTest {
        val initialDate = viewModel.currentChartReferenceDate.value
        viewModel.navigateChartDate(1)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotEquals(initialDate, viewModel.currentChartReferenceDate.value)

        viewModel.navigateChartDate(-1)
        testDispatcher.scheduler.advanceUntilIdle()
        assertThat(viewModel.currentChartReferenceDate.value).isEqualTo(initialDate)
    }

    @Test
    fun `when no data, summary shows zero`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        assertThat(viewModel.appDetailFocusedUsageDisplay.value).isEqualTo("0m")
        assertThat(viewModel.appDetailFocusedScrollDisplay.value).isEqualTo("0 m")
    }

    @Test
    fun `loadAppDetailChartData combines usage and scroll data correctly`() = runTest {
        val date = DateUtil.getPastDateString(2)
        val referenceDate = DateUtil.getCurrentLocalDateString()
        val weeklyDates = DateUtil.getStartOfWeek(DateUtil.parseLocalDate(referenceDate)!!).let { start ->
            (0..6).map { start.plusDays(it.toLong()).toString() }
        }

        coEvery {
            scrollDataRepository.getUsageForPackageAndDates(eq(packageName), eq(weeklyDates))
        } returns listOf(
            DailyAppUsageRecord(packageName = packageName, dateString = date, usageTimeMillis = 3600000, activeTimeMillis = 1800000, appOpenCount = 5, notificationCount = 0, lastUpdatedTimestamp = 0)
        )
        coEvery {
            scrollDataRepository.getAggregatedScrollForPackageAndDates(eq(packageName), eq(weeklyDates))
        } returns listOf(
            AppScrollDataPerDate(dateString = date, totalScroll = 1500, totalScrollX = 500, totalScrollY = 1000)
        )
        coEvery { conversionUtil.formatScrollDistance(500, 1000) } returns ("1.23" to "m")

        viewModel.changeChartPeriod(ChartPeriodType.WEEKLY)
        testDispatcher.scheduler.advanceUntilIdle()

        val chartData = viewModel.appDetailChartData.value
        assertThat(chartData).hasSize(7)
        val dayData = chartData.find { it.date == date }
        assertThat(dayData).isNotNull()
        assertThat(dayData!!.usageTimeMillis).isEqualTo(3600000)
        assertThat(dayData.activeTimeMillis).isEqualTo(1800000)
        assertThat(dayData.scrollUnits).isEqualTo(1500)
        assertThat(dayData.openCount).isEqualTo(5)

        viewModel.setFocusedDate(date)
        testDispatcher.scheduler.advanceUntilIdle()
    }
}
