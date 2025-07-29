package com.example.scrolltrack.ui.main

import android.content.Context
import app.cash.turbine.test
import com.example.scrolltrack.data.*
import com.example.scrolltrack.db.DailyAppUsageRecord
import com.example.scrolltrack.db.DailyDeviceSummary
import com.example.scrolltrack.ui.mappers.AppUiModelMapper
import com.example.scrolltrack.ui.model.AppUsageUiItem
import com.example.scrolltrack.ui.theme.AppTheme
import com.example.scrolltrack.util.ConversionUtil
import com.example.scrolltrack.util.DateUtil
import com.example.scrolltrack.util.GreetingUtil
import com.example.scrolltrack.util.PermissionUtils
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.TimeZone

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class TodaySummaryViewModelTest {

    private lateinit var viewModel: TodaySummaryViewModel
    private val scrollDataRepository: ScrollDataRepository = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val appUiModelMapper: AppUiModelMapper = mockk(relaxed = true)
    private val conversionUtil: ConversionUtil = mockk(relaxed = true)
    private val greetingUtil: GreetingUtil = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private val summaryFlow = MutableStateFlow<DailyDeviceSummary?>(null)
    private val yesterdaySummaryFlow = MutableStateFlow<DailyDeviceSummary?>(null)
    private val weeklyUsageFlow = MutableStateFlow<List<DailyAppUsageRecord>>(emptyList())

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Dispatchers.setMain(testDispatcher)
        val today = DateUtil.getCurrentLocalDateString()
        val yesterday = DateUtil.getPastDateString(1)
        val sevenDaysAgo = DateUtil.getPastDateString(6)

        every { scrollDataRepository.getDeviceSummaryForDate(today) } returns summaryFlow
        every { scrollDataRepository.getDeviceSummaryForDate(yesterday) } returns yesterdaySummaryFlow
        every { scrollDataRepository.getUsageRecordsForDateRange(sevenDaysAgo, today) } returns weeklyUsageFlow
        coEvery { scrollDataRepository.refreshDataOnAppOpen() } just runs
        coEvery { settingsRepository.setSelectedTheme(any()) } just runs

        // Mock permission flows
        mockkObject(PermissionUtils)
        every { PermissionUtils.isUsageStatsPermissionGrantedFlow(any()) } returns flowOf(true)
        every { PermissionUtils.isAccessibilityServiceEnabledFlow(any()) } returns flowOf(true)
        every { PermissionUtils.isNotificationListenerEnabledFlow(any()) } returns flowOf(true)

        viewModel = TodaySummaryViewModel(scrollDataRepository, settingsRepository, appUiModelMapper, conversionUtil, greetingUtil, context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(PermissionUtils)
    }

    @Test
    fun `when viewmodel initializes, it is in loading state and then ready`() = runTest {
        viewModel.uiState.test {
            assertThat(awaitItem()).isInstanceOf(UiState.InitialLoading::class.java)
            advanceUntilIdle()
            assertThat(awaitItem()).isInstanceOf(UiState.Ready::class.java)
        }
    }

    @Test
    fun `summary data is correctly propagated`() = runTest {
        val summary = DailyDeviceSummary(DateUtil.getCurrentLocalDateString(), 3600000, 1800000, 10, 5, 5, 20, 0, 0)
        
        viewModel.totalPhoneUsageTodayMillis.test {
            assertThat(awaitItem()).isEqualTo(0L) // Initial value
            summaryFlow.value = summary
            assertThat(awaitItem()).isEqualTo(3600000L)
        }
    }

    @Test
    fun `top weekly app is correctly aggregated`() = runTest {
        val records = listOf(
            DailyAppUsageRecord(packageName = "app1", dateString = "d1", usageTimeMillis = 1000, appOpenCount = 1),
            DailyAppUsageRecord(packageName = "app2", dateString = "d1", usageTimeMillis = 5000, appOpenCount = 1),
            DailyAppUsageRecord(packageName = "app1", dateString = "d2", usageTimeMillis = 2000, appOpenCount = 1)
        )
        coEvery { appUiModelMapper.mapToAppUsageUiItem(any()) } answers {
            val record = firstArg<DailyAppUsageRecord>()
            AppUsageUiItem(record.packageName, "Mapped", null, record.usageTimeMillis, record.packageName)
        }

        viewModel.topWeeklyApp.test {
            awaitItem() // consume initial null
            weeklyUsageFlow.value = records
            val topApp = awaitItem()
            assertThat(topApp?.packageName).isEqualTo("app2")
            assertThat(topApp?.usageTimeMillis).isEqualTo(5000L)
        }
    }

    @Test
    fun `onPullToRefresh - success - shows refreshing state and snackbar`() = runTest {
        viewModel.uiState.test {
            skipItems(2) // Skip initial loading -> ready
            viewModel.onPullToRefresh()
            assertThat(awaitItem()).isEqualTo(UiState.Refreshing)
            assertThat(awaitItem()).isEqualTo(UiState.Ready)
            cancelAndIgnoreRemainingEvents()
        }
        advanceUntilIdle()
        viewModel.snackbarMessage.test {
            assertThat(awaitItem()).isEqualTo("Data refreshed successfully")
        }
        coVerify { scrollDataRepository.refreshDataOnAppOpen() }
    }

    @Test
    fun `onPullToRefresh - failure - shows error state and snackbar`() = runTest {
        val errorMessage = "Error refreshing data"
        coEvery { scrollDataRepository.refreshDataOnAppOpen() } throws Exception("DB error")

        viewModel.uiState.test {
            skipItems(2)
            viewModel.onPullToRefresh()
            assertThat(awaitItem()).isEqualTo(UiState.Refreshing)
            val errorState = awaitItem()
            assertThat(errorState).isInstanceOf(UiState.Error::class.java)
            assertThat(awaitItem()).isEqualTo(UiState.Ready)
            cancelAndIgnoreRemainingEvents()
        }
        advanceUntilIdle()
        viewModel.snackbarMessage.test {
            assertThat(awaitItem()).isEqualTo(errorMessage)
        }
    }

    @Test
    fun `dismissSnackbar - clears snackbar message`() = runTest {
        coEvery { scrollDataRepository.refreshDataOnAppOpen() } throws Exception("DB error")
        viewModel.onPullToRefresh()
        advanceUntilIdle()
        viewModel.snackbarMessage.test {
            assertThat(awaitItem()).isNotNull()
            viewModel.dismissSnackbar()
            assertThat(awaitItem()).isNull()
        }
    }

    @Test
    fun `screenTimeComparison - calculates correctly`() = runTest {
        val todaySummary = DailyDeviceSummary(DateUtil.getCurrentLocalDateString(), 120, 0, 0, 0, 0, 0, 0, 0)
        val yesterdaySummary = DailyDeviceSummary(DateUtil.getPastDateString(1), 100, 0, 0, 0, 0, 0, 0, 0)

        viewModel.screenTimeComparison.test {
            awaitItem() // consume initial null
            summaryFlow.value = todaySummary
            yesterdaySummaryFlow.value = yesterdaySummary
            val comparison = awaitItem()
            assertThat(comparison?.isIncrease).isTrue()
            assertThat(comparison?.percentageChange).isWithin(0.01f).of(20.0f)
        }
    }

    @Test
    fun `performHistoricalUsageDataBackfill - permission granted - calls repository`() = runTest {
        coEvery { scrollDataRepository.backfillHistoricalAppUsageData(7) } returns true
        var completed = false
        viewModel.performHistoricalUsageDataBackfill(7) { success ->
            completed = success
        }
        advanceUntilIdle()
        coVerify { scrollDataRepository.backfillHistoricalAppUsageData(7) }
        assertThat(completed).isTrue()
    }

    @Test
    fun `updateThemePalette - calls settings repository`() = runTest {
        val theme = AppTheme.ClarityTeal
        viewModel.updateThemePalette(theme)
        advanceUntilIdle()
        coVerify { settingsRepository.setSelectedTheme(theme) }
    }

    @Test
    fun `firstUnlockTime and lastUnlockTime formatted correctly`() = runTest {
        val today = DateUtil.getCurrentLocalDateString()
        val startOfDay = DateUtil.getStartOfDayUtcMillis(today)
        val summary = DailyDeviceSummary(
            dateString = today, totalUsageTimeMillis = 0, totalUnlockedDurationMillis = 0,
            totalUnlockCount = 0, intentionalUnlockCount = 0, glanceUnlockCount = 0,
            totalNotificationCount = 0,
            firstUnlockTimestampUtc = startOfDay + 3600000, // 1 AM
            lastUnlockTimestampUtc = startOfDay + 72000000 // 8 PM
        )
        
        mockkObject(DateUtil) // Re-mock to control time formatting for this test
        every { DateUtil.formatUtcTimestampToTimeString(startOfDay + 3600000) } returns "1:00 AM"
        every { DateUtil.formatUtcTimestampToTimeString(startOfDay + 72000000) } returns "8:00 PM"

        viewModel.firstUnlockTime.test {
            assertThat(awaitItem()).isEqualTo("N/A") // Initial value
            summaryFlow.value = summary
            assertThat(awaitItem()).isEqualTo("1:00 AM")
        }
        viewModel.lastUnlockTime.test {
            assertThat(awaitItem()).isEqualTo("N/A") // Initial value
            summaryFlow.value = summary
            assertThat(awaitItem()).isEqualTo("8:00 PM")
        }
    }
}