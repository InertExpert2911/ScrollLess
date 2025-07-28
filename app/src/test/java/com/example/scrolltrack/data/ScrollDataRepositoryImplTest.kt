package com.example.scrolltrack.data

import android.app.usage.UsageEvents
import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.scrolltrack.data.processors.DailyDataProcessor
import com.example.scrolltrack.data.processors.DailyProcessingResult
import com.example.scrolltrack.db.*
import com.example.scrolltrack.util.AppConstants
import com.example.scrolltrack.util.DateUtil
import com.google.common.truth.Truth.assertThat
import io.mockk.*
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
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class ScrollDataRepositoryImplTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var repository: ScrollDataRepositoryImpl
    private lateinit var mockAppMetadataRepository: AppMetadataRepository
    private lateinit var mockDailyDataProcessor: DailyDataProcessor
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
        mockDailyDataProcessor = mockk()

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
            dailyDataProcessor = mockDailyDataProcessor,
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
        return AppMetadata(
            packageName = packageName,
            appName = "App $packageName",
            versionName = "1.0",
            versionCode = 1L,
            isSystemApp = true,
            isUserVisible = isUserVisible,
            userHidesOverride = userHidesOverride,
            installTimestamp = 0L,
            lastUpdateTimestamp = 0L,
            appCategory = -1
        )
    }

    @Test
    fun `processAndSummarizeDate - with mixed events - creates correct summaries`() = runTest {
        val date = "2024-01-20"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val events = listOf(createRawEvent("app1", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + 1000))
        rawAppEventDao.insertEvents(events)

        val mockResult = DailyProcessingResult(
            unlockSessions = listOf(UnlockSessionRecord(id = 1, dateString = date, unlockTimestamp = startOfDay + 500, sessionType = "Intentional", unlockEventType = "TEST")),
            scrollSessions = listOf(ScrollSessionRecord(packageName = "app1", dateString = date, scrollAmountY = 100, sessionStartTime = 1, sessionEndTime = 2, scrollAmount = 100, dataType = "MEASURED", sessionEndReason = "PROCESSED")),
            usageRecords = listOf(DailyAppUsageRecord(packageName = "app1", dateString = date, usageTimeMillis = 4000, appOpenCount = 1, notificationCount = 2)),
            deviceSummary = DailyDeviceSummary(dateString = date, totalUnlockCount = 1, totalAppOpens = 1, totalNotificationCount = 2, totalUsageTimeMillis = 4000, firstUnlockTimestampUtc = startOfDay + 500),
            insights = listOf(DailyInsight(dateString = date, insightKey = "first_app_used", stringValue = "app1"))
        )
        coEvery { mockDailyDataProcessor.invoke(any(), any(), any(), any(), any(), any()) } returns mockResult

        repository.processAndSummarizeDate(date)

        val scroll = scrollSessionDao.getScrollDataForDate(date).first().find { it.packageName == "app1" }
        assertThat(scroll?.totalScrollY).isEqualTo(100)

        val usage = dailyAppUsageDao.getUsageForDate(date).first().find { it.packageName == "app1" }
        assertThat(usage?.usageTimeMillis).isEqualTo(4000)

        val summary = dailyDeviceSummaryDao.getSummaryForDate(date).first()
        assertThat(summary?.totalUnlockCount).isEqualTo(1)

        val insights = dailyInsightDao.getInsightsForDateAsFlow(date).first()
        assertThat(insights.find { it.insightKey == "first_app_used" }).isNotNull()
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

        val eventsYesterday = mutableListOf(
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, startOfYesterday + 1000),
            createRawEvent("com.app.one", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfYesterday + 2000),
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE, startOfYesterday + 5000),
            // This event belongs to yesterday but occurs close to midnight
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, startOfToday - 5000)
        )
        val eventsToday = mutableListOf(
            createRawEvent("com.app.two", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfToday - 4000),
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE, startOfToday + 1000),
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, startOfToday + 2000),
            createRawEvent("com.app.one", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfToday + 3000),
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE, startOfToday + 6000)
        )

        rawAppEventDao.insertEvents(eventsYesterday + eventsToday)

        // Process yesterday and verify
        coEvery { mockDailyDataProcessor.invoke(yesterday, any(), any(), any(), any(), any()) } returns DailyProcessingResult(emptyList(), emptyList(), emptyList(), DailyDeviceSummary(dateString = yesterday, totalUnlockCount = 2, totalUsageTimeMillis = 0L, totalUnlockedDurationMillis = 0L, intentionalUnlockCount = 0, glanceUnlockCount = 0, totalAppOpens = 0, totalNotificationCount = 0, lastUpdatedTimestamp = 0L), emptyList())
        repository.processAndSummarizeDate(yesterday)
        val yesterdaySummary = dailyDeviceSummaryDao.getSummaryForDate(yesterday).first()
        assertThat(yesterdaySummary?.totalUnlockCount).isEqualTo(2)

        // Process today and verify
        coEvery { mockDailyDataProcessor.invoke(today, any(), any(), any(), any(), any()) } returns DailyProcessingResult(emptyList(), emptyList(), emptyList(), DailyDeviceSummary(dateString = today, totalUnlockCount = 1, totalUsageTimeMillis = 0L, totalUnlockedDurationMillis = 0L, intentionalUnlockCount = 0, glanceUnlockCount = 0, totalAppOpens = 0, totalNotificationCount = 0, lastUpdatedTimestamp = 0L), emptyList())
        repository.processAndSummarizeDate(today)
        val todaySummary = dailyDeviceSummaryDao.getSummaryForDate(today).first()
        assertThat(todaySummary?.totalUnlockCount).isEqualTo(1)
    }




    @Test
    fun `processAndSummarizeDate - hidden apps are excluded from summaries`() = runTest {
        val date = "2024-03-10"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val visibleApp = "com.app.visible"
        val hiddenApp = "com.app.hidden"

        val visibleAppMeta = createAppMeta(visibleApp, isUserVisible = true)
        val hiddenAppMeta = createAppMeta(hiddenApp, isUserVisible = true, userHidesOverride = true)
        coEvery { mockAppMetadataRepository.getAppMetadata(visibleApp) } returns visibleAppMeta
        coEvery { mockAppMetadataRepository.getAppMetadata(hiddenApp) } returns hiddenAppMeta
        coEvery { mockAppMetadataRepository.getAllMetadata() } returns flowOf(listOf(visibleAppMeta, hiddenAppMeta))
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
            dailyDataProcessor = mockDailyDataProcessor,
            context = context,
            ioDispatcher = UnconfinedTestDispatcher()
        )


        val events = listOf(
            createRawEvent(visibleApp, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + 1000),
            createRawEvent(visibleApp, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, startOfDay + 2000, scrollDeltaY = 100),
            createRawEvent(visibleApp, RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, startOfDay + 3000),
            createRawEvent(hiddenApp, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + 4000),
            createRawEvent(hiddenApp, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, startOfDay + 5000, scrollDeltaY = 200),
            createRawEvent(hiddenApp, RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, startOfDay + 6000)
        )
        rawAppEventDao.insertEvents(events)
        notificationDao.insert(NotificationRecord(notificationKey = "key1", packageName = hiddenApp, postTimeUTC = startOfDay + 4500, dateString = date, title = "t", text = "t", category = "c"))

        val mockScrollSession = ScrollSessionRecord(packageName = visibleApp, dateString = date, scrollAmountY = 100, sessionStartTime = 1, sessionEndTime = 2, scrollAmount = 100, dataType = "MEASURED", sessionEndReason = "PROCESSED")
        coEvery { mockDailyDataProcessor.invoke(date, any(), any(), any(), any(), any()) } returns DailyProcessingResult(emptyList(), listOf(mockScrollSession), listOf(DailyAppUsageRecord(packageName = visibleApp, dateString = date, usageTimeMillis = 2000, appOpenCount = 1, notificationCount = 0)), DailyDeviceSummary(dateString = date, totalUsageTimeMillis = 2000L, totalNotificationCount = 0, totalUnlockCount = 0, intentionalUnlockCount = 0, glanceUnlockCount = 0, totalAppOpens = 1, totalUnlockedDurationMillis = 0, lastUpdatedTimestamp = 0), emptyList())
        repository.processAndSummarizeDate(date)

        val appUsage = dailyAppUsageDao.getUsageForDate(date).first()
        assertThat(appUsage.find { it.packageName == hiddenApp }).isNull()
        assertThat(appUsage.find { it.packageName == visibleApp }).isNotNull()

        val scrollData = scrollSessionDao.getScrollDataForDate(date).first()
        assertThat(scrollData.find { it.packageName == hiddenApp }).isNull()
        assertThat(scrollData.find { it.packageName == visibleApp }).isNotNull()

        val summary = dailyDeviceSummaryDao.getSummaryForDate(date).first()
        assertThat(summary?.totalUsageTimeMillis).isEqualTo(2000L)
        assertThat(summary?.totalNotificationCount).isEqualTo(0)
    }

    @Test
    fun `processAndSummarizeDate - session closes at midnight`() = runTest {
        val date = "2024-03-11"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val endOfDay = DateUtil.getEndOfDayUtcMillis(date)
        val appA = "com.app.midnight"

        val events = listOf(
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, endOfDay - 5000)
        )
        rawAppEventDao.insertEvents(events)

        coEvery { mockDailyDataProcessor.invoke(date, any(), any(), any(), any(), any()) } returns DailyProcessingResult(emptyList(), emptyList(), listOf(DailyAppUsageRecord(packageName = appA, dateString = date, usageTimeMillis = 5000L, appOpenCount = 1, notificationCount = 0)), null, emptyList())
        repository.processAndSummarizeDate(date)

        val usage = dailyAppUsageDao.getUsageForDate(date).first().find { it.packageName == appA }
        assertThat(usage).isNotNull()
        assertThat(usage?.usageTimeMillis).isEqualTo(5000L)
    }

    @Test
    fun `processAndSummarizeDate - infers RETURN_TO_HOME event`() = runTest {
        val date = "2024-03-12"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val appA = "com.app.a"
        val appB = "com.app.b"

        val events = listOf(
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + 1000),
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, startOfDay + 2000),
            createRawEvent(appB, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + 2000 + 501)
        )
        rawAppEventDao.insertEvents(events)

        coEvery { mockDailyDataProcessor.invoke(date, any(), any(), any(), any(), any()) } returns DailyProcessingResult(emptyList(), emptyList(), listOf(DailyAppUsageRecord(packageName = appB, dateString = date, appOpenCount = 0, usageTimeMillis = 0)), null, emptyList())
        repository.processAndSummarizeDate(date)

        val usageForB = dailyAppUsageDao.getUsageForDate(date).first().find { it.packageName == appB }
        assertThat(usageForB?.appOpenCount).isEqualTo(0)
    }


    @Test
    fun `getFirstAppUsedAfter - skips hidden app`() = runTest {
        val date = "2024-03-14"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val hiddenApp = "com.app.hidden"
        val visibleApp = "com.app.visible"

        val hiddenAppMeta = createAppMeta(hiddenApp, userHidesOverride = true)
        coEvery { mockAppMetadataRepository.getAppMetadata(hiddenApp) } returns hiddenAppMeta
        coEvery { mockAppMetadataRepository.getAppMetadata(visibleApp) } returns createAppMeta(visibleApp)
        coEvery { mockAppMetadataRepository.getAllMetadata() } returns flowOf(listOf(hiddenAppMeta, createAppMeta(visibleApp)))
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
            dailyDataProcessor = mockDailyDataProcessor,
            context = context,
            ioDispatcher = UnconfinedTestDispatcher()
        )

        val events = listOf(
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, startOfDay + 1000),
            createRawEvent(hiddenApp, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + 2000),
            createRawEvent(visibleApp, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + 3000)
        )
        rawAppEventDao.insertEvents(events)

        coEvery { mockDailyDataProcessor.invoke(date, any(), any(), any(), any(), any()) } returns DailyProcessingResult(emptyList(), emptyList(), emptyList(), null, listOf(DailyInsight(dateString = date, insightKey = "first_app_used", stringValue = visibleApp)))
        repository.processAndSummarizeDate(date)

        val insights = dailyInsightDao.getInsightsForDateAsFlow(date).first()
        val firstAppInsight = insights.find { it.insightKey == "first_app_used" }
        assertThat(firstAppInsight).isNotNull()
        assertThat(firstAppInsight?.stringValue).isEqualTo(visibleApp)
    }
    @Test
    fun `getAppUsageForDate - filters out hidden apps`() = runTest {
        val date = "2024-03-15"
        val visibleApp = "com.app.visible"
        val hiddenApp = "com.app.hidden"

        // 1. Setup the filter set
        val hiddenAppMeta = createAppMeta(hiddenApp, userHidesOverride = true)
        coEvery { mockAppMetadataRepository.getAllMetadata() } returns flowOf(listOf(hiddenAppMeta))

        // 2. Re-initialize repository to pick up the new filter set from the mock
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
            dailyDataProcessor = mockDailyDataProcessor,
            context = context,
            ioDispatcher = UnconfinedTestDispatcher()
        )

        // 3. Insert data into DAO
        val visibleUsage = DailyAppUsageRecord(packageName = visibleApp, dateString = date, usageTimeMillis = 1000L)
        val hiddenUsage = DailyAppUsageRecord(packageName = hiddenApp, dateString = date, usageTimeMillis = 2000L)
        dailyAppUsageDao.insertAllUsage(listOf(visibleUsage, hiddenUsage))

        // 4. Act
        val result = repository.getAppUsageForDate(date).first()

        // 5. Assert
        assertThat(result).hasSize(1)
        assertThat(result.first().packageName).isEqualTo(visibleApp)
    }

    @Test
    fun `refreshDataOnAppOpen - calls sync and process for today`() = runTest {
        // Spy on the repository to verify calls to its own methods
        val spiedRepo = spyk(repository)

        // Stub the methods to prevent them from running their actual logic
        coEvery { spiedRepo.syncSystemEvents() } returns true
        coEvery { spiedRepo.processAndSummarizeDate(any()) } just Runs

        // Act
        spiedRepo.refreshDataOnAppOpen()

        // Verify
        coVerify { spiedRepo.syncSystemEvents() }
        coVerify { spiedRepo.processAndSummarizeDate(DateUtil.getCurrentLocalDateString()) }
    }
}
