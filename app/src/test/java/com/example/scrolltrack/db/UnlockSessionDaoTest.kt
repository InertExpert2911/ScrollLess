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
        var lastOpen = dao.getOpenSessionBefore("2024-01-01", 1500L)
        assertThat(lastOpen?.unlockTimestamp).isEqualTo(1000L)

        dao.insert(unlock2)
        lastOpen = dao.getOpenSessionBefore("2024-01-01", 2500L)
        assertThat(lastOpen?.unlockTimestamp).isEqualTo(2000L)

        val shouldBeNull = dao.getOpenSessionBefore("2024-01-01", 1000L)
        assertThat(shouldBeNull).isNull()
    }

    @Test
    fun `closeSession updates the correct session`() = runTest {
        val insertedId = dao.insert(unlock1)
        
        val lockTime = 1500L
        val duration = lockTime - unlock1.unlockTimestamp
        val pkg = "com.app.one"
        
        dao.closeSession(insertedId, lockTime, duration, pkg, "Intentional", "LOCKED")
        
        val closedSession = dao.getOpenSessionBefore("2024-01-01", 1600L)
        assertThat(closedSession).isNull()

        val unlockCount = dao.getUnlockCountForDateFlow("2024-01-01").first()
        assertThat(unlockCount).isEqualTo(1)
    }

    @Test
    fun `getUnlockCountForDateFlow returns correct count`() = runTest {
        dao.insert(unlock1)
        dao.insert(unlock2)
        dao.insert(unlock3)
        
        dao.getUnlockCountForDateFlow("2024-01-01").test {
            assertThat(awaitItem()).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
        dao.getUnlockCountForDateFlow("2024-01-02").test {
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

        val day1Count = dao.getUnlockCountForDateFlow("2024-01-01").first()
        val day2Count = dao.getUnlockCountForDateFlow("2024-01-02").first()

        assertThat(day1Count).isEqualTo(0)
        assertThat(day2Count).isEqualTo(1)
    }

    @Test
    fun getCompulsiveCheckCountsByPackage_returnsCorrectAggregatedCounts() = runTest {
        // Given: Insert several unlock sessions
        // App 1: 2 compulsive checks
        dao.insert(UnlockSessionRecord(unlockTimestamp = 1, dateString = "2023-01-01", firstAppPackageName = "com.app1", isCompulsive = true, unlockEventType = "TEST"))
        dao.insert(UnlockSessionRecord(unlockTimestamp = 2, dateString = "2023-01-01", firstAppPackageName = "com.app1", isCompulsive = true, unlockEventType = "TEST"))
        // App 1: 1 non-compulsive check
        dao.insert(UnlockSessionRecord(unlockTimestamp = 3, dateString = "2023-01-01", firstAppPackageName = "com.app1", isCompulsive = false, unlockEventType = "TEST"))
        // App 2: 1 compulsive check
        dao.insert(UnlockSessionRecord(unlockTimestamp = 4, dateString = "2023-01-01", firstAppPackageName = "com.app2", isCompulsive = true, unlockEventType = "TEST"))
        // App 3: No compulsive checks
        dao.insert(UnlockSessionRecord(unlockTimestamp = 5, dateString = "2023-01-01", firstAppPackageName = "com.app3", isCompulsive = false, unlockEventType = "TEST"))
        // Another date to ensure the date range filter works
        dao.insert(UnlockSessionRecord(unlockTimestamp = 6, dateString = "2023-01-02", firstAppPackageName = "com.app1", isCompulsive = true, unlockEventType = "TEST"))


        // When
        val compulsiveCounts = dao.getCompulsiveCheckCountsByPackage("2023-01-01", "2023-01-01").first()

        // Then
        assertThat(compulsiveCounts).hasSize(2) // Only app1 and app2 should be present
        assertThat(compulsiveCounts).containsExactly(
            PackageCount(packageName = "com.app1", count = 2),
            PackageCount(packageName = "com.app2", count = 1)
        ).inOrder() // The result should be ordered by count descending
    }
}
