package com.example.scrolltrack.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import app.cash.turbine.test
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class DailyInsightDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: DailyInsightDao

    private val testDate1 = "2023-10-27"
    private val testDate2 = "2023-10-28"

    private val insight1 = DailyInsight(dateString = testDate1, insightKey = "first_unlock_time", longValue = 1000L)
    private val insight2 = DailyInsight(dateString = testDate1, insightKey = "last_app_used", stringValue = "com.example.app")
    private val insight3 = DailyInsight(dateString = testDate2, insightKey = "glance_count", longValue = 5)


    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries() // Allowing queries on the main thread for testing only
            .build()
        dao = db.dailyInsightDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertInsights_and_getInsightsForDate_retrievesCorrectData() {
        runBlocking {
            // Given
            val insightsForDate1 = listOf(insight1, insight2)
            dao.insertInsights(insightsForDate1)
            dao.insertInsights(listOf(insight3))

            // When
            val retrievedInsights = dao.getInsightsForDate(testDate1)

            // Then
            assertThat(retrievedInsights).hasSize(2)
            // We can't compare directly due to auto-generated IDs.
            // Instead, we check that the content (ignoring ID) matches.
            val retrievedWithoutIds = retrievedInsights.map { it.copy(id = 0) }
            assertThat(retrievedWithoutIds).containsExactlyElementsIn(insightsForDate1)
        }
    }

    @Test
    fun getInsightsForDate_returnsEmptyList_whenNoInsightsMatchDate() {
        runBlocking {
            // Given
            dao.insertInsights(listOf(insight3)) // Insert data only for testDate2

            // When
            val retrievedInsights = dao.getInsightsForDate(testDate1) // Query for testDate1

            // Then
            assertThat(retrievedInsights).isEmpty()
        }
    }

    @Test
    fun deleteInsightsForDate_removesOnlyTheSpecifiedDate() {
        runBlocking {
            // Given
            dao.insertInsights(listOf(insight1, insight2, insight3))

            // When
            dao.deleteInsightsForDate(testDate1)

            // Then
            val insightsForDate1 = dao.getInsightsForDate(testDate1)
            val insightsForDate2 = dao.getInsightsForDate(testDate2)
            assertThat(insightsForDate1).isEmpty()
            assertThat(insightsForDate2).hasSize(1)
            // Compare the content, but use the real ID from the database for the comparison object.
            val retrievedInsight = insightsForDate2.first()
            assertThat(retrievedInsight).isEqualTo(insight3.copy(id = retrievedInsight.id))
        }
    }

    @Test
    fun getInsightsForDateAsFlow_emitsUpdatesCorrectly() {
        runBlocking {
            dao.getInsightsForDateAsFlow(testDate1).test {
                // Then: Initial emission should be empty
                assertThat(awaitItem()).isEmpty()

                // When: Insert one insight
                dao.insertInsights(listOf(insight1))
                // Then: It should emit the list with one insight. Compare content by copying the ID.
                var emitted = awaitItem()
                assertThat(emitted).hasSize(1)
                assertThat(emitted.first()).isEqualTo(insight1.copy(id = emitted.first().id))

                // When: Insert a second insight for the same date
                dao.insertInsights(listOf(insight2))
                // Then: It should emit the updated list.
                emitted = awaitItem()
                assertThat(emitted).hasSize(2)
                val retrievedWithoutIds = emitted.map { it.copy(id = 0) }
                assertThat(retrievedWithoutIds).containsExactly(insight1, insight2)

                // When: Delete the insights for the date
                dao.deleteInsightsForDate(testDate1)
                // Then: It should emit an empty list
                assertThat(awaitItem()).isEmpty()

                // Cancel the flow
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
}
