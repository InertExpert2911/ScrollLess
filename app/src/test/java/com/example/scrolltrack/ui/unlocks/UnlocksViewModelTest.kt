package com.example.scrolltrack.ui.unlocks

import app.cash.turbine.test
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.db.DailyDeviceSummary
import com.example.scrolltrack.ui.mappers.AppUiModelMapper
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
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
import java.time.LocalDate

@ExperimentalCoroutinesApi
class UnlocksViewModelTest {
    private lateinit var viewModel: UnlocksViewModel
    private val scrollDataRepository: ScrollDataRepository = mockk()
    private val appUiModelMapper: AppUiModelMapper = mockk()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { scrollDataRepository.getAllDeviceSummaries() } returns flowOf(emptyList())
        viewModel = UnlocksViewModel(scrollDataRepository, appUiModelMapper)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is loading`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.unlockStat).isEqualTo(0)
            assertThat(state.appOpens).isEmpty()
        }
    }

    @Test
    fun `unlock count is updated`() = runTest {
        val today = LocalDate.now().toString()
        val summary = DailyDeviceSummary(dateString = today, totalUnlockCount = 10)
        every { scrollDataRepository.getAllDeviceSummaries() } returns flowOf(listOf(summary))
        every { scrollDataRepository.getUsageRecordsForDateRange(any(), any()) } returns flowOf(emptyList())

        // Re-create ViewModel to observe changes after mocks are updated
        viewModel = UnlocksViewModel(scrollDataRepository, appUiModelMapper)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val initialState = awaitItem()
            assertThat(initialState.unlockStat).isEqualTo(0)
            val state = awaitItem()
            assertThat(state.unlockStat).isEqualTo(10)
        }
    }
} 