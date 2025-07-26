package com.example.scrolltrack.ui.insights

import com.example.scrolltrack.data.AppMetadataRepository
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.db.DailyInsight
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class InsightsViewModelTest {

    private lateinit var viewModel: InsightsViewModel
    private lateinit var scrollDataRepository: ScrollDataRepository
    private lateinit var appMetadataRepository: AppMetadataRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        scrollDataRepository = mockk(relaxed = true)
        appMetadataRepository = mockk(relaxed = true)
        viewModel = InsightsViewModel(scrollDataRepository, appMetadataRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test conditional insights are not included when condition is not met`() = runTest {
        val insights = listOf(
            DailyInsight(0, "2023-10-26", "top_compulsive_app", "com.example.app", 2)
        )
        coEvery { scrollDataRepository.getInsightsForDate(any()) } returns flowOf(insights)

        val insightCards = viewModel.insightCards.first()

        assertThat(insightCards).isEmpty()
    }

    @Test
    fun `test empty or missing data`() = runTest {
        coEvery { scrollDataRepository.getInsightsForDate(any()) } returns flowOf(emptyList())

        val insightCards = viewModel.insightCards.first()
        val dailyInsights = viewModel.dailyInsights.first()

        assertThat(insightCards).isEmpty()
        assertThat(dailyInsights.glanceCount).isEqualTo(0)
    }
}