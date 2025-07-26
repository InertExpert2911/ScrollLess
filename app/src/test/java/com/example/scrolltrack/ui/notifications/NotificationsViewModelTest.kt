package com.example.scrolltrack.ui.notifications

import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.data.NotificationCountPerApp
import com.example.scrolltrack.data.ScrollDataRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
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
class NotificationsViewModelTest {

    private lateinit var viewModel: NotificationsViewModel
    private lateinit var scrollDataRepository: ScrollDataRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        scrollDataRepository = mockk(relaxed = true)
        val appMetadataRepository: AppMetadataRepository = mockk(relaxed = true)
        viewModel = NotificationsViewModel(scrollDataRepository, appMetadataRepository, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test date range calculation`() = runTest(testDispatcher) {
        val date = LocalDate.of(2023, 10, 26)
        viewModel.onDateSelected(date)
        viewModel.selectPeriod(NotificationPeriod.Weekly)
        advanceUntilIdle()

        val uiState = viewModel.uiState.value as NotificationsUiState.Success
        assertThat(uiState.periodTitle).contains("Week 43")
    }

    @Test
    fun `test data averaging`() = runTest(testDispatcher) {
        coEvery { scrollDataRepository.getAllNotificationSummaries() } returns flowOf(emptyList())
        coEvery { scrollDataRepository.getNotificationCountPerAppForPeriod(any(), any()) } returns flowOf(
            listOf(
                NotificationCountPerApp("com.example.app", 30)
            )
        )
        viewModel.onDateSelected(LocalDate.of(2023, 10, 26))
        viewModel.selectPeriod(NotificationPeriod.Weekly)
        advanceUntilIdle()

        val uiState = viewModel.uiState.value as NotificationsUiState.Success
        assertThat(uiState.totalCount).isEqualTo(4) // (10 + 20) / 7
    }
}