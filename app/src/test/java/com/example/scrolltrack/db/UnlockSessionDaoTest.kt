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
class UnlockSessionDaoTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var dao: UnlockSessionDao

    private val unlock1 = UnlockSessionRecord(unlockTimestamp = 1000L, dateString = "2024-01-01", unlockEventType = "USER_PRESENT")
    private val unlock2 = UnlockSessionRecord(unlockTimestamp = 2000L, dateString = "2024-01-01", unlockEventType = "USER_PRESENT")
    private val unlock3 = UnlockSessionRecord(unlockTimestamp = 3000L, dateString = "2024-01-02", unlockEventType = "USER_PRESENT")

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.unlockSessionDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `insert and getOpenSessionBefore work correctly`() = runTest {
        dao.insert(unlock1)
        var lastOpen = dao.getOpenSessionBefore(1500L)
        assertThat(lastOpen?.unlockTimestamp).isEqualTo(1000L)

        dao.insert(unlock2)
        lastOpen = dao.getOpenSessionBefore(2500L)
        assertThat(lastOpen?.unlockTimestamp).isEqualTo(2000L)

        val shouldBeNull = dao.getOpenSessionBefore(1000L)
        assertThat(shouldBeNull).isNull()
    }

    @Test
    fun `closeSession updates the correct session`() = runTest {
        dao.insert(unlock1)
        val inserted = dao.getOpenSessionBefore(1100L)
        assertThat(inserted).isNotNull()
        
        val lockTime = 1500L
        val duration = lockTime - inserted!!.unlockTimestamp
        val pkg = "com.app.one"
        
        dao.closeSession(inserted.id, lockTime, duration, pkg, null)
        
        // Check that it is indeed closed and not returned as an open session
        val closedSession = dao.getOpenSessionBefore(1600L)
        assertThat(closedSession).isNull()

        val unlockCount = dao.getUnlockCountForDate("2024-01-01").first()
        assertThat(unlockCount).isEqualTo(1)
    }

    @Test
    fun `getUnlockCountForDate returns correct count`() = runTest {
        dao.insert(unlock1)
        dao.insert(unlock2)
        dao.insert(unlock3)
        
        dao.getUnlockCountForDate("2024-01-01").test {
            assertThat(awaitItem()).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
        dao.getUnlockCountForDate("2024-01-02").test {
            assertThat(awaitItem()).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteSessionsForDate removes correct sessions`() = runTest {
        dao.insert(unlock1)
        dao.insert(unlock2)
        dao.insert(unlock3)

        dao.deleteSessionsForDate("2024-01-01")

        val day1Count = dao.getUnlockCountForDate("2024-01-01").first()
        val day2Count = dao.getUnlockCountForDate("2024-01-02").first()

        assertThat(day1Count).isEqualTo(0)
        assertThat(day2Count).isEqualTo(1)
    }
    
    @Test
    fun `getUnlockTimestampsForDate returns correct timestamps`() = runTest {
        dao.insert(unlock1)
        dao.insert(unlock2)
        dao.insert(unlock3)

        val timestamps = dao.getUnlockTimestampsForDate("2024-01-01")
        assertThat(timestamps).hasSize(2)
        assertThat(timestamps).containsExactly(1000L, 2000L)
    }
} 