package com.example.scrolltrack.db

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class DailyDeviceSummaryDaoTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var dao: DailyDeviceSummaryDao

    private val summary1 = DailyDeviceSummary(dateString = "2024-01-01", totalUsageTimeMillis = 1000L, totalUnlockCount = 10, totalNotificationCount = 100)
    private val summary2 = DailyDeviceSummary(dateString = "2024-01-02", totalUsageTimeMillis = 2000L, totalUnlockCount = 20, totalNotificationCount = 200)
    private val summary3 = DailyDeviceSummary(dateString = "2024-01-03", totalUsageTimeMillis = 3000L, totalUnlockCount = 30, totalNotificationCount = 300)

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.dailyDeviceSummaryDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `insertOrUpdate and getSummaryForDate work correctly`() = runTest {
        dao.insertOrUpdate(summary1)

        dao.getSummaryForDate("2024-01-01").test {
            assertThat(awaitItem()).isEqualTo(summary1)
            cancelAndIgnoreRemainingEvents()
        }

        val updatedSummary1 = summary1.copy(totalUsageTimeMillis = 9999L)
        dao.insertOrUpdate(updatedSummary1)

        dao.getSummaryForDate("2024-01-01").test {
            val item = awaitItem()
            assertThat(item?.totalUsageTimeMillis).isEqualTo(9999L)
            cancelAndIgnoreRemainingEvents()
        }
    }
    
    @Test
    fun `getSummaryForDate returns null for non-existent date`() = runTest {
        dao.getSummaryForDate("2024-01-01").test {
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getUnlockCountForDate returns correct count`() = runTest {
        dao.insertOrUpdate(summary1)
        dao.getUnlockCountForDate("2024-01-01").test {
            assertThat(awaitItem()).isEqualTo(10)
            cancelAndIgnoreRemainingEvents()
        }
    }
    
    @Test
    fun `getUnlockCountForDate returns null for non-existent date`() = runTest {
        dao.getUnlockCountForDate("2024-01-01").test {
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getNotificationCountForDate returns correct count`() = runTest {
        dao.insertOrUpdate(summary1)
        dao.getNotificationCountForDate("2024-01-01").test {
            assertThat(awaitItem()).isEqualTo(100)
            cancelAndIgnoreRemainingEvents()
        }
    }
    
    @Test
    fun `getNotificationCountForDate returns null for non-existent date`() = runTest {
        dao.getNotificationCountForDate("2024-01-01").test {
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAllSummaries returns all summaries sorted by date`() = runTest {
        dao.insertOrUpdate(summary3)
        dao.insertOrUpdate(summary1)
        dao.insertOrUpdate(summary2)

        dao.getAllSummaries().test {
            val summaries = awaitItem()
            assertThat(summaries).hasSize(3)
            assertThat(summaries.map { it.dateString }).containsExactly("2024-01-01", "2024-01-02", "2024-01-03").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteSummaryForDate removes correct summary`() = runTest {
        dao.insertOrUpdate(summary1)
        dao.insertOrUpdate(summary2)

        dao.deleteSummaryForDate("2024-01-01")

        val allSummaries = dao.getAllSummaries().first()
        assertThat(allSummaries).hasSize(1)
        assertThat(allSummaries.first()).isEqualTo(summary2)
    }

    @Test
    fun `clearAll removes all summaries`() = runTest {
        dao.insertOrUpdate(summary1)
        dao.insertOrUpdate(summary2)

        dao.clearAll()

        val allSummaries = dao.getAllSummaries().first()
        assertThat(allSummaries).isEmpty()
    }
} 