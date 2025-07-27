package com.example.scrolltrack.ui.detail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.db.AppMetadata
import com.example.scrolltrack.db.DailyAppUsageRecord
import com.example.scrolltrack.ui.main.ComparisonColorType
import com.example.scrolltrack.ui.main.ComparisonIconType
import com.example.scrolltrack.util.ConversionUtil
import com.example.scrolltrack.util.DateUtil
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class AppDetailViewModelTest {

    private lateinit var viewModel: AppDetailViewModel
    private val scrollDataRepository: ScrollDataRepository = mockk(relaxed = true)
    private val appMetadataRepository: AppMetadataRepository = mockk(relaxed = true)
    private val conversionUtil: ConversionUtil = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private val packageName = "com.example.app"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val savedStateHandle = SavedStateHandle(mapOf("packageName" to packageName))
        val context: Context = mockk(relaxed = true)

        coEvery { appMetadataRepository.getAppMetadata(packageName) } returns AppMetadata(packageName, "Test App", isUserVisible = true, isSystemApp = false, userHidesOverride = null, installTimestamp = 0L, lastUpdateTimestamp = 0L, versionName = "1.0", versionCode = 1L)
        coEvery { appMetadataRepository.getIconFile(packageName) } returns null
        coEvery { scrollDataRepository.getUsageForPackageAndDates(any(), any()) } returns emptyList()
        coEvery { scrollDataRepository.getAggregatedScrollForPackageAndDates(any(), any()) } returns emptyList()
        coEvery { conversionUtil.formatScrollDistance(any(), any()) } returns ("0" to "m")

        viewModel = AppDetailViewModel(scrollDataRepository, appMetadataRepository, conversionUtil, savedStateHandle, context, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `weekly summary calculates average usage correctly`() = runTest {
        val refDate = LocalDate.of(2023, 10, 26)
        val startOfWeek = DateUtil.getStartOfWeek(refDate)
        val dateStrings = (0..6).map { startOfWeek.plusDays(it.toLong()).toString() }
        val currentWeekUsage = listOf(DailyAppUsageRecord(packageName = packageName, dateString = dateStrings[0], usageTimeMillis = 70000, appOpenCount = 1))
        coEvery { scrollDataRepository.getUsageForPackageAndDates(packageName, dateStrings) } returns currentWeekUsage

        viewModel.appDetailFocusedUsageDisplay.test {
            awaitItem() // consume initial state

            viewModel.changeChartPeriod(ChartPeriodType.WEEKLY)
            viewModel.setFocusedDate(refDate.toString())
            
            // Consume intermediate states
            awaitItem()
            val finalState = awaitItem()

            // 70000ms / 7 days = 10000ms -> "< 1m"
            assertThat(finalState).isEqualTo("< 1m")
        }
    }

    @Test
    fun `updateComparisonText generates correct strings`() {
        viewModel.updateComparisonText(10000, 5000, "week")
        assertThat(viewModel.appDetailComparisonText.value).isEqualTo("100% more vs last week")
        assertThat(viewModel.appDetailComparisonIconType.value).isEqualTo(ComparisonIconType.UP)
        assertThat(viewModel.appDetailComparisonColorType.value).isEqualTo(ComparisonColorType.RED)
    }
}
