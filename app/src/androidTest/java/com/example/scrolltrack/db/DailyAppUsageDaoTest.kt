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