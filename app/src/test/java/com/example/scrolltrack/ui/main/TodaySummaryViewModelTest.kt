package com.example.scrolltrack.ui.main

import android.content.Context
import app.cash.turbine.test
import com.example.scrolltrack.data.*
import com.example.scrolltrack.db.DailyAppUsageRecord
import com.example.scrolltrack.db.DailyDeviceSummary
import com.example.scrolltrack.ui.mappers.AppUiModelMapper
import com.example.scrolltrack.ui.model.AppScrollUiItem
import com.example.scrolltrack.ui.model.AppUsageUiItem
import com.example.scrolltrack.ui.theme.AppTheme
import com.example.scrolltrack.util.ConversionUtil
import com.example.scrolltrack.util.DateUtil
import com.example.scrolltrack.util.GreetingUtil
import com.google.common.truth.Truth.assertThat
import io.mockk.*
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
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE)
class TodaySummaryViewModelTest {

    private lateinit var viewModel: TodaySummaryViewModel
    private val scrollDataRepository: ScrollDataRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val appUiModelMapper: AppUiModelMapper = mockk(relaxed = true)
    private val conversionUtil: ConversionUtil = mockk(relaxed = true)
    private val greetingUtil: GreetingUtil = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Provide default mock behaviors that can be overridden in specific tests
        every { scrollDataRepository.getDeviceSummaryForDate(any()) } returns flowOf(null)
        every { scrollDataRepository.getAppUsageForDate(any()) } returns flowOf(emptyList())
        every { scrollDataRepository.getScrollDataForDate(any()) } returns flowOf(emptyList())
        every { scrollDataRepository.getUsageRecordsForDateRange(any(), any()) } returns flowOf(emptyList())
        coEvery { scrollDataRepository.refreshDataOnAppOpen() } just runs

        coEvery { conversionUtil.formatScrollDistance(any(), any()) } returns ("0" to "m")
        every { settingsRepository.selectedTheme } returns flowOf(AppTheme.CalmLavender)
        every { settingsRepository.isDarkMode } returns flowOf(true)

        viewModel = TodaySummaryViewModel(
            repository = scrollDataRepository,
            settingsRepository = settingsRepository,
            mapper = appUiModelMapper,
            conversionUtil = conversionUtil,
            greetingUtil = greetingUtil,
            context = context
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when viewmodel initializes, it is in loading state and then ready`() = runTest {
        // Create a new instance to test the init block specifically
        val newViewModel = TodaySummaryViewModel(scrollDataRepository, settingsRepository, appUiModelMapper, conversionUtil, greetingUtil, context)
        newViewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(UiState.InitialLoading)
            // Let the init block's refresh coroutine run
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(UiState.Ready)
            cancelAndConsumeRemainingEvents()
        }
    }


    @Test
    fun `summary data is calculated correctly`() = runTest {
        val today = DateUtil.getCurrentLocalDateString()
        val summary = DailyDeviceSummary(today, 3600000, 1800000, 10, 20, 0, 5, 1622548800000, 1622635200000)
        every { scrollDataRepository.getDeviceSummaryForDate(today) } returns flowOf(summary)

        // Re-create ViewModel to trigger init with the new mock
        viewModel = TodaySummaryViewModel(scrollDataRepository, settingsRepository, appUiModelMapper, conversionUtil, greetingUtil, context)
        
        viewModel.totalPhoneUsageTodayMillis.test {
            // Initial value is 0, then the summary is emitted.
            awaitItem() // Consume initial value
            assertThat(awaitItem()).isEqualTo(3600000L)
            cancelAndConsumeRemainingEvents()
        }
        
        viewModel.totalUnlocksToday.test {
            awaitItem() // Consume initial value
            assertThat(awaitItem()).isEqualTo(10)
            cancelAndConsumeRemainingEvents()
        }

        viewModel.totalNotificationsToday.test {
            awaitItem() // Consume initial value
            assertThat(awaitItem()).isEqualTo(20)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `top weekly app is identified correctly`() = runTest {
        val today = DateUtil.getCurrentLocalDateString()
        val sevenDaysAgo = DateUtil.getPastDateString(6)
        val records = listOf(
            DailyAppUsageRecord(1, "app1", today, 1000),
            DailyAppUsageRecord(2, "app2", today, 2000),
            DailyAppUsageRecord(3, "app1", sevenDaysAgo, 500)
        )
        val topAppUiItem = AppUsageUiItem("app2", "App Two", null, 2000, "app2")

        every { scrollDataRepository.getUsageRecordsForDateRange(sevenDaysAgo, today) } returns flowOf(records)
        // Correctly mock the mapper for the aggregated record
        coEvery { appUiModelMapper.mapToAppUsageUiItem(match { it.packageName == "app2" && it.usageTimeMillis == 2000L }) } returns topAppUiItem

        // Re-create viewModel with the mocks
        viewModel = TodaySummaryViewModel(scrollDataRepository, settingsRepository, appUiModelMapper, conversionUtil, greetingUtil, context)
        
        viewModel.topWeeklyApp.test {
            // The initial value is null
            assertThat(awaitItem()).isNull()
            advanceUntilIdle()
            // After processing, the top app is emitted
            assertThat(awaitItem()).isEqualTo(topAppUiItem)
            cancelAndConsumeRemainingEvents()
        }
    }


    @Test
    fun `onAppResumed triggers a refresh`() = runTest {
        // Initial refresh in init
        coVerify(exactly = 1) { scrollDataRepository.refreshDataOnAppOpen() }

        viewModel.onAppResumed()
        advanceUntilIdle()

        // second refresh from onAppResumed
        coVerify(exactly = 2) { scrollDataRepository.refreshDataOnAppOpen() }
    }

    @Test
    fun `scroll data is correctly aggregated and formatted`() = runTest {
        val today = DateUtil.getCurrentLocalDateString()
        val scrollData = listOf(
            AppScrollData("com.app.one", 1000, 400, 600, "MEASURED"),
            AppScrollData("com.app.two", 700, 500, 200, "MEASURED")
        )

        val mappedScrollData = listOf(
             AppScrollUiItem("com.app.one", "App One", null, 1000, 400, 600, "com.app.one", "MEASURED"),
             AppScrollUiItem("com.app.two", "App Two", null, 700, 500, 200, "com.app.two", "MEASURED")
        )

        every { scrollDataRepository.getScrollDataForDate(today) } returns flowOf(scrollData)
        coEvery { appUiModelMapper.mapToAppScrollUiItem(scrollData[0]) } returns mappedScrollData[0]
        coEvery { appUiModelMapper.mapToAppScrollUiItem(scrollData[1]) } returns mappedScrollData[1]

        val totalX = 900L
        val totalY = 800L
        coEvery { conversionUtil.formatScrollDistance(totalX, totalY) } returns ("1.70" to "m")

        viewModel = TodaySummaryViewModel(scrollDataRepository, settingsRepository, appUiModelMapper, conversionUtil, greetingUtil, context)

        viewModel.totalScrollToday.test {
            assertThat(awaitItem()).isEqualTo(0L) // Initial value
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(1700L)
            cancelAndConsumeRemainingEvents()
        }

        viewModel.scrollDistanceTodayFormatted.test {
            assertThat(awaitItem()).isEqualTo("0" to "m") // Initial value
            advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo("1.70" to "m")
            cancelAndConsumeRemainingEvents()
        }
    }
} 