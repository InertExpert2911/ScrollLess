package com.example.scrolltrack.ui.detail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.util.ConversionUtil
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class AppDetailViewModelTest {

    private lateinit var viewModel: AppDetailViewModel
    private lateinit var scrollDataRepository: ScrollDataRepository
    private lateinit var appMetadataRepository: AppMetadataRepository
    private lateinit var conversionUtil: ConversionUtil
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        scrollDataRepository = mockk(relaxed = true)
        appMetadataRepository = mockk(relaxed = true)
        conversionUtil = mockk(relaxed = true)
        val savedStateHandle = SavedStateHandle(mapOf("packageName" to "com.example.app"))
        val context: Context = mockk(relaxed = true)
        viewModel = AppDetailViewModel(scrollDataRepository, appMetadataRepository, conversionUtil, savedStateHandle, context, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test change chart period`() = runTest {
        viewModel.changeChartPeriod(ChartPeriodType.WEEKLY)
        assertThat(viewModel.currentChartPeriodType.value).isEqualTo(ChartPeriodType.WEEKLY)
    }

    @Test
    fun `test navigate chart date`() = runTest {
        val initialDate = viewModel.currentChartReferenceDate.value
        viewModel.navigateChartDate(-1)
        assertThat(viewModel.currentChartReferenceDate.value).isNotEqualTo(initialDate)
    }

    @Test
    fun `test summary calculations`() = runTest {
        val data = listOf(
            CombinedAppDailyData("2023-10-26", 1000, 500, 100, 50, 50, 1),
            CombinedAppDailyData("2023-10-27", 2000, 1000, 200, 100, 100, 2)
        )
        coEvery { scrollDataRepository.getUsageForPackageAndDates(any(), any()) } returns emptyList()
        coEvery { scrollDataRepository.getAggregatedScrollForPackageAndDates(any(), any()) } returns emptyList()
        
        viewModel.loadAppDetailsInfo()
        
        // This is a simplified test. A more thorough test would involve mocking the data and verifying the calculated averages.
        assertThat(viewModel.appDetailFocusedUsageDisplay.value).isNotNull()
    }

    @Test
    fun `test comparison text generation`() {
        viewModel.updateComparisonText(1000, 500, "week")
        assertThat(viewModel.appDetailComparisonText.value).isEqualTo("100% more vs last week")
    }
}
