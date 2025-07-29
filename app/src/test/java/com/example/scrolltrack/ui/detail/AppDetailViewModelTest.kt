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
    fun `init - metadata not found - falls back to package name`() = runTest {
        coEvery { appMetadataRepository.getAppMetadata(packageName) } returns null
        val savedStateHandle = SavedStateHandle(mapOf("packageName" to packageName))
        viewModel = AppDetailViewModel(scrollDataRepository, appMetadataRepository, conversionUtil, savedStateHandle, mockk(relaxed = true), testDispatcher)
        
        viewModel.appDetailAppName.test {
            assertThat(awaitItem()).isEqualTo("app")
        }
    }

    @Test
    fun `daily period - loads correct data for focused date`() = runTest {
        val refDate = LocalDate.of(2023, 10, 26)
        val startOfWeek = DateUtil.getStartOfWeek(refDate)
        val dateStrings = (0..6).map { startOfWeek.plusDays(it.toLong()).toString() }
        val usageData = listOf(
            DailyAppUsageRecord(packageName = packageName, dateString = refDate.toString(), usageTimeMillis = 50000, appOpenCount = 5)
        )
        coEvery { scrollDataRepository.getUsageForPackageAndDates(packageName, dateStrings) } returns usageData
        coEvery { conversionUtil.formatScrollDistance(0, 0) } returns ("50" to "cm")

        viewModel.setFocusedDate(refDate.toString())

        viewModel.appDetailChartData.test {
            assertThat(awaitItem().size).isEqualTo(7)
        }
        viewModel.appDetailFocusedUsageDisplay.test {
            assertThat(awaitItem()).isEqualTo(DateUtil.formatDuration(50000))
        }
        viewModel.appDetailFocusedOpenCount.test {
            assertThat(awaitItem()).isEqualTo(5)
        }
    }

    @Test
    fun `weekly period - calculates and displays weekly average and comparison`() = runTest {
        val refDate = LocalDate.of(2023, 10, 26)
        val startOfWeek = DateUtil.getStartOfWeek(refDate)
        val currentWeekStrings = (0..6).map { startOfWeek.plusDays(it.toLong()).toString() }
        val prevWeekStrings = (0..6).map { startOfWeek.minusWeeks(1).plusDays(it.toLong()).toString() }

        val currentWeekUsage = listOf(DailyAppUsageRecord(packageName = packageName, dateString = currentWeekStrings[0], usageTimeMillis = 70000, appOpenCount = 1))
        val prevWeekUsage = listOf(DailyAppUsageRecord(packageName = packageName, dateString = prevWeekStrings[0], usageTimeMillis = 35000, appOpenCount = 1))

        coEvery { scrollDataRepository.getUsageForPackageAndDates(packageName, currentWeekStrings) } returns currentWeekUsage
        coEvery { scrollDataRepository.getUsageForPackageAndDates(packageName, prevWeekStrings) } returns prevWeekUsage

        viewModel.changeChartPeriod(ChartPeriodType.WEEKLY)

        viewModel.appDetailFocusedUsageDisplay.test {
            // 70000ms / 7 days = 10000ms -> "< 1m"
            assertThat(awaitItem()).isEqualTo(DateUtil.formatDuration(10000))
        }
        viewModel.appDetailComparisonText.test {
            assertThat(awaitItem()).isEqualTo("100% more vs last week")
        }
    }

    @Test
    fun `navigateChartDate - updates reference date and reloads data`() = runTest {
        val initialDate = "2023-10-26"
        viewModel.setFocusedDate(initialDate)
        
        viewModel.navigateChartDate(1) // Forward one day
        
        viewModel.currentChartReferenceDate.test {
            assertThat(awaitItem()).isEqualTo("2023-10-27")
        }
        coVerify { scrollDataRepository.getUsageForPackageAndDates(packageName, any()) }
    }

    @Test
    fun `updateComparisonText - all branches`() {
        viewModel.updateComparisonText(10000, 5000, "week")
        assertThat(viewModel.appDetailComparisonText.value).isEqualTo("100% more vs last week")
        assertThat(viewModel.appDetailComparisonIconType.value).isEqualTo(ComparisonIconType.UP)
        assertThat(viewModel.appDetailComparisonColorType.value).isEqualTo(ComparisonColorType.RED)

        viewModel.updateComparisonText(5000, 10000, "week")
        assertThat(viewModel.appDetailComparisonText.value).isEqualTo("50% less vs last week")
        assertThat(viewModel.appDetailComparisonIconType.value).isEqualTo(ComparisonIconType.DOWN)
        assertThat(viewModel.appDetailComparisonColorType.value).isEqualTo(ComparisonColorType.GREEN)

        viewModel.updateComparisonText(10000, 0, "week")
        assertThat(viewModel.appDetailComparisonText.value).isEqualTo("${DateUtil.formatDurationWithSeconds(10000)} from no usage")

        viewModel.updateComparisonText(10000, 10000, "week")
        assertThat(viewModel.appDetailComparisonText.value).isEqualTo("Same as last week")
    }
}
