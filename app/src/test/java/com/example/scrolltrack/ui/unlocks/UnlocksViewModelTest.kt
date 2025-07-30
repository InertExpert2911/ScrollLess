package com.example.scrolltrack.ui.unlocks

import app.cash.turbine.test
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.db.DailyAppUsageRecord
import com.example.scrolltrack.db.DailyDeviceSummary
import com.example.scrolltrack.ui.mappers.AppUiModelMapper
import com.example.scrolltrack.ui.model.AppOpenUiItem
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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
import java.time.LocalDate
import java.time.YearMonth

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class UnlocksViewModelTest {

    private lateinit var viewModel: UnlocksViewModel
    private val scrollDataRepository: ScrollDataRepository = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private val summariesFlow = MutableStateFlow<List<DailyDeviceSummary>>(emptyList())
    private val usageFlow = MutableStateFlow<List<DailyAppUsageRecord>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { scrollDataRepository.getAllDeviceSummaries() } returns summariesFlow
        every { scrollDataRepository.getUsageRecordsForDateRange(any(), any()) } returns usageFlow
        val mapper: AppUiModelMapper = mockk(relaxed = true) {
            coEvery { mapToAppOpenUiItem(any()) } answers {
                val record = firstArg<DailyAppUsageRecord>()
                AppOpenUiItem(record.packageName, record.packageName, null, record.appOpenCount)
            }
        }
        viewModel = UnlocksViewModel(scrollDataRepository, mapper, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `weekly data is correctly averaged and sorted`() = runTest {
        val summaries = listOf(
            DailyDeviceSummary(dateString = "2023-10-23", totalUnlockCount = 10),
            DailyDeviceSummary(dateString = "2023-10-24", totalUnlockCount = 20)
        )
        val usageRecords = listOf(
            DailyAppUsageRecord(packageName = "com.app1", dateString = "2023-10-23", appOpenCount = 10, usageTimeMillis = 0),
            DailyAppUsageRecord(packageName = "com.app2", dateString = "2023-10-25", appOpenCount = 21, usageTimeMillis = 0)
        )

        viewModel.uiState.test {
            // Initial state
            awaitItem()

            // Provide data to the flows
            summariesFlow.value = summaries
            usageFlow.value = usageRecords

            // Trigger the update
            viewModel.onDateSelected(LocalDate.of(2023, 10, 26))
            viewModel.onPeriodChanged(UnlockPeriod.Weekly)

            // Await the final state after processing
            val state = awaitItem()
            
            assertThat(state.unlockStat).isEqualTo(4) // (10 + 20) / 7
            assertThat(state.appOpens.map { it.packageName }).containsExactly("com.app2", "com.app1").inOrder()
            assertThat(state.appOpens.find { it.packageName == "com.app2" }?.openCount).isEqualTo(3) // 21 / 7
            assertThat(state.appOpens.find { it.packageName == "com.app1" }?.openCount).isEqualTo(1) // 10 / 7
        }
    }

    @Test
    fun `daily data is correctly displayed without averaging`() = runTest {
        val targetDate = LocalDate.of(2023, 10, 23)
        val summaries = listOf(
            DailyDeviceSummary(dateString = "2023-10-23", totalUnlockCount = 15),
            DailyDeviceSummary(dateString = "2023-10-24", totalUnlockCount = 25) // Other day's data
        )
        val usageRecords = listOf(
            DailyAppUsageRecord(packageName = "com.app1", dateString = "2023-10-23", appOpenCount = 5, usageTimeMillis = 0),
            DailyAppUsageRecord(packageName = "com.app2", dateString = "2023-10-23", appOpenCount = 10, usageTimeMillis = 0),
            DailyAppUsageRecord(packageName = "com.app3", dateString = "2023-10-24", appOpenCount = 30, usageTimeMillis = 0) // Other day's data
        )
        
        // More specific mock for this test case
        every {
            scrollDataRepository.getUsageRecordsForDateRange(
                targetDate.toString(),
                targetDate.toString()
            )
        } returns flowOf(usageRecords.filter { it.dateString == targetDate.toString() })


        viewModel.uiState.test {
            awaitItem() // Initial state

            summariesFlow.value = summaries
            // usageFlow is not used in this test due to the specific mock above

            viewModel.onDateSelected(targetDate)
            viewModel.onPeriodChanged(UnlockPeriod.Daily)

            val state = awaitItem()

            assertThat(state.unlockStat).isEqualTo(15) // Exact value for the day
            assertThat(state.appOpens.map { it.packageName }).containsExactly("com.app2", "com.app1").inOrder()
            assertThat(state.appOpens.find { it.packageName == "com.app1" }?.openCount).isEqualTo(5) // Exact value
            assertThat(state.appOpens.find { it.packageName == "com.app2" }?.openCount).isEqualTo(10) // Exact value
        }
    }

    @Test
    fun `monthly data is correctly averaged and sorted`() = runTest {
        // October has 31 days
        val summaries = listOf(
            DailyDeviceSummary(dateString = "2023-10-01", totalUnlockCount = 31),
            DailyDeviceSummary(dateString = "2023-10-15", totalUnlockCount = 31),
            DailyDeviceSummary(dateString = "2023-11-01", totalUnlockCount = 100) // Different month
        )
        val usageRecords = listOf(
            DailyAppUsageRecord(packageName = "com.app1", dateString = "2023-10-01", appOpenCount = 62, usageTimeMillis = 0),
            DailyAppUsageRecord(packageName = "com.app2", dateString = "2023-10-31", appOpenCount = 31, usageTimeMillis = 0)
        )

        viewModel.uiState.test {
            awaitItem()

            summariesFlow.value = summaries
            usageFlow.value = usageRecords

            viewModel.onDateSelected(LocalDate.of(2023, 10, 26))
            viewModel.onPeriodChanged(UnlockPeriod.Monthly)

            val state = awaitItem()

            assertThat(state.unlockStat).isEqualTo(2) // (31 + 31) / 31
            assertThat(state.appOpens.map { it.packageName }).containsExactly("com.app1", "com.app2").inOrder()
            assertThat(state.appOpens.find { it.packageName == "com.app1" }?.openCount).isEqualTo(2) // 62 / 31
            assertThat(state.appOpens.find { it.packageName == "com.app2" }?.openCount).isEqualTo(1) // 31 / 31
        }
    }

    @Test
    fun `heatmap and month data are correctly generated`() = runTest {
        val summaries = listOf(
            DailyDeviceSummary(dateString = "2023-08-10", totalUnlockCount = 5),
            DailyDeviceSummary(dateString = "2023-09-01", totalUnlockCount = 10),
            DailyDeviceSummary(dateString = "2023-09-15", totalUnlockCount = 20),
            DailyDeviceSummary(dateString = "2023-10-05", totalUnlockCount = 50)
        )

        viewModel.uiState.test {
            awaitItem()

            summariesFlow.value = summaries
            // No need to trigger period/date change, this should be calculated on init
            
            val state = awaitItem()

            assertThat(state.heatmapData).hasSize(4)
            assertThat(state.heatmapData[LocalDate.of(2023, 9, 15)]).isEqualTo(20)
            
            val expectedMonths = listOf(
                YearMonth.of(2023, 8),
                YearMonth.of(2023, 9),
                YearMonth.of(2023, 10)
            )
            assertThat(state.monthsWithData).containsExactlyElementsIn(expectedMonths).inOrder()
        }
    }

    @Test
    fun `uiState is correct when repository returns no data`() = runTest {
        viewModel.uiState.test {
            awaitItem() // Initial state

            summariesFlow.value = emptyList()
            usageFlow.value = emptyList()

            viewModel.onDateSelected(LocalDate.of(2023, 10, 26))
            viewModel.onPeriodChanged(UnlockPeriod.Weekly)

            val state = awaitItem()

            assertThat(state.unlockStat).isEqualTo(0)
            assertThat(state.appOpens).isEmpty()
            assertThat(state.heatmapData).isEmpty()
            assertThat(state.monthsWithData).isEmpty()
        }
    }
}