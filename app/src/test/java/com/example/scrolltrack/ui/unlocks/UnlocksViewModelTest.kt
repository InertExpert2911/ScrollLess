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
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

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
}