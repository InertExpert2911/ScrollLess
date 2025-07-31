package com.example.scrolltrack.data

import android.app.Application
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.SharedPreferences
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.scrolltrack.data.processors.DailyDataProcessor
import com.example.scrolltrack.data.processors.DailyProcessingResult
import com.example.scrolltrack.db.*
import com.example.scrolltrack.util.DateUtil
import com.example.scrolltrack.util.PermissionUtils
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
import org.robolectric.Shadows
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
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockPrefsEditor: SharedPreferences.Editor

    @Before
    fun setUp() {
        // Use a spyk to use the real context but stub getSharedPreferences
        context = spyk(ApplicationProvider.getApplicationContext<Application>())
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
        mockDailyDataProcessor = mockk(relaxed = true)

        usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        mockPrefs = mockk(relaxed = true)
        mockPrefsEditor = mockk(relaxed = true)
        every { mockPrefs.edit() } returns mockPrefsEditor
        every { context.getSharedPreferences(any(), any()) } returns mockPrefs


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
        coEvery { mockDailyDataProcessor.invoke(any<String>(), any<List<RawAppEvent>>(), any<List<NotificationRecord>>(), any<Set<String>>(), any<Map<String, Int>>(), or(any<String>(), isNull())) } returns mockResult

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
        coEvery { mockDailyDataProcessor.invoke(eq(yesterday), any(), any(), any(), any(), any()) } returns DailyProcessingResult(emptyList(), emptyList(), emptyList(), DailyDeviceSummary(dateString = yesterday, totalUnlockCount = 2, totalUsageTimeMillis = 0L, totalUnlockedDurationMillis = 0L, intentionalUnlockCount = 0, glanceUnlockCount = 0, totalAppOpens = 0, totalNotificationCount = 0, lastUpdatedTimestamp = 0L), emptyList())
        repository.processAndSummarizeDate(yesterday)
        val yesterdaySummary = dailyDeviceSummaryDao.getSummaryForDate(yesterday).first()
        assertThat(yesterdaySummary?.totalUnlockCount).isEqualTo(2)

        // Process today and verify
        coEvery { mockDailyDataProcessor.invoke(eq(today), any(), any(), any(), any(), any()) } returns DailyProcessingResult(emptyList(), emptyList(), emptyList(), DailyDeviceSummary(dateString = today, totalUnlockCount = 1, totalUsageTimeMillis = 0L, totalUnlockedDurationMillis = 0L, intentionalUnlockCount = 0, glanceUnlockCount = 0, totalAppOpens = 0, totalNotificationCount = 0, lastUpdatedTimestamp = 0L), emptyList())
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
        coEvery { mockDailyDataProcessor.invoke(any<String>(), any<List<RawAppEvent>>(), any<List<NotificationRecord>>(), any<Set<String>>(), any<Map<String, Int>>(), or(any<String>(), isNull())) } returns DailyProcessingResult(emptyList(), listOf(mockScrollSession), listOf(DailyAppUsageRecord(packageName = visibleApp, dateString = date, usageTimeMillis = 2000, appOpenCount = 1, notificationCount = 0)), DailyDeviceSummary(dateString = date, totalUsageTimeMillis = 2000L, totalNotificationCount = 0, totalUnlockCount = 0, intentionalUnlockCount = 0, glanceUnlockCount = 0, totalAppOpens = 1, totalUnlockedDurationMillis = 0, lastUpdatedTimestamp = 0), emptyList())
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
        val endOfDay = DateUtil.getEndOfDayUtcMillis(date)
        val appA = "com.app.midnight"

        val events = listOf(
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, endOfDay - 5000)
        )
        rawAppEventDao.insertEvents(events)

        coEvery { mockDailyDataProcessor.invoke(any<String>(), any<List<RawAppEvent>>(), any<List<NotificationRecord>>(), any<Set<String>>(), any<Map<String, Int>>(), or(any<String>(), isNull())) } returns DailyProcessingResult(emptyList(), emptyList(), listOf(DailyAppUsageRecord(packageName = appA, dateString = date, usageTimeMillis = 5000L, appOpenCount = 1, notificationCount = 0)), null, emptyList())
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

        coEvery { mockDailyDataProcessor.invoke(any<String>(), any<List<RawAppEvent>>(), any<List<NotificationRecord>>(), any<Set<String>>(), any<Map<String, Int>>(), or(any<String>(), isNull())) } returns DailyProcessingResult(emptyList(), emptyList(), listOf(DailyAppUsageRecord(packageName = appB, dateString = date, appOpenCount = 0, usageTimeMillis = 0)), null, emptyList())
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

        coEvery { mockDailyDataProcessor.invoke(any<String>(), any<List<RawAppEvent>>(), any<List<NotificationRecord>>(), any<Set<String>>(), any<Map<String, Int>>(), or(any<String>(), isNull())) } returns DailyProcessingResult(emptyList(), emptyList(), emptyList(), null, listOf(DailyInsight(dateString = date, insightKey = "first_app_used", stringValue = visibleApp)))
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

    @Test
    fun `syncSystemEvents - inserts events and updates timestamp`() = runTest {
        // 1. Arrange
        val lastSyncTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)
        every { mockPrefs.getLong(any(), any()) } returns lastSyncTime

        val shadowUsageStatsManager = Shadows.shadowOf(usageStatsManager)
        shadowUsageStatsManager.addEvent("com.app.one", lastSyncTime + 1000, UsageEvents.Event.ACTIVITY_RESUMED)
        shadowUsageStatsManager.addEvent("com.app.one", lastSyncTime + 2000, UsageEvents.Event.ACTIVITY_PAUSED)

        // 2. Act
        val result = repository.syncSystemEvents()

        // 3. Assert
        assertThat(result).isTrue()

        // Verify correct events were inserted
        val insertedEvents = rawAppEventDao.getEventsForPeriod(0, System.currentTimeMillis() + 1)
        assertThat(insertedEvents).hasSize(2)
        assertThat(insertedEvents[0].packageName).isEqualTo("com.app.one")
        assertThat(insertedEvents[0].eventType).isEqualTo(RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED)
        assertThat(insertedEvents[1].eventType).isEqualTo(RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED)

        // Verify timestamp was updated
        val capturedTimestamp = slot<Long>()
        verify { mockPrefsEditor.putLong(eq("last_system_event_sync_timestamp"), capture(capturedTimestamp)) }
        assertThat(capturedTimestamp.captured).isGreaterThan(lastSyncTime)
    }

    @Test
    fun `backfillHistoricalAppUsageData - fetches events and processes multiple days`() = runTest {
        // 1. Arrange
        val datesToProcess = (0..2).map { DateUtil.getPastDateString(it) } // Process 3 days
        val shadowUsageStatsManager = Shadows.shadowOf(usageStatsManager)

        // Mock UsageStatsManager to return events for each day
        for (date in datesToProcess) {
            val startTime = DateUtil.getStartOfDayUtcMillis(date)
            shadowUsageStatsManager.addEvent("com.app.$date", startTime + 1000, UsageEvents.Event.ACTIVITY_RESUMED)
        }

        // Mock the data processor to return a simple result for each day
        coEvery { mockDailyDataProcessor.invoke(any<String>(), any<List<RawAppEvent>>(), any<List<NotificationRecord>>(), any<Set<String>>(), any<Map<String, Int>>(), or(any<String>(), isNull())) } answers {
            val dateArg = arg<String>(0)
            DailyProcessingResult(
                deviceSummary = DailyDeviceSummary(dateString = dateArg, totalAppOpens = 1),
                usageRecords = listOf(DailyAppUsageRecord(packageName = "com.app.$dateArg", dateString = dateArg, appOpenCount = 1, usageTimeMillis = 1000)),
                scrollSessions = emptyList(),
                unlockSessions = emptyList(),
                insights = emptyList()
            )
        }

        // 2. Act
        val result = repository.backfillHistoricalAppUsageData(3)

        // 3. Assert
        assertThat(result).isTrue()

        // Verify events were inserted
        val allEvents = rawAppEventDao.getEventsForPeriod(0, System.currentTimeMillis())
        assertThat(allEvents).hasSize(3)

        // Verify each day was processed and data was inserted
        for (date in datesToProcess) {
            val summary = dailyDeviceSummaryDao.getSummaryForDate(date).first()
            assertThat(summary).isNotNull()
            assertThat(summary?.totalAppOpens).isEqualTo(1)

            val usage = dailyAppUsageDao.getUsageForDate(date).first()
            assertThat(usage).hasSize(1)
            assertThat(usage.first().packageName).isEqualTo("com.app.$date")
        }
    }

    @Test
    fun `getScrollDataForDate - filters out hidden apps`() = runTest {
        val date = "2024-03-16"
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
        val visibleScroll = ScrollSessionRecord(packageName = visibleApp, dateString = date, scrollAmount = 100, sessionStartTime = 1, sessionEndTime = 2, sessionEndReason = "test")
        val hiddenScroll = ScrollSessionRecord(packageName = hiddenApp, dateString = date, scrollAmount = 200, sessionStartTime = 1, sessionEndTime = 2, sessionEndReason = "test")
        scrollSessionDao.insertSessions(listOf(visibleScroll, hiddenScroll))

        // 4. Act
        val result = repository.getScrollDataForDate(date).first()

        // 5. Assert
        assertThat(result).hasSize(1)
        assertThat(result.first().packageName).isEqualTo(visibleApp)
    }

    @Test
    fun `getTotalScrollForDate - returns correct sum from DAO`() = runTest {
        val date = "2024-03-17"
        scrollSessionDao.insertSessions(listOf(
            ScrollSessionRecord(packageName = "app1", dateString = date, scrollAmount = 100, sessionStartTime = 1, sessionEndTime = 2, sessionEndReason = "test"),
            ScrollSessionRecord(packageName = "app2", dateString = date, scrollAmount = 250, sessionStartTime = 1, sessionEndTime = 2, sessionEndReason = "test"),
            ScrollSessionRecord(packageName = "app1", dateString = "2024-03-18", scrollAmount = 500, sessionStartTime = 1, sessionEndTime = 2, sessionEndReason = "test") // Different date
        ))

        val totalScroll = repository.getTotalScrollForDate(date).first()

        assertThat(totalScroll).isEqualTo(350)
    }

    @Test
    fun `getCurrentForegroundApp - returns app with most recent lastTimeUsed`() = runTest {
        val now = System.currentTimeMillis()

        // For this test only, fully mock the context and manager because queryUsageStats is broken in Robolectric
        val mockUsageStatsManager: UsageStatsManager = mockk()
        val mockContext: Context = mockk(relaxed = true)
        every { mockContext.getSystemService(Context.USAGE_STATS_SERVICE) } returns mockUsageStatsManager
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs

        // Because we are using a mock context, we must also mock the permission check.
        mockkObject(PermissionUtils)
        every { PermissionUtils.hasUsageStatsPermission(mockContext) } returns true

        // Re-initialize repository with the fully mocked context
        repository = ScrollDataRepositoryImpl(
            appDatabase = db, appMetadataRepository = mockAppMetadataRepository,
            scrollSessionDao = scrollSessionDao, dailyAppUsageDao = dailyAppUsageDao,
            rawAppEventDao = rawAppEventDao, notificationDao = notificationDao,
            dailyDeviceSummaryDao = dailyDeviceSummaryDao, unlockSessionDao = unlockSessionDao,
            dailyInsightDao = dailyInsightDao, dailyDataProcessor = mockDailyDataProcessor,
            context = mockContext,
            ioDispatcher = UnconfinedTestDispatcher()
        )

        // Create MOCKED UsageStats objects
        val statOld: UsageStats = mockk()
        every { statOld.packageName } returns "com.app.old"
        every { statOld.lastTimeUsed } returns now - 10000

        val statRecent: UsageStats = mockk()
        every { statRecent.packageName } returns "com.app.recent"
        every { statRecent.lastTimeUsed } returns now - 1000

        val statMiddle: UsageStats = mockk()
        every { statMiddle.packageName } returns "com.app.middle"
        every { statMiddle.lastTimeUsed } returns now - 5000

        val stats = listOf(statOld, statRecent, statMiddle)
        every { mockUsageStatsManager.queryUsageStats(any(), any(), any()) } returns stats

        val foregroundApp = repository.getCurrentForegroundApp()

        assertThat(foregroundApp).isEqualTo("com.app.recent")
    }
}