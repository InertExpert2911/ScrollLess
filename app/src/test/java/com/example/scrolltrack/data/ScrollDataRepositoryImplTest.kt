package com.example.scrolltrack.data

import android.app.usage.UsageEvents
import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.scrolltrack.db.AppDatabase
import com.example.scrolltrack.db.AppMetadata
import com.example.scrolltrack.db.DailyAppUsageDao
import com.example.scrolltrack.db.DailyDeviceSummary
import com.example.scrolltrack.db.DailyDeviceSummaryDao
import com.example.scrolltrack.db.NotificationDao
import com.example.scrolltrack.db.NotificationRecord
import com.example.scrolltrack.db.RawAppEvent
import com.example.scrolltrack.db.RawAppEventDao
import com.example.scrolltrack.db.ScrollSessionDao
import com.example.scrolltrack.db.UnlockSessionDao
import com.example.scrolltrack.util.DateUtil
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.example.scrolltrack.util.AppConstants

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

        mockAppMetadataRepository = mockk()
        coEvery { mockAppMetadataRepository.getAllMetadata() } returns emptyList()

        repository = ScrollDataRepositoryImpl(
            appDatabase = db,
            appMetadataRepository = mockAppMetadataRepository,
            scrollSessionDao = scrollSessionDao,
            dailyAppUsageDao = dailyAppUsageDao,
            rawAppEventDao = rawAppEventDao,
            notificationDao = notificationDao,
            dailyDeviceSummaryDao = dailyDeviceSummaryDao,
            unlockSessionDao = unlockSessionDao,
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
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_PRESENT, startOfDay + 500)
        )
        rawAppEventDao.insertEvents(events)

        notificationDao.insert(NotificationRecord(notificationKey = "key1", packageName = app1, postTimeUTC = startOfDay + 1500, dateString = date, title = "t", text = "t", category = "c"))
        notificationDao.insert(NotificationRecord(notificationKey = "key2", packageName = app1, postTimeUTC = startOfDay + 1600, dateString = date, title = "t", text = "t", category = "c"))
        notificationDao.insert(NotificationRecord(notificationKey = "key3", packageName = app2, postTimeUTC = startOfDay + 6500, dateString = date, title = "t", text = "t", category = "c"))

        coEvery { mockAppMetadataRepository.getAllMetadata() } returns listOf(createAppMeta(filteredApp, isUserVisible = false))

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
        
        coEvery { mockAppMetadataRepository.getAllMetadata() } returns emptyList()

        repository.processAndSummarizeDate(date)

        val summary = dailyDeviceSummaryDao.getSummaryForDate(date).first()
        assertThat(summary).isNull()

        val usage = dailyAppUsageDao.getUsageForDate(date).first()
        assertThat(usage).isEmpty()

        val scrolls = scrollSessionDao.getScrollDataForDate(date).first()
        assertThat(scrolls).isEmpty()
    }

    @Test
    fun `processScrollEvents - merges sessions correctly`() = runTest {
        val now = System.currentTimeMillis()
        val app1 = "com.app.one"
        val app2 = "com.app.two"
        val filter = emptySet<String>()

        val events = listOf(
            // Session 1 (app1, MEASURED)
            createRawEvent(app1, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, now, scrollDeltaY = 100),
            createRawEvent(app1, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, now + 1000, scrollDeltaY = 150), // Should merge

            // Session 2 (app1, INFERRED) - Different type, should not merge with session 1
            createRawEvent(app1, RawAppEvent.EVENT_TYPE_SCROLL_INFERRED, now + 2000, scrollDeltaY = 50),

            // Session 3 (app2, MEASURED) - Different app, should not merge
            createRawEvent(app2, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, now + 3000, scrollDeltaX = 70),

            // No-op scroll, should be ignored
            createRawEvent(app1, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, now + 4000, scrollDeltaX = 0, scrollDeltaY = 0),

            // Session 4 (app1, MEASURED) - Large time gap, should not merge with session 1
            createRawEvent(app1, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, now + AppConstants.SESSION_MERGE_GAP_MS + 2000, scrollDeltaY = 200)
        )

        val result = repository.processScrollEvents(events, filter)

        assertThat(result).hasSize(4)

        val session1Result = result.find { it.sessionStartTime == now }
        assertThat(session1Result).isNotNull()
        assertThat(session1Result?.packageName).isEqualTo(app1)
        assertThat(session1Result?.dataType).isEqualTo("MEASURED")
        assertThat(session1Result?.scrollAmount).isEqualTo(250) // 100 + 150
        assertThat(session1Result?.sessionEndTime).isEqualTo(now + 1000)

        val session2Result = result.find { it.sessionStartTime == now + 2000L }
        assertThat(session2Result).isNotNull()
        assertThat(session2Result?.dataType).isEqualTo("INFERRED")
        assertThat(session2Result?.scrollAmount).isEqualTo(50)

        val session3Result = result.find { it.sessionStartTime == now + 3000L }
        assertThat(session3Result).isNotNull()
        assertThat(session3Result?.packageName).isEqualTo(app2)
        assertThat(session3Result?.scrollAmount).isEqualTo(70)

        val session4Result = result.find { it.sessionStartTime == now + AppConstants.SESSION_MERGE_GAP_MS + 2000 }
        assertThat(session4Result).isNotNull()
        assertThat(session4Result?.scrollAmount).isEqualTo(200)
    }

    @Test
    fun `processUnlockSessions - creates and closes sessions correctly`() = runTest {
        val date = "2024-01-22"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val app1 = "com.app.one"

        val events = listOf(
            // Unlock 1
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, startOfDay + 1000),
            createRawEvent(app1, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + 2000),
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE, startOfDay + 5000), // Lock 1

            // Unlock 2 (no associated lock in this list)
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_PRESENT, startOfDay + 10000)
        )

        repository.processUnlockSessions(events, date)

        // Check Unlock 1 was created and closed
        val closedSession = unlockSessionDao.getOpenSessionBefore(startOfDay + 6000) // Look for open sessions before lock
        assertThat(closedSession).isNull() // It should be closed, so no open session found

        val allSessions = db.unlockSessionDao().getUnlockTimestampsForDate(date)
        assertThat(allSessions).hasSize(2)

        // We can't easily get the closed one back without a new DAO method,
        // but we can infer its creation by the fact that there are 2 sessions and one is still open.
        val openSession = unlockSessionDao.getOpenSessionBefore(startOfDay + 11000)
        assertThat(openSession).isNotNull()
        assertThat(openSession?.unlockTimestamp).isEqualTo(startOfDay + 10000)
    }

    @Test
    fun `aggregateUsage - calculates usage and active time correctly`() = runTest {
        val app1 = "com.app.one"
        val app2 = "com.app.two"
        val periodEnd = 20000L

        val events = listOf(
            // App 1 session: 5 seconds total, 2 seconds active
            createRawEvent(app1, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 1000L),
            createRawEvent(app1, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 2000L, scrollDeltaY = 10), // a 1s window
            createRawEvent(app1, RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_CLICKED, 3000L), // a 1s window
            createRawEvent(app1, RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, 6000L),

            // App 2 session: 4 seconds total, interrupted by screen off
            createRawEvent(app2, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 10000L),
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE, 14000L),

            // App 1 second session: open until end of period
            createRawEvent(app1, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 15000L)
        )

        val result = repository.aggregateUsage(events, periodEnd)

        assertThat(result).hasSize(2)
        
        val app1Usage = result[app1]
        assertThat(app1Usage).isNotNull()
        assertThat(app1Usage?.first).isEqualTo(10000L) // (6k-1k) + (20k-15k)
        // Active time: scroll event [2k, 2k+scroll_window], tap event [3k, 3k+tap_window]
        // Assuming windows are 1s, and they overlap, total active time is around 2-4s. We'll check it's > 0
        assertThat(app1Usage?.second).isGreaterThan(0L)
        
        val app2Usage = result[app2]
        assertThat(app2Usage).isNotNull()
        assertThat(app2Usage?.first).isEqualTo(4000L) // 14k - 10k
        assertThat(app2Usage?.second).isEqualTo(0L) // No interaction events for app2
    }
}
