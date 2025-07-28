package com.example.scrolltrack.ui.insights

import app.cash.turbine.test
import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.db.AppMetadata
import com.example.scrolltrack.db.DailyInsight
import com.example.scrolltrack.util.DateUtil
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
import java.io.File

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class InsightsViewModelTest {

    private lateinit var viewModel: InsightsViewModel
    private val scrollDataRepository: ScrollDataRepository = mockk()
    private val appMetadataRepository: AppMetadataRepository = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private val todaysInsightsFlow = MutableStateFlow<List<DailyInsight>>(emptyList())
    private val yesterdaysInsightsFlow = MutableStateFlow<List<DailyInsight>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val todayString = DateUtil.getCurrentLocalDateString()
        val yesterdayString = DateUtil.getPastDateString(1)
        coEvery { scrollDataRepository.getInsightsForDate(todayString) } returns todaysInsightsFlow
        coEvery { scrollDataRepository.getInsightsForDate(yesterdayString) } returns yesterdaysInsightsFlow
        coEvery { scrollDataRepository.getUnlockSessionsForDateRange(any(), any()) } returns kotlinx.coroutines.flow.flowOf(emptyList())

        coEvery { appMetadataRepository.getAppMetadata(any()) } answers {
            val packageName = firstArg<String>()
            AppMetadata(packageName, packageName.substringAfterLast('.').capitalize(), isUserVisible = true, isSystemApp = false, userHidesOverride = null, installTimestamp = 0L, lastUpdateTimestamp = 0L, versionName = "1.0", versionCode = 1L)
        }
        coEvery { appMetadataRepository.getIconFile(any()) } returns null

        viewModel = InsightsViewModel(scrollDataRepository, appMetadataRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createInsight(key: String, sValue: String? = null, lValue: Long? = null, date: String = "2023-10-26"): DailyInsight {
        return DailyInsight(dateString = date, insightKey = key, stringValue = sValue, longValue = lValue)
    }

    @Test
    fun `dailyInsights are mapped correctly with correct time format`() = runTest {
        val insights = listOf(
            createInsight("glance_count", lValue = 10),
            createInsight("first_unlock_time", lValue = 1698298200000), // 8:40 AM in test timezone
            createInsight("first_app_used", sValue = "com.example.app", lValue = 1698298200000)
        )

        viewModel.dailyInsights.test {
            todaysInsightsFlow.value = insights
            val dailyInsights = awaitItem()
            assertThat(dailyInsights.glanceCount).isEqualTo(10)
            assertThat(dailyInsights.firstUnlock).isEqualTo("8:40 AM")
            assertThat(dailyInsights.firstUsedApp).isEqualTo("App at 8:40 AM")
        }
    }

    @Test
    fun `insight cards are created with correct content`() = runTest {
        val todaysInsights = listOf(
            createInsight("first_app_used", sValue = "com.example.first", lValue = 1698298200000), // 8:40 AM
            createInsight("top_compulsive_app", sValue = "com.example.compulsive", lValue = 5),
            createInsight("busiest_unlock_hour", lValue = 14) // Afternoon
        )
        val yesterdaysInsights = listOf(
            createInsight("last_app_used", sValue = "com.example.last", lValue = 1698276600000, date = "2023-10-25") // Yesterday
        )

        viewModel.insightCards.test {
            todaysInsightsFlow.value = todaysInsights
            yesterdaysInsightsFlow.value = yesterdaysInsights
            
            val insightCards = awaitItem()
            assertThat(insightCards).hasSize(3)

            val firstAppCard = insightCards.find { it is InsightCardUiModel.FirstApp } as InsightCardUiModel.FirstApp
            assertThat(firstAppCard.appName).isEqualTo("First")
            assertThat(firstAppCard.time).isEqualTo("8:40 AM")
        }
    }
    
    @Test
    fun `conditional insight cards are not created when conditions are not met`() = runTest {
        val insights = listOf(
            createInsight("top_compulsive_app", sValue = "com.example.compulsive", lValue = 2), // Below threshold
            createInsight("top_notification_unlock_app", sValue = "com.example.notif", lValue = 5), // Below threshold
            createInsight("glance_count", lValue = 50),
            createInsight("meaningful_unlock_count", lValue = 50)
        )

        viewModel.insightCards.test {
            todaysInsightsFlow.value = insights
            val cards = awaitItem()
            assertThat(cards.any { it is InsightCardUiModel.CompulsiveCheck }).isFalse()
            assertThat(cards.any { it is InsightCardUiModel.NotificationLeader }).isFalse()
        }
    }
}