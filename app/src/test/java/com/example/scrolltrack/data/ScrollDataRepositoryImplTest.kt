package com.example.scrolltrack.data

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.scrolltrack.db.*
import com.example.scrolltrack.util.DateUtil
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class ScrollDataRepositoryImplTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var repository: ScrollDataRepositoryImpl
    private lateinit var mockAppMetadataRepository: AppMetadataRepository
    private lateinit var scrollSessionDao: ScrollSessionDao
    private lateinit var dailyAppUsageDao: DailyAppUsageDao
    private lateinit var rawAppEventDao: RawAppEventDao
    private lateinit var notificationDao: NotificationDao
    private lateinit var dailyDeviceSummaryDao: DailyDeviceSummaryDao
    private lateinit var unlockSessionDao: UnlockSessionDao
    private lateinit var dailyInsightDao: DailyInsightDao
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        scrollSessionDao = db.scrollSessionDao()
        dailyAppUsageDao = db.dailyAppUsageDao()
        rawAppEventDao = db.rawAppEventDao()
        notificationDao = db.notificationDao()
        dailyDeviceSummaryDao = db.dailyDeviceSummaryDao()
        unlockSessionDao = db.unlockSessionDao()
        dailyInsightDao = db.dailyInsightDao()

        mockAppMetadataRepository = mockk()
        coEvery { mockAppMetadataRepository.getAllMetadata() } returns flowOf(emptyList())

        repository = ScrollDataRepositoryImpl(
            appDatabase = db,
            appMetadataRepository = mockAppMetadataRepository,
            scrollSessionDao = scrollSessionDao,
            dailyAppUsageDao = dailyAppUsageDao,
            rawAppEventDao = rawAppEventDao,
            notificationDao = notificationDao,
            dailyDeviceSummaryDao = dailyDeviceSummaryDao,
            unlockSessionDao = unlockSessionDao,
            dailyInsightDao = dailyInsightDao,
            context = context,
            ioDispatcher = UnconfinedTestDispatcher()
        )
    }

    @After
    fun tearDown() {
        db.close()
        unmockkAll()
    }

    private fun createRawEvent(
        pkg: String,
        type: Int,
        timestamp: Long,
        className: String? = "TestClass",
        scrollDeltaX: Int? = null,
        scrollDeltaY: Int? = null
    ): RawAppEvent {
        return RawAppEvent(
            packageName = pkg,
            className = className,
            eventType = type,
            eventTimestamp = timestamp,
            eventDateString = DateUtil.formatUtcTimestampToLocalDateString(timestamp),
            source = "TEST",
            scrollDeltaX = scrollDeltaX,
            scrollDeltaY = scrollDeltaY
        )
    }
    
    private fun createAppMeta(packageName: String, isUserVisible: Boolean = true, userHidesOverride: Boolean? = null): AppMetadata {
        return AppMetadata(packageName, "App $packageName", "1.0", 1L, false, true, false, -1, isUserVisible, userHidesOverride, 0L, 0L)
    }

    @Test
    fun `processAndSummarizeDate - with mixed events - creates correct summaries`() = runTest {
        val date = "2024-01-20"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)

        val app1 = "com.app.one"
        val app2 = "com.app.two"
        val filteredApp = "com.system.background"

        val events = listOf(
            createRawEvent(app1, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + 1000),
            createRawEvent(app1, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, startOfDay + 2000, scrollDeltaY = 50),
            createRawEvent(app1, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, startOfDay + 3000, scrollDeltaY = 50),
            createRawEvent(app1, RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, startOfDay + 5000), 
            createRawEvent(app2, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + 6000),
            createRawEvent(app2, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, startOfDay + 7000, scrollDeltaX = 50),
            createRawEvent(app2, RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, startOfDay + 9000), 
            createRawEvent(filteredApp, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + 10000),
            createRawEvent(filteredApp, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, startOfDay + 11000, scrollDeltaY = 1000),
            createRawEvent(filteredApp, RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, startOfDay + 12000),
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, startOfDay + 500)
        )
        rawAppEventDao.insertEvents(events)

        notificationDao.insert(NotificationRecord(notificationKey = "key1", packageName = app1, postTimeUTC = startOfDay + 1500, dateString = date, title = "t", text = "t", category = "c"))
        notificationDao.insert(NotificationRecord(notificationKey = "key2", packageName = app1, postTimeUTC = startOfDay + 1600, dateString = date, title = "t", text = "t", category = "c"))
        notificationDao.insert(NotificationRecord(notificationKey = "key3", packageName = app2, postTimeUTC = startOfDay + 6500, dateString = date, title = "t", text = "t", category = "c"))

        coEvery { mockAppMetadataRepository.getAllMetadata() } returns flowOf(listOf(createAppMeta(filteredApp, isUserVisible = false)))

        repository.processAndSummarizeDate(date)

        val app1Scroll = scrollSessionDao.getScrollDataForDate(date).first().find { it.packageName == app1 }
        val app2Scroll = scrollSessionDao.getScrollDataForDate(date).first().find { it.packageName == app2 }
        assertThat(app1Scroll?.totalScrollY).isEqualTo(100L)
        assertThat(app2Scroll?.totalScrollX).isEqualTo(50L)

        val app1Usage = dailyAppUsageDao.getUsageForDate(date).first().find { it.packageName == app1 }
        val app2Usage = dailyAppUsageDao.getUsageForDate(date).first().find { it.packageName == app2 }
        assertThat(app1Usage?.usageTimeMillis).isEqualTo(4000L)
        assertThat(app1Usage?.activeTimeMillis).isGreaterThan(0L)
        assertThat(app1Usage?.appOpenCount).isEqualTo(1)
        assertThat(app1Usage?.notificationCount).isEqualTo(2)

        assertThat(app2Usage?.usageTimeMillis).isEqualTo(3000L)
        assertThat(app2Usage?.activeTimeMillis).isGreaterThan(0L)
        assertThat(app2Usage?.appOpenCount).isEqualTo(1)
        assertThat(app2Usage?.notificationCount).isEqualTo(1)

        val summary = dailyDeviceSummaryDao.getSummaryForDate(date).first()
        assertThat(summary).isNotNull()
        assertThat(summary?.dateString).isEqualTo(date)
        assertThat(summary?.totalUnlockCount).isEqualTo(1)
        assertThat(summary?.totalAppOpens).isEqualTo(2)
        assertThat(summary?.totalNotificationCount).isEqualTo(3)
        assertThat(summary?.totalUsageTimeMillis).isEqualTo(7000L) 
        assertThat(summary?.firstUnlockTimestampUtc).isEqualTo(startOfDay + 500)
    }

    @Test
    fun `processAndSummarizeDate - with no events - does not write any data and clears previous`() = runTest {
        val date = "2024-01-21"
        
        dailyDeviceSummaryDao.insertOrUpdate(DailyDeviceSummary(dateString = date, totalUnlockCount = 99))
        
        coEvery { mockAppMetadataRepository.getAllMetadata() } returns flowOf(emptyList())

        repository.processAndSummarizeDate(date)

        val summary = dailyDeviceSummaryDao.getSummaryForDate(date).first()
        assertThat(summary).isNull()

        val usage = dailyAppUsageDao.getUsageForDate(date).first()
        assertThat(usage).isEmpty()

        val scrolls = scrollSessionDao.getScrollDataForDate(date).first()
        assertThat(scrolls).isEmpty()
    }
    
    @Test
    fun `backfill - processes multiple days without duplicating unlock counts`() = runTest {
        val today = "2024-02-02"
        val yesterday = "2024-02-01"
        val startOfToday = DateUtil.getStartOfDayUtcMillis(today)
        val startOfYesterday = DateUtil.getStartOfDayUtcMillis(yesterday)

        // Events for yesterday
        val eventsYesterday = mutableListOf(
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, startOfYesterday + 1000), // Unlock 1
            createRawEvent("com.app.one", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfYesterday + 2000),
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE, startOfYesterday + 5000) // Lock 1
        )
        // Events for today - includes an open session that carries over midnight
        val eventsToday = mutableListOf(
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, startOfToday - 5000), // Unlock 2 (yesterday's session)
            createRawEvent("com.app.two", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfToday - 4000),
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE, startOfToday + 1000), // Lock 2 (closes yesterday's session)

            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, startOfToday + 2000), // Unlock 3 (today)
            createRawEvent("com.app.one", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfToday + 3000),
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE, startOfToday + 6000) // Lock 3
        )

        // Simulate backfill: insert all events, then process day by day (past to present)
        rawAppEventDao.insertEvents(eventsYesterday + eventsToday)

        // Process yesterday first
        repository.processAndSummarizeDate(yesterday)
        var yesterdaySummary = dailyDeviceSummaryDao.getSummaryForDate(yesterday).first()
        // Should have 2 unlocks: the one fully within yesterday, and the one that started yesterday and ended today
        assertThat(yesterdaySummary?.totalUnlockCount).isEqualTo(2)


        // Now process today
        repository.processAndSummarizeDate(today)
        var todaySummary = dailyDeviceSummaryDao.getSummaryForDate(today).first()
        assertThat(todaySummary?.totalUnlockCount).isEqualTo(1)

        // Re-process yesterday and check for data corruption
        repository.processAndSummarizeDate(yesterday)
        yesterdaySummary = dailyDeviceSummaryDao.getSummaryForDate(yesterday).first()
        assertThat(yesterdaySummary?.totalUnlockCount).isEqualTo(2) // This should NOT change
    }
}
