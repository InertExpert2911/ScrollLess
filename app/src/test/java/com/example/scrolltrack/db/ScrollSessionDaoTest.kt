package com.example.scrolltrack.db

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.example.scrolltrack.data.AppScrollData
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
class ScrollSessionDaoTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var dao: ScrollSessionDao

    private val session1 = ScrollSessionRecord(packageName = "com.app.one", scrollAmount = 100, scrollAmountX = 50, scrollAmountY = 50, dataType = "MEASURED", sessionStartTime = 1000L, sessionEndTime = 2000L, dateString = "2024-01-01", sessionEndReason = "END")
    private val session2 = ScrollSessionRecord(packageName = "com.app.one", scrollAmount = 200, scrollAmountX = 0, scrollAmountY = 200, dataType = "INFERRED", sessionStartTime = 3000L, sessionEndTime = 4000L, dateString = "2024-01-01", sessionEndReason = "END")
    private val session3 = ScrollSessionRecord(packageName = "com.app.two", scrollAmount = 300, scrollAmountX = 300, scrollAmountY = 0, dataType = "MEASURED", sessionStartTime = 5000L, sessionEndTime = 6000L, dateString = "2024-01-01", sessionEndReason = "END")
    private val session4 = ScrollSessionRecord(packageName = "com.app.one", scrollAmount = 400, scrollAmountX = 100, scrollAmountY = 300, dataType = "MEASURED", sessionStartTime = 7000L, sessionEndTime = 8000L, dateString = "2024-01-02", sessionEndReason = "END")

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.scrollSessionDao()
        runTest { dao.insertSessions(listOf(session1, session2, session3, session4)) }
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `deleteSessionsForDate removes correct sessions`() = runTest {
        dao.deleteSessionsForDate("2024-01-01")
        val totalScroll = dao.getTotalScrollForDate("2024-01-01").first()
        val remainingScroll = dao.getTotalScrollForDate("2024-01-02").first()
        assertThat(totalScroll).isNull()
        assertThat(remainingScroll).isEqualTo(400L)
    }

    @Test
    fun `getTotalScrollForDate sums scroll correctly`() = runTest {
        dao.getTotalScrollForDate("2024-01-01").test {
            assertThat(awaitItem()).isEqualTo(100 + 200 + 300)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getScrollDataForDate groups and sums correctly`() = runTest {
        dao.getScrollDataForDate("2024-01-01").test {
            val data = awaitItem()
            assertThat(data).hasSize(3)
            assertThat(data).containsExactly(
                AppScrollData("com.app.one", 100, 50, 50, "MEASURED"),
                AppScrollData("com.app.one", 200, 0, 200, "INFERRED"),
                AppScrollData("com.app.two", 300, 300, 0, "MEASURED")
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAggregatedScrollForPackageAndDates returns correct data`() = runTest {
        val result = dao.getAggregatedScrollForPackageAndDates("com.app.one", listOf("2024-01-01", "2024-01-02"))
        assertThat(result).hasSize(2)
        val day1Data = result.find { it.dateString == "2024-01-01" }
        val day2Data = result.find { it.dateString == "2024-01-02" }

        assertThat(day1Data?.totalScroll).isEqualTo(300L) // 100 + 200
        assertThat(day1Data?.totalScrollX).isEqualTo(50L)
        assertThat(day1Data?.totalScrollY).isEqualTo(250L)

        assertThat(day2Data?.totalScroll).isEqualTo(400L)
        assertThat(day2Data?.totalScrollX).isEqualTo(100L)
        assertThat(day2Data?.totalScrollY).isEqualTo(300L)
    }

    @Test
    fun `getAllDistinctScrollDateStrings returns correct dates`() = runTest {
        dao.getAllDistinctScrollDateStrings().test {
            val dates = awaitItem()
            assertThat(dates).hasSize(2)
            assertThat(dates).containsExactly("2024-01-02", "2024-01-01") // sorted desc
            cancelAndIgnoreRemainingEvents()
        }
    }
} 