package com.example.scrolltrack.db

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ScrollSessionDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var dao: ScrollSessionDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.scrollSessionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertSession_storesDataCorrectly() = runTest {
        val date = "2023-11-01"
        val session = ScrollSessionRecord(
            packageName = "com.app.test",
            scrollAmount = 1500,
            sessionStartTime = 1698825600000, // 08:00:00
            sessionEndTime = 1698829200000,   // 09:00:00
            date = date,
            sessionEndReason = "TEST"
        )
        dao.insertSession(session)

        dao.getAllSessionsFlow().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("com.app.test", list[0].packageName)
            assertEquals(1500L, list[0].scrollAmount)
        }
    }

    @Test
    fun getAggregatedScrollDataForDate_groupsAndSumsCorrectly() = runTest {
        val date = "2023-11-01"
        val session1 = ScrollSessionRecord(packageName = "com.app.one", scrollAmount = 100, sessionStartTime = 1, sessionEndTime = 2, date = date, sessionEndReason = "R1")
        val session2 = ScrollSessionRecord(packageName = "com.app.two", scrollAmount = 200, sessionStartTime = 3, sessionEndTime = 4, date = date, sessionEndReason = "R2")
        val session3 = ScrollSessionRecord(packageName = "com.app.one", scrollAmount = 50, sessionStartTime = 5, sessionEndTime = 6, date = date, sessionEndReason = "R3")
        // Session on another day
        val session4 = ScrollSessionRecord(packageName = "com.app.one", scrollAmount = 1000, sessionStartTime = 7, sessionEndTime = 8, date = "2023-11-02", sessionEndReason = "R4")

        dao.insertSession(session1)
        dao.insertSession(session2)
        dao.insertSession(session3)
        dao.insertSession(session4)

        dao.getAggregatedScrollDataForDate(date).test {
            val aggregatedData = awaitItem()

            assertEquals(2, aggregatedData.size)

            val appOneData = aggregatedData.find { it.packageName == "com.app.one" }
            val appTwoData = aggregatedData.find { it.packageName == "com.app.two" }

            assertEquals(150L, appOneData?.totalScroll) // 100 + 50
            assertEquals(200L, appTwoData?.totalScroll)

            // Verify descending order
            assertTrue(aggregatedData[0].totalScroll >= aggregatedData[1].totalScroll)
        }
    }

    @Test
    fun getTotalScrollForDate_sumsAllScrollsCorrectly() = runTest {
        val date = "2023-11-01"
        val session1 = ScrollSessionRecord(packageName = "com.app.one", scrollAmount = 100, sessionStartTime = 1, sessionEndTime = 2, date = date, sessionEndReason = "R1")
        val session2 = ScrollSessionRecord(packageName = "com.app.two", scrollAmount = 200, sessionStartTime = 3, sessionEndTime = 4, date = date, sessionEndReason = "R2")
        val session3 = ScrollSessionRecord(packageName = "com.app.one", scrollAmount = 150, sessionStartTime = 5, sessionEndTime = 6, date = date, sessionEndReason = "R3")
        // Session on another day should be ignored
        val session4 = ScrollSessionRecord(packageName = "com.app.one", scrollAmount = 1000, sessionStartTime = 7, sessionEndTime = 8, date = "2023-11-02", sessionEndReason = "R4")

        dao.insertSession(session1)
        dao.insertSession(session2)
        dao.insertSession(session3)
        dao.insertSession(session4)

        dao.getTotalScrollForDate(date).test {
            val totalScroll = awaitItem()
            assertEquals(450L, totalScroll) // 100 + 200 + 150
        }
    }

    @Test
    fun getTotalScrollForDate_returnsNullForDateWithNoScrolls() = runTest {
        dao.getTotalScrollForDate("2023-11-03").test {
            val totalScroll = awaitItem()
            assertEquals(null, totalScroll)
        }
    }
} 