package com.example.scrolltrack.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.scrolltrack.db.*
import com.example.scrolltrack.util.AppConstants
import com.example.scrolltrack.util.DateUtil
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowUsageStatsManager
import java.util.*
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class ScrollDataRepositoryImplTest {

    class MainCoroutineRule(
        val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
    ) : TestWatcher() {
        override fun starting(description: Description) {
            super.starting(description)
            Dispatchers.setMain(testDispatcher)
        }

        override fun finished(description: Description) {
            super.finished(description)
            Dispatchers.resetMain()
        }
    }

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private lateinit var context: Context
    private lateinit var mockAppDb: AppDatabase
    private lateinit var shadowUsageStatsManager: ShadowUsageStatsManager
    private lateinit var mockAppMetadataRepository: AppMetadataRepository
    private lateinit var mockScrollSessionDao: ScrollSessionDao
    private lateinit var mockDailyAppUsageDao: DailyAppUsageDao
    private lateinit var mockRawAppEventDao: RawAppEventDao
    private lateinit var mockNotificationDao: NotificationDao
    private lateinit var mockDailyDeviceSummaryDao: DailyDeviceSummaryDao
    private lateinit var repository: ScrollDataRepositoryImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockAppDb = mockk()
        mockAppMetadataRepository = mockk()
        mockScrollSessionDao = mockk()
        mockDailyAppUsageDao = mockk()
        mockRawAppEventDao = mockk(relaxUnitFun = true)
        mockNotificationDao = mockk()
        mockDailyDeviceSummaryDao = mockk(relaxUnitFun = true)

        val usageStatsService = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        shadowUsageStatsManager = Shadows.shadowOf(usageStatsService)

        coEvery { mockAppDb.withTransaction<Unit>(any()) } coAnswers {
            val block = arg<suspend () -> Unit>(0)
            block()
        }

        coEvery { mockAppMetadataRepository.getAllMetadata() } returns emptyList()
        every { context.packageName } returns "com.example.scrolltrack"

        repository = ScrollDataRepositoryImpl(
            appDatabase = mockAppDb,
            appMetadataRepository = mockAppMetadataRepository,
            scrollSessionDao = mockScrollSessionDao,
            dailyAppUsageDao = mockDailyAppUsageDao,
            rawAppEventDao = mockRawAppEventDao,
            notificationDao = mockNotificationDao,
            dailyDeviceSummaryDao = mockDailyDeviceSummaryDao,
            context = context,
            ioDispatcher = mainCoroutineRule.testDispatcher
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createRawEvent(pkg: String, type: Int, timestamp: Long, className: String? = null): RawAppEvent {
        return RawAppEvent(0, pkg, className, type, timestamp, DateUtil.formatUtcTimestampToLocalDateString(timestamp))
    }

    private fun createUsageStatsEvent(pkg: String, className: String?, eventType: Int, timestamp: Long): UsageEvents.Event {
        val event = mockk<UsageEvents.Event>()
        every { event.packageName } returns pkg
        every { event.className } returns className
        every { event.eventType } returns eventType
        every { event.timeStamp } returns timestamp
        return event
    }

    private fun createAppMeta(packageName: String, isUserVisible: Boolean = true, userHidesOverride: Boolean? = null) : AppMetadata {
        return AppMetadata(packageName, "App $packageName", "1.0", 1L, false, true, false, 0, isUserVisible, userHidesOverride, 0L,0L )
    }

    @Test
    fun `getAggregatedScrollDataForDate - calls DAO and returns its flow`() = runTest {
        val dateString = "2023-01-01"
        val expectedData = listOf(AppScrollData("pkg1", 100L, "m"), AppScrollData("pkg2", 200L, "m"))
        every { mockScrollSessionDao.getAggregatedScrollDataForDate(dateString) } returns flowOf(expectedData)

        val resultFlow = repository.getAggregatedScrollDataForDate(dateString)
        assertThat(resultFlow.first()).isEqualTo(expectedData)
        verify { mockScrollSessionDao.getAggregatedScrollDataForDate(dateString) }
    }

    @Test
    fun `insertScrollSession - calls DAO`() = runTest {
        val session = ScrollSessionRecord(packageName = "test", scrollAmount = 10, dataType = "m", sessionStartTime = 0, sessionEndTime = 1, date = "d", sessionEndReason = "r")
        coEvery { mockScrollSessionDao.insertSession(session) } just Runs
        repository.insertScrollSession(session)
        coVerify { mockScrollSessionDao.insertSession(session) }
    }

    @Test
    fun `updateTodayAppUsageStats - fetches system events, aggregates, and saves`() = runTest {
        val today = DateUtil.getCurrentLocalDateString()
        val startOfToday = DateUtil.getStartOfDayUtcMillis(today)

        val systemEvent1 = createUsageStatsEvent("app1", "ActivityA", UsageEvents.Event.ACTIVITY_RESUMED, startOfToday + 1000)
        val systemEvent2 = createUsageStatsEvent("app1", "ActivityA", UsageEvents.Event.ACTIVITY_PAUSED, startOfToday + 5000) // 4s usage
        shadowUsageStatsManager.addEvent(systemEvent1)
        shadowUsageStatsManager.addEvent(systemEvent2)

        val customEvent1 = createRawEvent("app1", RawAppEvent.EVENT_TYPE_USER_PRESENT, startOfToday + 500)
        val mappedSystemEvent1 = repository.mapUsageEventToRawAppEvent(systemEvent1)!!
        val mappedSystemEvent2 = repository.mapUsageEventToRawAppEvent(systemEvent2)!!
        coEvery { mockRawAppEventDao.getEventsForDate(today) } returns listOf(customEvent1, mappedSystemEvent1, mappedSystemEvent2)


        coEvery { mockDailyAppUsageDao.insertOrUpdateUsage(any()) } just Runs
        coEvery { mockNotificationDao.getNotificationCountForDateImmediate(today) } returns 5
        coEvery { mockNotificationDao.getNotificationsForDateList(today) } returns emptyList()


        val success = repository.updateTodayAppUsageStats()
        assertThat(success).isTrue()

        val capturedSystemEventsToInsert = slot<List<RawAppEvent>>()
        coVerify { mockRawAppEventDao.insertEvents(capture(capturedSystemEventsToInsert)) }
        assertThat(capturedSystemEventsToInsert.captured.size).isEqualTo(2)

        coVerify { mockDailyDeviceSummaryDao.insertOrUpdate(match { it.dateString == today && it.totalUnlockCount == 1}) }
        coVerify { mockDailyAppUsageDao.insertOrUpdateUsage(match { it.packageName == "app1" && it.dateString == today && it.usageTimeMillis == 4000L }) }
    }

    @Test
    fun `aggregateUsage - app-to-app switch - calculates usage correctly`() = runTest {
        val time1 = 1000L
        val time2 = 2000L
        val time3 = 3000L
        val periodEnd = 4000L
        val date = DateUtil.formatUtcTimestampToLocalDateString(time1)

        val events = listOf(
            createRawEvent("app1", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, time1),
            createRawEvent("app1", RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, time2),
            createRawEvent("app2", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, time2),
            createRawEvent("app2", RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, time3)
        )
        coEvery { mockAppMetadataRepository.getAllMetadata() } returns emptyList()

        val result = repository.aggregateUsage(events, periodEnd)

        val app1Usage = result[Pair("app1", date)]?.first
        assertThat(app1Usage).isEqualTo(1000L)

        val app2Usage = result[Pair("app2", date)]?.first
        assertThat(app2Usage).isEqualTo(1000L)
    }

    @Test
    fun `aggregateUsage - app filtered out - usage is zero`() = runTest {
        val time1 = 1000L
        val time2 = 2000L
        val periodEnd = 3000L
        val date = DateUtil.formatUtcTimestampToLocalDateString(time1)
        val filteredAppPkg = "com.filtered.app"

        val events = listOf(
            createRawEvent(filteredAppPkg, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, time1),
            createRawEvent(filteredAppPkg, RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, time2)
        )
        coEvery { mockAppMetadataRepository.getAllMetadata() } returns listOf(createAppMeta(filteredAppPkg, isUserVisible = false))

        val result = repository.aggregateUsage(events, periodEnd)
        assertThat(result[Pair(filteredAppPkg, date)]).isNull()
    }


    @Test
    fun `aggregateUsage - active time calculation with interactions`() = runTest {
        val sessionStart = 1000L
        val interaction1 = sessionStart + 100L
        val interaction2 = interaction1 + (AppConstants.ACTIVE_TIME_INTERACTION_WINDOW_MS / 2)
        val interactionFar = interaction2 + AppConstants.ACTIVE_TIME_INTERACTION_WINDOW_MS + 500L
        val sessionEnd = interactionFar + AppConstants.ACTIVE_TIME_INTERACTION_WINDOW_MS + 100L
        val periodEnd = sessionEnd + 1000L
        val date = DateUtil.formatUtcTimestampToLocalDateString(sessionStart)

        val events = mutableListOf(
            createRawEvent("app1", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, sessionStart),
            createRawEvent("app1", RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_CLICKED, interaction1),
            createRawEvent("app1", RawAppEvent.EVENT_TYPE_ACCESSIBILITY_TYPING, interaction2),
            createRawEvent("app1", RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_FOCUSED, interactionFar),
            createRawEvent("app1", RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, sessionEnd)
        )
        coEvery { mockAppMetadataRepository.getAllMetadata() } returns emptyList()

        val result = repository.aggregateUsage(events, periodEnd)
        val (totalUsage, activeUsage) = result[Pair("app1", date)] ?: (0L to 0L)

        assertThat(totalUsage).isEqualTo(sessionEnd - sessionStart)

        val expectedActiveBlock1Duration = (interaction2 + AppConstants.ACTIVE_TIME_INTERACTION_WINDOW_MS) - interaction1
        val expectedActiveBlock2Duration = AppConstants.ACTIVE_TIME_INTERACTION_WINDOW_MS
        val expectedTotalActiveUsage = expectedActiveBlock1Duration + expectedActiveBlock2Duration

        assertThat(activeUsage).isEqualTo(expectedTotalActiveUsage)
    }

    @Test
    fun `getDeviceSummaryForDate for today - combines persisted and live data correctly`() = runTest {
        val today = DateUtil.getCurrentLocalDateString()
        val persistedSummary = DailyDeviceSummary(today, 5, 10, System.currentTimeMillis(), 20, 1000L)
        val liveUnlocks = 7
        val liveFirstUnlock = 2000L
        val liveNotifications = 12
        val expectedLiveAppOpens = 3

        every { mockDailyDeviceSummaryDao.getSummaryForDate(today) } returns flowOf(persistedSummary)
        every { mockRawAppEventDao.countEventsOfTypeForDate(today, RawAppEvent.EVENT_TYPE_USER_PRESENT) } returns flowOf(liveUnlocks)
        every { mockRawAppEventDao.getFirstEventTimestampOfTypeForDate(today, RawAppEvent.EVENT_TYPE_USER_PRESENT) } returns flowOf(liveFirstUnlock)
        every { mockNotificationDao.getNotificationCountForDate(today) } returns flowOf(liveNotifications)

        val resumeEventsForAppOpenCalc = listOf(
            createRawEvent("appA", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 1000L),
            createRawEvent("appA", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 1000L + AppConstants.CONTEXTUAL_APP_OPEN_DEBOUNCE_MS - 100),
            createRawEvent("appA", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 1000L + AppConstants.CONTEXTUAL_APP_OPEN_DEBOUNCE_MS + 1),
            createRawEvent("appB", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 2000L + AppConstants.CONTEXTUAL_APP_OPEN_DEBOUNCE_MS * 2)
        )
        every { mockRawAppEventDao.getEventsOfTypeForDate(today, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED) } returns flowOf(resumeEventsForAppOpenCalc)
        coEvery { mockAppMetadataRepository.getAllMetadata() } returns emptyList()

        val resultFlow = repository.getDeviceSummaryForDate(today)
        val result = resultFlow.first()

        assertThat(result).isNotNull()
        assertThat(result!!.dateString).isEqualTo(today)
        assertThat(result.totalUnlockCount).isEqualTo(liveUnlocks)
        assertThat(result.firstUnlockTimestampUtc).isEqualTo(liveFirstUnlock)
        assertThat(result.totalNotificationCount).isEqualTo(liveNotifications)
        assertThat(result.totalAppOpens).isEqualTo(expectedLiveAppOpens)
    }

    @Test
    fun `getDeviceSummaryForDate for past day - returns from DAO`() = runTest {
        val pastDate = "2023-01-01"
        val expectedSummary = DailyDeviceSummary(pastDate, 3, 8, 123L, 15, 12345L)
        every { mockDailyDeviceSummaryDao.getSummaryForDate(pastDate) } returns flowOf(expectedSummary)

        val result = repository.getDeviceSummaryForDate(pastDate).first()
        assertThat(result).isEqualTo(expectedSummary)
        verify { mockDailyDeviceSummaryDao.getSummaryForDate(pastDate) }
        verify(exactly = 0) { mockRawAppEventDao.countEventsOfTypeForDate(any(), any()) }
    }

    @Test
    fun `backfillHistoricalAppUsageData - processes days and saves data`() = runTest {
        val numDays = 2
        val todayCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val day1Cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { add(Calendar.DAY_OF_YEAR, -1) }

        val day1DateStr = DateUtil.formatDateToYyyyMmDdString(day1Cal.time)
        val day2DateStr = DateUtil.formatDateToYyyyMmDdString(todayCal.time)

        val startOfDay1 = DateUtil.getStartOfDayUtcMillis(day1DateStr)
        val startOfDay2 = DateUtil.getStartOfDayUtcMillis(day2DateStr)

        val day1SysEvent1 = createUsageStatsEvent("app1", "Act1", UsageEvents.Event.ACTIVITY_RESUMED, startOfDay1 + 100)
        val day1SysEvent2 = createUsageStatsEvent("app1", "Act1", UsageEvents.Event.ACTIVITY_PAUSED, startOfDay1 + 200) // 100ms usage
        shadowUsageStatsManager.addEvent(day1SysEvent1)
        shadowUsageStatsManager.addEvent(day1SysEvent2)

        val day2SysEvent1 = createUsageStatsEvent("app2", "Act2", UsageEvents.Event.ACTIVITY_RESUMED, startOfDay2 + 300)
        val day2SysEvent2 = createUsageStatsEvent("app2", "Act2", UsageEvents.Event.ACTIVITY_PAUSED, startOfDay2 + 500) // 200ms usage
        shadowUsageStatsManager.addEvent(day2SysEvent1)
        shadowUsageStatsManager.addEvent(day2SysEvent2)

        coEvery { mockRawAppEventDao.getEventsForDate(day1DateStr) } returns emptyList()
        coEvery { mockRawAppEventDao.getEventsForDate(day2DateStr) } returns emptyList()

        coEvery { mockNotificationDao.getNotificationsForDateList(any()) } returns emptyList()
        coEvery { mockNotificationDao.getNotificationCountForDateImmediate(any()) } returns 0


        val success = repository.backfillHistoricalAppUsageData(numDays)
        assertThat(success).isTrue()

        coVerify { mockDailyDeviceSummaryDao.insertOrUpdate(match { it.dateString == day1DateStr }) }
        coVerify { mockDailyAppUsageDao.insertOrUpdateUsage(match { it.packageName == "app1" && it.dateString == day1DateStr && it.usageTimeMillis == 100L }) }

        coVerify { mockDailyDeviceSummaryDao.insertOrUpdate(match { it.dateString == day2DateStr }) }
        coVerify { mockDailyAppUsageDao.insertOrUpdateUsage(match { it.packageName == "app2" && it.dateString == day2DateStr && it.usageTimeMillis == 200L }) }
    }
}
