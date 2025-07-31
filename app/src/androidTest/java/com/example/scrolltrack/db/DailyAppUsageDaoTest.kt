package com.example.scrolltrack.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.scrolltrack.util.DateUtil
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class DailyAppUsageDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: DailyAppUsageDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java).build()
        dao = db.dailyAppUsageDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun getUsageForAppsOnDate_returnsCorrectRecords() = runBlocking {
        // Arrange
        val todayString = DateUtil.getCurrentLocalDateString()
        val yesterdayString = DateUtil.getPastDateString(1)

        val app1 = DailyAppUsageRecord(dateString = todayString, packageName = "app1", usageTimeMillis = 100, appOpenCount = 1)
        val app2 = DailyAppUsageRecord(dateString = todayString, packageName = "app2", usageTimeMillis = 200, appOpenCount = 1)
        val app3 = DailyAppUsageRecord(dateString = todayString, packageName = "app3", usageTimeMillis = 300, appOpenCount = 1)
        val app4Yesterday = DailyAppUsageRecord(dateString = yesterdayString, packageName = "app1", usageTimeMillis = 50, appOpenCount = 1)
        dao.insertAllUsage(listOf(app1, app2, app3, app4Yesterday))

        // Act
        val result = dao.getUsageForAppsOnDate(listOf("app1", "app3"), todayString).first()

        // Assert
        assertThat(result).hasSize(2)
        assertThat(result.map { it.packageName }).containsExactly("app1", "app3")
        assertThat(result.find { it.packageName == "app1" }?.usageTimeMillis).isEqualTo(100)
        assertThat(result.find { it.packageName == "app3" }?.usageTimeMillis).isEqualTo(300)
    }
}
@Test
    fun `insertOrUpdateUsage - inserts new record and updates existing`() = runBlocking {
        val record1 = DailyAppUsageRecord(dateString = "2024-01-01", packageName = "app1", usageTimeMillis = 100)
        dao.insertOrUpdateUsage(record1)
        var records = dao.getUsageForDate("2024-01-01").first()
        assertThat(records).hasSize(1)
        assertThat(records.first().usageTimeMillis).isEqualTo(100)

        val updatedRecord1 = DailyAppUsageRecord(dateString = "2024-01-01", packageName = "app1", usageTimeMillis = 200)
        dao.insertOrUpdateUsage(updatedRecord1)
        records = dao.getUsageForDate("2024-01-01").first()
        assertThat(records).hasSize(1)
        assertThat(records.first().usageTimeMillis).isEqualTo(200)
    }

    @Test
    fun `insertAllUsage - inserts multiple records`() = runBlocking {
        val records = listOf(
            DailyAppUsageRecord(dateString = "2024-01-01", packageName = "app1", usageTimeMillis = 100),
            DailyAppUsageRecord(dateString = "2024-01-01", packageName = "app2", usageTimeMillis = 200)
        )
        dao.insertAllUsage(records)
        val fetchedRecords = dao.getUsageForDate("2024-01-01").first()
        assertThat(fetchedRecords).hasSize(2)
    }
@Test
    fun `query methods return correct data`() = runBlocking {
        val records = listOf(
            DailyAppUsageRecord(dateString = "2024-01-01", packageName = "app1", usageTimeMillis = 100),
            DailyAppUsageRecord(dateString = "2024-01-01", packageName = "app2", usageTimeMillis = 200),
            DailyAppUsageRecord(dateString = "2024-01-02", packageName = "app1", usageTimeMillis = 300),
            DailyAppUsageRecord(dateString = "2024-01-03", packageName = "app3", usageTimeMillis = 400)
        )
        dao.insertAllUsage(records)

        // getUsageForDate
        val usageForDate = dao.getUsageForDate("2024-01-01").first()
        assertThat(usageForDate).hasSize(2)
        assertThat(usageForDate.map { it.packageName }).containsExactly("app1", "app2")

        // getSpecificAppUsageForDate
        val specificUsage = dao.getSpecificAppUsageForDate("app1", "2024-01-02").first()
        assertThat(specificUsage).isNotNull()
        assertThat(specificUsage?.usageTimeMillis).isEqualTo(300)

        // getTotalUsageTimeMillisForDate
        val totalUsage = dao.getTotalUsageTimeMillisForDate("2024-01-01").first()
        assertThat(totalUsage).isEqualTo(300)

        // getUsageRecordsForDateRange
        val rangeUsage = dao.getUsageRecordsForDateRange("2024-01-01", "2024-01-02").first()
        assertThat(rangeUsage).hasSize(3)

        // getUsageForPackageAndDates
        val packageUsage = dao.getUsageForPackageAndDates("app1", listOf("2024-01-01", "2024-01-02"))
        assertThat(packageUsage).hasSize(2)

        // getAllDistinctUsageDateStrings
        val distinctDates = dao.getAllDistinctUsageDateStrings().first()
        assertThat(distinctDates).containsExactly("2024-01-01", "2024-01-02", "2024-01-03")

        // getAllUsageRecords
        val allRecords = dao.getAllUsageRecords().first()
        assertThat(allRecords).hasSize(4)
    }
@Test
    fun `delete methods remove correct data`() = runBlocking {
        val records = listOf(
            DailyAppUsageRecord(dateString = "2024-01-01", packageName = "app1", usageTimeMillis = 100),
            DailyAppUsageRecord(dateString = "2024-01-02", packageName = "app2", usageTimeMillis = 200),
            DailyAppUsageRecord(dateString = "2024-01-03", packageName = "app3", usageTimeMillis = 300)
        )
        dao.insertAllUsage(records)

        // deleteOldUsageData
        dao.deleteOldUsageData("2024-01-02")
        var allRecords = dao.getAllUsageRecords().first()
        assertThat(allRecords).hasSize(2)
        assertThat(allRecords.find { it.dateString == "2024-01-01" }).isNull()

        // deleteUsageForDate
        dao.deleteUsageForDate("2024-01-02")
        allRecords = dao.getAllUsageRecords().first()
        assertThat(allRecords).hasSize(1)
        assertThat(allRecords.first().packageName).isEqualTo("app3")

        // clearAllUsageData
        dao.clearAllUsageData()
        allRecords = dao.getAllUsageRecords().first()
        assertThat(allRecords).isEmpty()
    }
@Test
    fun `getUsageCountForDateString - returns correct count`() = runBlocking {
        val records = listOf(
            DailyAppUsageRecord(dateString = "2024-01-01", packageName = "app1", usageTimeMillis = 100),
            DailyAppUsageRecord(dateString = "2024-01-01", packageName = "app2", usageTimeMillis = 200)
        )
        dao.insertAllUsage(records)

        val count = dao.getUsageCountForDateString("2024-01-01")
        assertThat(count).isEqualTo(2)

        val emptyCount = dao.getUsageCountForDateString("2024-01-02")
        assertThat(emptyCount).isEqualTo(0)
    }