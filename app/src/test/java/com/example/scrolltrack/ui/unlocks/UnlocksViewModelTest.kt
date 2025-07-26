package com.example.scrolltrack.ui.unlocks

import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.db.DailyDeviceSummary
import com.example.scrolltrack.ui.mappers.AppUiModelMapper
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
    private lateinit var scrollDataRepository: ScrollDataRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        scrollDataRepository = mockk(relaxed = true)
        val mapper: AppUiModelMapper = mockk(relaxed = true)
        viewModel = UnlocksViewModel(scrollDataRepository, mapper, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test date range calculation`() = runTest(testDispatcher) {
        val date = LocalDate.of(2023, 10, 26)
        viewModel.onDateSelected(date)
        viewModel.onPeriodChanged(UnlockPeriod.Weekly)
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertThat(uiState.periodDisplay).contains("Oct 23")
        assertThat(uiState.periodDisplay).contains("29, 2023")
    }

    @Test
    fun `test data averaging`() = runTest(testDispatcher) {
        val summaries = listOf(
            DailyDeviceSummary("2023-10-23", 10, 0, 0, 0, 0),
            DailyDeviceSummary("2023-10-24", 20, 0, 0, 0, 0)
        )
        coEvery { scrollDataRepository.getAllDeviceSummaries() } returns flowOf(summaries)
        viewModel.onDateSelected(LocalDate.of(2023, 10, 26))
        viewModel.onPeriodChanged(UnlockPeriod.Weekly)
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertThat(uiState.unlockStat).isEqualTo(4) // (10 + 20) / 7
    }
}