package com.example.scrolltrack.ui.detail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.db.AppMetadata
import com.example.scrolltrack.util.ConversionUtil
import com.example.scrolltrack.util.DateUtil
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        viewModel = AppDetailViewModel(scrollDataRepository, appMetadataRepository, conversionUtil, savedStateHandle, context, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when viewmodel initializes, it loads app info and daily data`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.appDetailAppName.test {
            assertThat(awaitItem()).isEqualTo("App One")
        }
        coVerify { scrollDataRepository.getUsageForPackageAndDates(eq(packageName), any()) }
        coVerify { scrollDataRepository.getAggregatedScrollForPackageAndDates(eq(packageName), any()) }
    }

    @Test
    fun `when chart period changes, data is reloaded`() = runTest {
        viewModel.changeChartPeriod(ChartPeriodType.WEEKLY)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { scrollDataRepository.getUsageForPackageAndDates(eq(packageName), any()) }
        assertThat(viewModel.currentChartPeriodType.value).isEqualTo(ChartPeriodType.WEEKLY)

        viewModel.changeChartPeriod(ChartPeriodType.MONTHLY)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { scrollDataRepository.getUsageForPackageAndDates(eq(packageName), any()) }
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
        viewModel.appDetailFocusedUsageDisplay.test {
            assertThat(awaitItem()).isEqualTo("0m")
        }
        viewModel.appDetailFocusedScrollDisplay.test {
            assertThat(awaitItem()).isEqualTo("0 m")
        }
    }
} 