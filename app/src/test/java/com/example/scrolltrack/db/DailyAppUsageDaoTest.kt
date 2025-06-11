package com.example.scrolltrack.db

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class DailyAppUsageDaoTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var dao: DailyAppUsageDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries() // Only for testing
            .build()
        dao = database.dailyAppUsageDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `insertOrUpdateUsage and getSpecificAppUsageForDate work correctly`() = runTest {
        // ARRANGE
        val record1 = DailyAppUsageRecord(packageName = "com.app.one", dateString = "2024-01-01", usageTimeMillis = 1000L, activeTimeMillis = 100L, lastUpdatedTimestamp = 0L)
        
        // ACT
        dao.insertOrUpdateUsage(record1)
        val retrieved = dao.getSpecificAppUsageForDate("com.app.one", "2024-01-01")

        // ASSERT
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.packageName).isEqualTo("com.app.one")
        assertThat(retrieved?.usageTimeMillis).isEqualTo(1000L)

        // ACT (Update)
        val record2 = DailyAppUsageRecord(id = retrieved!!.id, packageName = "com.app.one", dateString = "2024-01-01", usageTimeMillis = 2000L, activeTimeMillis = 200L, lastUpdatedTimestamp = 1L)
        dao.insertOrUpdateUsage(record2)
        val updated = dao.getSpecificAppUsageForDate("com.app.one", "2024-01-01")

        // ASSERT (Update)
        assertThat(updated).isNotNull()
        assertThat(updated?.usageTimeMillis).isEqualTo(2000L)
    }

    @Test
    fun `insertAllUsage replaces existing records`() = runTest {
        // ARRANGE
        val initialRecord = DailyAppUsageRecord(packageName = "com.app.one", dateString = "2024-01-01", usageTimeMillis = 1000L, activeTimeMillis = 100L, lastUpdatedTimestamp = 0L)
        dao.insertOrUpdateUsage(initialRecord)

        val newRecords = listOf(
            DailyAppUsageRecord(packageName = "com.app.one", dateString = "2024-01-01", usageTimeMillis = 5000L, activeTimeMillis = 500L, lastUpdatedTimestamp = 1L), // Overwrite
            DailyAppUsageRecord(packageName = "com.app.two", dateString = "2024-01-01", usageTimeMillis = 3000L, activeTimeMillis = 300L, lastUpdatedTimestamp = 1L)  // New
        )

        // ACT
        dao.insertAllUsage(newRecords)
        val allUsage = dao.getUsageForDate("2024-01-01").first()

        // ASSERT
        assertThat(allUsage).hasSize(2)
        val appOne = allUsage.find { it.packageName == "com.app.one" }
        assertThat(appOne?.usageTimeMillis).isEqualTo(5000L)
    }

    @Test
    fun `getUsageForDate returns sorted data for a specific date`() = runTest {
        // ARRANGE
        val records = listOf(
            DailyAppUsageRecord(packageName = "com.app.one", dateString = "2024-01-01", usageTimeMillis = 1000L, activeTimeMillis = 100L, lastUpdatedTimestamp = 0L),
            DailyAppUsageRecord(packageName = "com.app.two", dateString = "2024-01-01", usageTimeMillis = 3000L, activeTimeMillis = 300L, lastUpdatedTimestamp = 0L),
            DailyAppUsageRecord(packageName = "com.app.three", dateString = "2024-01-02", usageTimeMillis = 2000L, activeTimeMillis = 200L, lastUpdatedTimestamp = 0L)
        )
        dao.insertAllUsage(records)

        // ACT & ASSERT
        dao.getUsageForDate("2024-01-01").test {
            val usageList = awaitItem()
            assertThat(usageList).hasSize(2)
            assertThat(usageList.map { it.packageName }).containsExactly("com.app.two", "com.app.one").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getTotalUsageTimeMillisForDate sums usage correctly`() = runTest {
        // ARRANGE
        val records = listOf(
            DailyAppUsageRecord(packageName = "com.app.one", dateString = "2024-01-01", usageTimeMillis = 1000L, activeTimeMillis = 100L, lastUpdatedTimestamp = 0L),
            DailyAppUsageRecord(packageName = "com.app.two", dateString = "2024-01-01", usageTimeMillis = 3000L, activeTimeMillis = 300L, lastUpdatedTimestamp = 0L),
            DailyAppUsageRecord(packageName = "com.app.three", dateString = "2024-01-02", usageTimeMillis = 5000L, activeTimeMillis = 500L, lastUpdatedTimestamp = 0L)
        )
        dao.insertAllUsage(records)

        // ACT & ASSERT
        dao.getTotalUsageTimeMillisForDate("2024-01-01").test {
            assertThat(awaitItem()).isEqualTo(4000L)
            cancelAndIgnoreRemainingEvents()
        }
        dao.getTotalUsageTimeMillisForDate("2024-01-03").test {
            assertThat(awaitItem()).isNull() // No records, so SUM is null
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getUsageRecordsForDateRange returns correct records`() = runTest {
        // ARRANGE
        val records = listOf(
            DailyAppUsageRecord(packageName = "com.app.one", dateString = "2024-01-01", usageTimeMillis = 100L, activeTimeMillis = 10L, lastUpdatedTimestamp = 0L),
            DailyAppUsageRecord(packageName = "com.app.two", dateString = "2024-01-02", usageTimeMillis = 200L, activeTimeMillis = 20L, lastUpdatedTimestamp = 0L),
            DailyAppUsageRecord(packageName = "com.app.three", dateString = "2024-01-03", usageTimeMillis = 300L, activeTimeMillis = 30L, lastUpdatedTimestamp = 0L),
            DailyAppUsageRecord(packageName = "com.app.four", dateString = "2024-01-04", usageTimeMillis = 400L, activeTimeMillis = 40L, lastUpdatedTimestamp = 0L)
        )
        dao.insertAllUsage(records)

        // ACT
        val range = dao.getUsageRecordsForDateRange("2024-01-02", "2024-01-03").first()

        // ASSERT
        assertThat(range).hasSize(2)
        assertThat(range.map { it.packageName }).containsExactly("com.app.two", "com.app.three")
    }

    @Test
    fun `getUsageForPackageAndDates returns specific records`() = runTest {
        // ARRANGE
        val records = listOf(
            DailyAppUsageRecord(packageName = "com.app.one", dateString = "2024-01-01", usageTimeMillis = 100L, activeTimeMillis = 10L, lastUpdatedTimestamp = 0L),
            DailyAppUsageRecord(packageName = "com.app.one", dateString = "2024-01-02", usageTimeMillis = 200L, activeTimeMillis = 20L, lastUpdatedTimestamp = 0L),
            DailyAppUsageRecord(packageName = "com.app.one", dateString = "2024-01-03", usageTimeMillis = 300L, activeTimeMillis = 30L, lastUpdatedTimestamp = 0L),
            DailyAppUsageRecord(packageName = "com.app.two", dateString = "2024-01-01", usageTimeMillis = 50L, activeTimeMillis = 5L, lastUpdatedTimestamp = 0L)
        )
        dao.insertAllUsage(records)

        // ACT
        val result = dao.getUsageForPackageAndDates("com.app.one", listOf("2024-01-01", "2024-01-03"))

        // ASSERT
        assertThat(result).hasSize(2)
        assertThat(result.map { it.dateString }).containsExactly("2024-01-01", "2024-01-03")
    }

    @Test
    fun `deleteOldUsageData works correctly`() = runTest {
        // ARRANGE
        val records = listOf(
            DailyAppUsageRecord(packageName = "com.app.one", dateString = "2024-01-01", usageTimeMillis = 100L, activeTimeMillis = 10L, lastUpdatedTimestamp = 1000L), // To be deleted
            DailyAppUsageRecord(packageName = "com.app.two", dateString = "2024-01-02", usageTimeMillis = 200L, activeTimeMillis = 20L, lastUpdatedTimestamp = 2000L), // To be deleted
            DailyAppUsageRecord(packageName = "com.app.three", dateString = "2024-01-03", usageTimeMillis = 300L, activeTimeMillis = 30L, lastUpdatedTimestamp = 3000L) // Stays
        )
        dao.insertAllUsage(records)
        
        // ACT
        val deletedRows = dao.deleteOldUsageData(2500)
        val remaining = dao.getUsageRecordsForDateRange("2024-01-01", "2024-01-03").first()

        // ASSERT
        assertThat(deletedRows).isEqualTo(2)
        assertThat(remaining).hasSize(1)
        assertThat(remaining.first().packageName).isEqualTo("com.app.three")
    }

    @Test
    fun `deleteUsageForDate removes correct records`() = runTest {
        // ARRANGE
        val records = listOf(
            DailyAppUsageRecord(packageName = "com.app.one", dateString = "2024-01-01", usageTimeMillis = 100L, activeTimeMillis = 10L, lastUpdatedTimestamp = 0L),
            DailyAppUsageRecord(packageName = "com.app.two", dateString = "2024-01-01", usageTimeMillis = 200L, activeTimeMillis = 20L, lastUpdatedTimestamp = 0L),
            DailyAppUsageRecord(packageName = "com.app.three", dateString = "2024-01-02", usageTimeMillis = 300L, activeTimeMillis = 30L, lastUpdatedTimestamp = 0L)
        )
        dao.insertAllUsage(records)

        // ACT
        dao.deleteUsageForDate("2024-01-01")
        val forDay1 = dao.getUsageForDate("2024-01-01").first()
        val forDay2 = dao.getUsageForDate("2024-01-02").first()

        // ASSERT
        assertThat(forDay1).isEmpty()
        assertThat(forDay2).hasSize(1)
    }
    
    @Test
    fun `clearAllUsageData removes all records`() = runTest {
        // ARRANGE
        val records = listOf(
            DailyAppUsageRecord(packageName = "com.app.one", dateString = "2024-01-01", usageTimeMillis = 100L, activeTimeMillis = 10L, lastUpdatedTimestamp = 0L),
            DailyAppUsageRecord(packageName = "com.app.three", dateString = "2024-01-02", usageTimeMillis = 300L, activeTimeMillis = 30L, lastUpdatedTimestamp = 0L)
        )
        dao.insertAllUsage(records)

        // ACT
        dao.clearAllUsageData()
        val remaining = dao.getUsageForDate("2024-01-01").first()

        // ASSERT
        assertThat(remaining).isEmpty()
    }
    
    @Test
    fun `getUsageCountForDateString counts correctly`() = runTest {
        // ARRANGE
        val records = listOf(
            DailyAppUsageRecord(packageName = "com.app.one", dateString = "2024-01-01", usageTimeMillis = 100L, activeTimeMillis = 10L, lastUpdatedTimestamp = 0L),
            DailyAppUsageRecord(packageName = "com.app.two", dateString = "2024-01-01", usageTimeMillis = 200L, activeTimeMillis = 20L, lastUpdatedTimestamp = 0L)
        )
        dao.insertAllUsage(records)

        // ACT
        val count1 = dao.getUsageCountForDateString("2024-01-01")
        val count2 = dao.getUsageCountForDateString("2024-01-02")
        
        // ASSERT
        assertThat(count1).isEqualTo(2)
        assertThat(count2).isEqualTo(0)
    }
}