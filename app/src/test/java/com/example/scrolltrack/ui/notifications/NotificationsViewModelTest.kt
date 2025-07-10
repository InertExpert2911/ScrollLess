package com.example.scrolltrack.ui.notifications

import app.cash.turbine.test
import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.data.NotificationCountPerApp
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.db.AppMetadata
import com.example.scrolltrack.util.DateUtil
import io.mockk.coEvery
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.LocalDate

@ExperimentalCoroutinesApi
class NotificationsViewModelTest {

    private lateinit var viewModel: NotificationsViewModel
    private val scrollDataRepository: ScrollDataRepository = mockk(relaxed = true)
    private val appMetadataRepository: AppMetadataRepository = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Provide a default mock for the initial weekly load to avoid crashes
        val today = DateUtil.getCurrentLocalDateString()
        val startOfWeek = DateUtil.getStartOfWeek(LocalDate.parse(today)).toString()
        val endOfWeek = DateUtil.getEndOfWeek(LocalDate.parse(today)).toString()
        coEvery { scrollDataRepository.getNotificationCountPerAppForPeriod(startOfWeek, endOfWeek) } returns flowOf(emptyList())

        viewModel = NotificationsViewModel(scrollDataRepository, appMetadataRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when viewmodel initializes, it loads weekly data by default`() = runTest {
        val today = DateUtil.getCurrentLocalDateString()
        val startOfWeek = DateUtil.getStartOfWeek(LocalDate.parse(today)).toString()
        val endOfWeek = DateUtil.getEndOfWeek(LocalDate.parse(today)).toString()
        val counts = listOf(NotificationCountPerApp("app1", 10))
        val metadata = AppMetadata("app1", "App One", "1.0", 1, false, true, true, 0, true, null, 0, 0)

        coEvery { scrollDataRepository.getNotificationCountPerAppForPeriod(startOfWeek, endOfWeek) } returns flowOf(counts)
        coEvery { appMetadataRepository.getAppMetadata("app1") } returns metadata

        // Re-create the ViewModel to pick up the new mocks for this specific test
        viewModel = NotificationsViewModel(scrollDataRepository, appMetadataRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            // Expect the initial loading state, then the success state
            assertEquals(NotificationsUiState.Loading, awaitItem())
            val state = awaitItem()
            assertTrue(state is NotificationsUiState.Success)
            val successState = state as NotificationsUiState.Success
            assertEquals(NotificationPeriod.Weekly, successState.selectedPeriod)
            assertEquals(1, successState.notificationCounts.size)
            assertEquals(metadata, successState.notificationCounts[0].first)
            assertEquals(10, successState.notificationCounts[0].second)
        }
    }

    @Test
    fun `selecting daily period loads daily data`() = runTest {
        val today = DateUtil.getCurrentLocalDateString()
        val counts = listOf(NotificationCountPerApp("app2", 5))
        val metadata = AppMetadata("app2", "App Two", "1.0", 1, false, true, true, 0, true, null, 0, 0)

        coEvery { scrollDataRepository.getNotificationCountPerAppForPeriod(today, today) } returns flowOf(counts)
        coEvery { appMetadataRepository.getAppMetadata("app2") } returns metadata

        viewModel.selectPeriod(NotificationPeriod.Daily)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            // The flow will emit: Initial Loading -> Daily Success
            assertEquals(NotificationsUiState.Loading, awaitItem()) // Initial state

            val dailyState = awaitItem() // Result of selecting daily
            assertTrue(dailyState is NotificationsUiState.Success)
            val successState = dailyState as NotificationsUiState.Success
            assertEquals(NotificationPeriod.Daily, successState.selectedPeriod)
            assertEquals(1, successState.notificationCounts.size)
            assertEquals("App Two", successState.notificationCounts[0].first.appName)
        }
    }
} 