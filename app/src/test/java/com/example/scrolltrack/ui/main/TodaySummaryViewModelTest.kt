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
import io.mockk.coEvery
import io.mockk.coVerify
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
class TodaySummaryViewModelTest {

    private lateinit var viewModel: TodaySummaryViewModel
    private val scrollDataRepository: ScrollDataRepository = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val appUiModelMapper: AppUiModelMapper = mockk(relaxed = true)
    private val conversionUtil: ConversionUtil = mockk(relaxed = true)
    private val greetingUtil: GreetingUtil = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { settingsRepository.selectedTheme } returns flowOf(AppTheme.CalmLavender)
        coEvery { settingsRepository.isDarkMode } returns flowOf(true)
        viewModel = TodaySummaryViewModel(scrollDataRepository, settingsRepository, appUiModelMapper, conversionUtil, greetingUtil, context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when viewmodel initializes, it is in loading state and then ready`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.uiState.test {
            assertEquals(UiState.Ready, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `summary data is calculated correctly`() = runTest {
        val today = DateUtil.getCurrentLocalDateString()
        val summary = DailyDeviceSummary(today, 3600000, 1800000, 10, 20, 0, 5, 0, 0)
        coEvery { scrollDataRepository.getDeviceSummaryForDate(today) } returns flowOf(summary)
        // No need to mock DateUtil as it's an object
        // coEvery { conversionUtil.formatDurationMillisToHourMinute(3600000) } returns "1h 0m"

        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.totalPhoneUsageTodayFormatted.test {
            assertEquals("...", awaitItem())
            assertEquals("0m", awaitItem())
            assertEquals("1h 0m", awaitItem())
        }
        viewModel.totalUnlocksToday.test {
            assertEquals(0, awaitItem())
            assertEquals(10, awaitItem())
        }
        viewModel.totalNotificationsToday.test {
            assertEquals(0, awaitItem())
            assertEquals(20, awaitItem())
        }
    }

    @Test
    fun `top weekly app is identified correctly`() = runTest {
        val today = DateUtil.getCurrentLocalDateString()
        val sevenDaysAgo = DateUtil.getPastDateString(6)
        val records = listOf(
            DailyAppUsageRecord(1, "app1", today, 1000, 0, 0, 0, 0),
            DailyAppUsageRecord(2, "app2", today, 2000, 0, 0, 0, 0),
            DailyAppUsageRecord(3, "app1", sevenDaysAgo, 500, 0, 0, 0, 0)
        )
        val topApp = AppUsageUiItem("app2", "App Two", null, 2000, "app2")


        coEvery { scrollDataRepository.getUsageRecordsForDateRange(sevenDaysAgo, today) } returns flowOf(records)
        coEvery { appUiModelMapper.mapToAppUsageUiItem(any()) } returns topApp
        testDispatcher.scheduler.advanceUntilIdle()


        viewModel.topWeeklyApp.test {
            assertEquals(null, awaitItem())
            assertEquals(topApp, awaitItem())
        }
    }

    @Test
    fun `onAppResumed triggers a refresh`() = runTest {
        viewModel.onAppResumed()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { scrollDataRepository.refreshDataOnAppOpen() }
    }
} 