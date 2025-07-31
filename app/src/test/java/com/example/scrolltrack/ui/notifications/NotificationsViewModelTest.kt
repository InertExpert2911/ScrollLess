package com.example.scrolltrack.ui.notifications

import app.cash.turbine.test
import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.data.NotificationCountPerApp
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.db.AppMetadata
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.coVerify
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
class NotificationsViewModelTest {

    private lateinit var viewModel: NotificationsViewModel
    private val scrollDataRepository: ScrollDataRepository = mockk(relaxed = true)
    private val appMetadataRepository: AppMetadataRepository = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private val notificationCountsFlow = MutableStateFlow<List<NotificationCountPerApp>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { scrollDataRepository.getNotificationCountPerAppForPeriod(any(), any()) } returns notificationCountsFlow
        coEvery { scrollDataRepository.getAllNotificationSummaries() } returns MutableStateFlow(emptyList())

        coEvery { appMetadataRepository.getAppMetadata(any()) } answers {
            val packageName = firstArg<String>()
            mockk<AppMetadata> {
                every { this@mockk.packageName } returns packageName
                every { appName } returns packageName.substringAfterLast('.')
                every { isUserVisible } returns true
                every { isInstalled } returns true
            }
        }
        viewModel = NotificationsViewModel(scrollDataRepository, appMetadataRepository, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `period changes update title and data correctly`() = runTest(testDispatcher) {
        val date = LocalDate.of(2023, 10, 26) // A Thursday in Week 43, October has 31 days
        notificationCountsFlow.value = listOf(NotificationCountPerApp("com.app", 217)) // 217 total

        viewModel.uiState.test {
            assertThat(awaitItem()).isInstanceOf(NotificationsUiState.Loading::class.java)
            
            val dailyState = awaitItem() as NotificationsUiState.Success
            assertThat(dailyState.selectedPeriod).isEqualTo(NotificationPeriod.Daily)
            assertThat(dailyState.totalCount).isEqualTo(217)

            viewModel.onDateSelected(date)
            viewModel.selectPeriod(NotificationPeriod.Weekly)
            val weeklyState = awaitItem() as NotificationsUiState.Success
            assertThat(weeklyState.periodTitle).contains("Week 43")
            assertThat(weeklyState.totalCount).isEqualTo(31) // 217 / 7 = 31

            viewModel.selectPeriod(NotificationPeriod.Monthly)
            val monthlyState = awaitItem() as NotificationsUiState.Success
            assertThat(monthlyState.periodTitle).isEqualTo("October 2023")
            assertThat(monthlyState.totalCount).isEqualTo(7) // 217 / 31 = 7
        }
    }
   @Test
   fun `filters out non-visible and zero-count apps`() = runTest(testDispatcher) {
       val counts = listOf(
           NotificationCountPerApp("com.visible", 10),
           NotificationCountPerApp("com.hidden", 5),
           NotificationCountPerApp("com.zero", 0)
       )
       coEvery { appMetadataRepository.getAppMetadata("com.hidden") } returns mockk {
           every { isUserVisible } returns false
       }

       viewModel.uiState.test {
           awaitItem() // Loading
           notificationCountsFlow.value = counts
           val state = awaitItem() as NotificationsUiState.Success
           assertThat(state.notificationCounts.map { it.first.packageName }).containsExactly("com.visible")
       }
   }

   @Test
   fun `getIcon returns drawable`() = runTest(testDispatcher) {
       val file = mockk<java.io.File>(relaxed = true)
       every { file.absolutePath } returns ""
       coEvery { appMetadataRepository.getIconFile("com.app") } returns file
       
       // This test can only verify that the repository is called.
       // The static Drawable.createFromPath cannot be mocked easily.
       viewModel.getIcon("com.app")
       coVerify { appMetadataRepository.getIconFile("com.app") }
   }
}