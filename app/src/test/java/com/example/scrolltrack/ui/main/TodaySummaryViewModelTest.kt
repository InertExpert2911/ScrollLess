package com.example.scrolltrack.ui.main

import android.content.Context
import app.cash.turbine.test
import com.example.scrolltrack.data.*
import com.example.scrolltrack.db.DailyAppUsageRecord
import com.example.scrolltrack.db.DailyDeviceSummary
import com.example.scrolltrack.ui.mappers.AppUiModelMapper
import com.example.scrolltrack.ui.model.AppUsageUiItem
import com.example.scrolltrack.util.ConversionUtil
import com.example.scrolltrack.util.DateUtil
import com.example.scrolltrack.util.GreetingUtil
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
    private val weeklyUsageFlow = MutableStateFlow<List<DailyAppUsageRecord>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val today = DateUtil.getCurrentLocalDateString()
        val sevenDaysAgo = DateUtil.getPastDateString(6)
        every { scrollDataRepository.getDeviceSummaryForDate(today) } returns summaryFlow
        every { scrollDataRepository.getUsageRecordsForDateRange(sevenDaysAgo, today) } returns weeklyUsageFlow
        coEvery { scrollDataRepository.refreshDataOnAppOpen() } just runs

        viewModel = TodaySummaryViewModel(scrollDataRepository, settingsRepository, appUiModelMapper, conversionUtil, greetingUtil, context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when viewmodel initializes, it is in loading state and then ready`() = runTest {
        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(UiState.InitialLoading)
            assertThat(awaitItem()).isEqualTo(UiState.Ready)
        }
    }

    @Test
    fun `summary data is correctly propagated`() = runTest {
        val summary = DailyDeviceSummary(DateUtil.getCurrentLocalDateString(), 3600000, 1800000, 10, 20, 0, 5, 0, 0)
        
        viewModel.totalPhoneUsageTodayMillis.test {
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
            weeklyUsageFlow.value = records
            val topApp = awaitItem()
            assertThat(topApp?.packageName).isEqualTo("app2")
            assertThat(topApp?.usageTimeMillis).isEqualTo(5000L)
        }
    }
}