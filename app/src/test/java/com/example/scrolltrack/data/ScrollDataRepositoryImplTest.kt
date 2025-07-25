package com.example.scrolltrack.data

import android.app.usage.UsageEvents
import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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

        val app1 = "com.app.one"
        val app2 = "com.app.two"
        val filteredApp = "com.system.background"

        val events = listOf(
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, startOfDay + 500),
            createRawEvent(app1, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + 1000), // Open 1 for App1
            createRawEvent(app1, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, startOfDay + 2000, scrollDeltaY = 50),
            createRawEvent(app1, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, startOfDay + 3000, scrollDeltaY = 50),
            createRawEvent(app1, RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, startOfDay + 5000),
            createRawEvent(app2, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + 6000), // Open 1 for App2 (quick switch, not counted)
            createRawEvent(app2, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, startOfDay + 7000, scrollDeltaX = 50),
            createRawEvent(app2, RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, startOfDay + 9000),
            createRawEvent(filteredApp, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + 10000),
            createRawEvent(filteredApp, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, startOfDay + 11000, scrollDeltaY = 1000),
            createRawEvent(filteredApp, RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED, startOfDay + 12000)
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
        assertThat(app1Usage?.appOpenCount).isEqualTo(1) // Only the first open should be counted
        assertThat(app1Usage?.notificationCount).isEqualTo(2)

        assertThat(app2Usage?.usageTimeMillis).isEqualTo(3000L)
        assertThat(app2Usage?.activeTimeMillis).isGreaterThan(0L)
        assertThat(app2Usage?.appOpenCount).isEqualTo(0) // This should not be counted as an open due to debounce
        assertThat(app2Usage?.notificationCount).isEqualTo(1)

        val summary = dailyDeviceSummaryDao.getSummaryForDate(date).first()
        assertThat(summary).isNotNull()
        assertThat(summary?.dateString).isEqualTo(date)
        assertThat(summary?.totalUnlockCount).isEqualTo(1)
        assertThat(summary?.totalAppOpens).isEqualTo(1)
        assertThat(summary?.totalNotificationCount).isEqualTo(3)
        assertThat(summary?.totalUsageTimeMillis).isEqualTo(9000L)
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

    // --- Unlock Session Logic Tests ---
    @Test
    fun `processUnlockEvents - glance session - correctly identifies glance`() = runTest {
        val date = "2024-01-20"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val unlockTime = startOfDay + 1000L
        val lockTime = unlockTime + AppConstants.MINIMUM_GLANCE_DURATION_MS - 1 // Just under the threshold

        val events = listOf(
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, unlockTime),
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE, lockTime)
        )

        repository.processUnlockEvents(events, emptyList(), emptySet(), setOf(RawAppEvent.EVENT_TYPE_USER_UNLOCKED), setOf(RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE))

        val sessions = unlockSessionDao.getUnlockSessionsForDate(date)
        assertThat(sessions).hasSize(1)
        assertThat(sessions.first().sessionType).isEqualTo("Glance")
    }

    @Test
    fun `processUnlockEvents - intentional session - correctly identifies intentional`() = runTest {
        val date = "2024-01-20"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val unlockTime = startOfDay + 1000L
        val lockTime = unlockTime + AppConstants.MINIMUM_GLANCE_DURATION_MS + 1 // Just over the threshold

        val events = listOf(
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, unlockTime),
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE, lockTime)
        )

        repository.processUnlockEvents(events, emptyList(), emptySet(), setOf(RawAppEvent.EVENT_TYPE_USER_UNLOCKED), setOf(RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE))

        val sessions = unlockSessionDao.getUnlockSessionsForDate(date)
        assertThat(sessions).hasSize(1)
        assertThat(sessions.first().sessionType).isEqualTo("Intentional")
    }

    @Test
    fun `processUnlockEvents - compulsive check - correctly identifies compulsive`() = runTest {
        val date = "2024-01-20"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val unlockTime = startOfDay + 1000L
        val appResumeTime = unlockTime + 500L
        val lockTime = unlockTime + AppConstants.COMPULSIVE_UNLOCK_THRESHOLD_MS - 1
        val appA = "com.app.a"

        val events = listOf(
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, unlockTime),
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, appResumeTime),
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE, lockTime)
        )

        repository.processUnlockEvents(events, emptyList(), emptySet(), setOf(RawAppEvent.EVENT_TYPE_USER_UNLOCKED), setOf(RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE))

        val sessions = unlockSessionDao.getUnlockSessionsForDate(date)
        assertThat(sessions).hasSize(1)
        assertThat(sessions.first().isCompulsive).isTrue()
    }

    @Test
    fun `processUnlockEvents - notification driven - correctly identifies trigger`() = runTest {
        val date = "2024-01-20"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val appA = "com.app.a"
        val notificationTime = startOfDay + 1000L
        val unlockTime = notificationTime + AppConstants.NOTIFICATION_UNLOCK_WINDOW_MS - 1
        val appResumeTime = unlockTime + 500L
        val lockTime = unlockTime + 5000L

        val events = listOf(
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, unlockTime),
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, appResumeTime),
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE, lockTime)
        )
        val notifications = listOf(
            NotificationRecord(notificationKey = "key1", packageName = appA, postTimeUTC = notificationTime, dateString = date, title = "title", text = "text", category = "cat")
        )

        repository.processUnlockEvents(events, notifications, emptySet(), setOf(RawAppEvent.EVENT_TYPE_USER_UNLOCKED), setOf(RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE))

        val sessions = unlockSessionDao.getUnlockSessionsForDate(date)
        assertThat(sessions).hasSize(1)
        assertThat(sessions.first().triggeringNotificationPackageName).isEqualTo(appA)
    }

    // --- Active Time Calculation Tests ---
    @Test
    fun `calculateActiveTimeFromInteractions - merges overlapping intervals`() = runTest {
        val sessionStart = 10000L
        val scrollTime = sessionStart + 1000L // Active window: [11000, 14000]
        val tapTime = sessionStart + 2000L    // Active window: [12000, 14000] (fully overlapped by scroll and type)
        val typeTime = sessionStart + 3500L   // Active window: [13500, 21500] (partially overlaps scroll)
        val sessionEnd = sessionStart + 25000L

        val events = listOf(
            createRawEvent("app", RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, scrollTime), // Window: 3000ms
            createRawEvent("app", RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_CLICKED, tapTime), // Window: 2000ms
            createRawEvent("app", RawAppEvent.EVENT_TYPE_ACCESSIBILITY_TYPING, typeTime) // Window: 8000ms
        )

        // Make the method accessible for testing
        val activeTime = repository.calculateActiveTimeFromInteractions(events, sessionStart, sessionEnd)

        // Expected merged interval: [11000, 21500]. Duration is 10500ms.
        assertThat(activeTime).isEqualTo(10500L)
    }

    @Test
    fun `calculateActiveTimeFromInteractions - caps time at session boundaries`() = runTest {
        val sessionStart = 10000L
        val sessionEnd = 12000L // A very short session
        val scrollTime = sessionStart + 1000L // Active window: [11000, 14000]

        val events = listOf(
            createRawEvent("app", RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, scrollTime)
        )

        val activeTime = repository.calculateActiveTimeFromInteractions(events, sessionStart, sessionEnd)

        // The active window [11000, 14000] should be clipped to the session boundary [10000, 12000].
        // The effective interval is [11000, 12000], duration is 1000ms.
        assertThat(activeTime).isEqualTo(1000L)
    }

    // --- Insight Generation Tests ---

    @Test
    fun `generateInsights - night owl - correctly identifies last app used after midnight`() = runTest {
        val date = "2024-01-20"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val nightOwlTime = startOfDay + TimeUnit.HOURS.toMillis(2) // 2 AM
        val appA = "com.night.app"

        val events = listOf(
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, nightOwlTime)
        )

        val insights = repository.generateInsights(date, emptyList(), events, emptySet())

        val nightOwlInsight = insights.find { it.insightKey == "night_owl_last_app" }
        assertThat(nightOwlInsight).isNotNull()
        assertThat(nightOwlInsight?.stringValue).isEqualTo(appA)
        assertThat(nightOwlInsight?.longValue).isEqualTo(nightOwlTime)
    }

    @Test
    fun `generateInsights - first app used - finds first app after first unlock`() = runTest {
        val date = "2024-01-20"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val firstUnlockTime = startOfDay + TimeUnit.HOURS.toMillis(7)
        val firstAppTime = firstUnlockTime + 1000L
        val appA = "com.morning.app"

        val events = listOf(
            createRawEvent("app", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + 1000), // Before unlock, should be ignored
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, firstAppTime)      // After unlock, this is the one
        )
        val unlockSessions = listOf(
            UnlockSessionRecord(id=1, unlockTimestamp = firstUnlockTime, dateString = date, unlockEventType = "TEST")
        )

        val insights = repository.generateInsights(date, unlockSessions, events, emptySet())

        val firstAppInsight = insights.find { it.insightKey == "first_app_used" }
        assertThat(firstAppInsight).isNotNull()
        assertThat(firstAppInsight?.stringValue).isEqualTo(appA)
        assertThat(firstAppInsight?.longValue).isEqualTo(firstAppTime)
    }

    // --- System Event Mapping Tests ---
    @Test
    fun `mapUsageEventToRawAppEvent - maps all event types correctly`() {
        val testTime = System.currentTimeMillis()
        val testPkg = "com.test.package"
        val testCls = "com.test.package.TestClass"

        // Helper to create a real UsageEvents.Event using reflection, as it's a final class with private fields.
        fun createUsageEvent(pkg: String, cls: String, ts: Long, type: Int): UsageEvents.Event {
            val event = UsageEvents.Event()
            val eventClass = UsageEvents.Event::class.java

            // Use reflection to set private fields. This is brittle but necessary for this final class.
            try {
                val packageField = eventClass.getDeclaredField("mPackageName") // The actual field name in Android S+
                packageField.isAccessible = true
                packageField.set(event, pkg)
            } catch (e: NoSuchFieldException) {
                // Fallback for older Android versions if the field name is different
                val packageField = eventClass.getDeclaredField("mPackage")
                packageField.isAccessible = true
                packageField.set(event, pkg)
            }

            val classField = eventClass.getDeclaredField("mClass")
            classField.isAccessible = true
            classField.set(event, cls)

            val timeStampField = eventClass.getDeclaredField("mTimeStamp")
            timeStampField.isAccessible = true
            timeStampField.set(event, ts)

            val eventTypeField = eventClass.getDeclaredField("mEventType")
            eventTypeField.isAccessible = true
            eventTypeField.set(event, type)

            return event
        }

        // Test each mapping by creating a new event each time
        val eventResume = createUsageEvent(testPkg, testCls, testTime, UsageEvents.Event.ACTIVITY_RESUMED)
        val mappedResume = repository.mapUsageEventToRawAppEvent(eventResume)
        assertThat(mappedResume?.eventType).isEqualTo(RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED)

        val eventPause = createUsageEvent(testPkg, testCls, testTime, UsageEvents.Event.ACTIVITY_PAUSED)
        val mappedPause = repository.mapUsageEventToRawAppEvent(eventPause)
        assertThat(mappedPause?.eventType).isEqualTo(RawAppEvent.EVENT_TYPE_ACTIVITY_PAUSED)

        val eventScreenOn = createUsageEvent(testPkg, testCls, testTime, UsageEvents.Event.SCREEN_INTERACTIVE)
        val mappedScreenOn = repository.mapUsageEventToRawAppEvent(eventScreenOn)
        assertThat(mappedScreenOn?.eventType).isEqualTo(RawAppEvent.EVENT_TYPE_SCREEN_INTERACTIVE)

        val eventUnknown = createUsageEvent(testPkg, testCls, testTime, -1) // Unknown event
        val mappedUnknown = repository.mapUsageEventToRawAppEvent(eventUnknown)
        assertThat(mappedUnknown).isNull()
    }

    @Test
    fun `processScrollEvents - inferred only - creates INFERRED session`() = runTest {
        val appA = "com.app.a"
        val events = listOf(
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_SCROLL_INFERRED, 1000, scrollDeltaY = 100)
        )

        val sessions = repository.processScrollEvents(events, emptySet())

        assertThat(sessions).hasSize(1)
        assertThat(sessions.first().packageName).isEqualTo(appA)
        assertThat(sessions.first().dataType).isEqualTo("INFERRED")
    }

    @Test
    fun `processScrollEvents - mixed events for one app - prioritizes MEASURED`() = runTest {
        val appA = "com.app.a"
        val events = listOf(
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1000, scrollDeltaY = 100),
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_SCROLL_INFERRED, 2000, scrollDeltaY = 500) // This should be ignored
        )

        val sessions = repository.processScrollEvents(events, emptySet())

        assertThat(sessions).hasSize(1)
        assertThat(sessions.first().packageName).isEqualTo(appA)
        assertThat(sessions.first().dataType).isEqualTo("MEASURED")
        assertThat(sessions.first().scrollAmountY).isEqualTo(100)
    }

    @Test
    fun `processScrollEvents - mixed events for multiple apps - handles each app independently`() = runTest {
        val appA = "com.app.a" // Has mixed events
        val appB = "com.app.b" // Has inferred only
        val events = listOf(
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1000, scrollDeltaY = 100),
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_SCROLL_INFERRED, 2000, scrollDeltaY = 500),
            createRawEvent(appB, RawAppEvent.EVENT_TYPE_SCROLL_INFERRED, 3000, scrollDeltaY = 200)
        )

        val sessions = repository.processScrollEvents(events, emptySet())

        assertThat(sessions).hasSize(2)
        val sessionA = sessions.find { it.packageName == appA }
        val sessionB = sessions.find { it.packageName == appB }

        assertThat(sessionA).isNotNull()
        assertThat(sessionA!!.dataType).isEqualTo("MEASURED")
        assertThat(sessionA.scrollAmountY).isEqualTo(100)

        assertThat(sessionB).isNotNull()
        assertThat(sessionB!!.dataType).isEqualTo("INFERRED")
        assertThat(sessionB.scrollAmountY).isEqualTo(200)
    }

    // --- Phase 3: Scroll Calculation & Session Merging Tests ---

    @Test
    fun `processScrollEvents - measured scroll - correctly calculates and merges session`() = runTest {
        val appA = "com.app.a"
        val events = listOf(
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1000, scrollDeltaX = 20, scrollDeltaY = 100),
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1500, scrollDeltaX = 30, scrollDeltaY = 150)
        )

        val sessions = repository.processScrollEvents(events, emptySet())

        assertThat(sessions).hasSize(1)
        val session = sessions.first()
        assertThat(session.packageName).isEqualTo(appA)
        assertThat(session.sessionStartTime).isEqualTo(1000)
        assertThat(session.sessionEndTime).isEqualTo(1500)
        assertThat(session.scrollAmountX).isEqualTo(50)
        assertThat(session.scrollAmountY).isEqualTo(250)
        assertThat(session.scrollAmount).isEqualTo(300)
    }

    @Test
    fun `processScrollEvents - inferred scroll - correctly calculates and merges session`() = runTest {
        val appB = "com.app.b"
        val events = listOf(
            createRawEvent(appB, RawAppEvent.EVENT_TYPE_SCROLL_INFERRED, 1000, scrollDeltaY = 50),
            createRawEvent(appB, RawAppEvent.EVENT_TYPE_SCROLL_INFERRED, 1500, scrollDeltaY = 60),
            createRawEvent(appB, RawAppEvent.EVENT_TYPE_SCROLL_INFERRED, 2000, scrollDeltaY = 70)
        )

        val sessions = repository.processScrollEvents(events, emptySet())

        assertThat(sessions).hasSize(1)
        val session = sessions.first()
        assertThat(session.packageName).isEqualTo(appB)
        assertThat(session.scrollAmountX).isEqualTo(0)
        assertThat(session.scrollAmountY).isEqualTo(180)
        assertThat(session.scrollAmount).isEqualTo(180)
    }

    @Test
    fun `processScrollEvents - session breaks due to time gap`() = runTest {
        val appA = "com.app.a"
        val events = listOf(
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1000, scrollDeltaY = 100),
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1000 + AppConstants.SESSION_MERGE_GAP_MS + 1, scrollDeltaY = 100)
        )

        val sessions = repository.processScrollEvents(events, emptySet())

        assertThat(sessions).hasSize(2)
    }

    @Test
    fun `processScrollEvents - session breaks due to different app`() = runTest {
        val appA = "com.app.a"
        val appB = "com.app.b"
        val events = listOf(
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1000, scrollDeltaY = 100),
            createRawEvent(appB, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1500, scrollDeltaY = 100)
        )

        val sessions = repository.processScrollEvents(events, emptySet())

        assertThat(sessions).hasSize(2)
    }

    // --- calculateAppOpens Tests ---

    @Test
    fun `calculateAppOpens - app resumed after unlock - counts as one open`() = runTest {
        val appA = "com.app.a"
        val events = listOf(
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, 1000),
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 2000)
        )

        val appOpens = repository.calculateAppOpens(events)

        assertThat(appOpens[appA]).isEqualTo(1)
    }

    @Test
    fun `calculateAppOpens - app resumed after home - counts as one open`() = runTest {
        val appA = "com.app.a"
        val events = listOf(
            createRawEvent("android.launcher", RawAppEvent.EVENT_TYPE_RETURN_TO_HOME, 1000),
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 2000)
        )

        val appOpens = repository.calculateAppOpens(events)

        assertThat(appOpens[appA]).isEqualTo(1)
    }


    @Test
    fun `calculateAppOpens - rapid app switching - debounces correctly`() = runTest {
        val appA = "com.app.a"
        val appB = "com.app.b"
        val events = listOf(
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 1000), // First open -> A=1
            createRawEvent(appB, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 1500), // Rapid switch, should not count as new open
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 2000), // Rapid switch back
            createRawEvent(appB, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 2000 + AppConstants.CONTEXTUAL_APP_OPEN_DEBOUNCE_MS + 1) // Long pause, should count -> B=1
        )

        val appOpens = repository.calculateAppOpens(events)

        assertThat(appOpens[appA]).isEqualTo(1)
        assertThat(appOpens[appB]).isEqualTo(1)
    }

    @Test
    fun `calculateAppOpens - no open on quick return`() = runTest {
        val appA = "com.app.a"
        val appB = "com.app.b"
        val events = listOf(
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 1000), // Open A
            createRawEvent(appB, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 2000), // Switch to B
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 3000)  // Switch back to A
        )

        val appOpens = repository.calculateAppOpens(events)

        assertThat(appOpens.getOrDefault(appA, 0)).isEqualTo(1)
        assertThat(appOpens.getOrDefault(appB, 0)).isEqualTo(0)
    }

    @Test
    fun `calculateAppOpens - first event is resume - counts as open`() = runTest {
        val appA = "com.app.a"
        val events = listOf(
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, 1000)
        )
        val appOpens = repository.calculateAppOpens(events)
        assertThat(appOpens[appA]).isEqualTo(1)
    }

    // --- generateInsights Tests ---

    @Test
    fun `generateInsights - busiest hour - calculates correctly`() = runTest {
        val date = "2024-01-20"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val unlockSessions = listOf(
            UnlockSessionRecord(unlockTimestamp = startOfDay + 20 * 3600 * 1000, dateString = date, unlockEventType = "TEST"), // 8 PM
            UnlockSessionRecord(unlockTimestamp = startOfDay + 20 * 3600 * 1000 + 1, dateString = date, unlockEventType = "TEST"), // 8 PM
            UnlockSessionRecord(unlockTimestamp = startOfDay + 10 * 3600 * 1000, dateString = date, unlockEventType = "TEST")  // 10 AM
        )

        val insights = repository.generateInsights(date, unlockSessions, emptyList(), emptySet())

        val busiestHourInsight = insights.find { it.insightKey == "busiest_unlock_hour" }
        assertThat(busiestHourInsight).isNotNull()
        assertThat(busiestHourInsight?.longValue).isEqualTo(20)
    }

    @Test
    fun `generateInsights - top compulsive app - calculates correctly`() = runTest {
        val date = "2024-01-20"
        val unlockSessions = listOf(
            UnlockSessionRecord(dateString = date, unlockTimestamp = 1, isCompulsive = true, firstAppPackageName = "com.twitter.android", lockTimestamp = 2, durationMillis = 1, unlockEventType = "TEST"),
            UnlockSessionRecord(dateString = date, unlockTimestamp = 3, isCompulsive = true, firstAppPackageName = "com.twitter.android", lockTimestamp = 4, durationMillis = 1, unlockEventType = "TEST"),
            UnlockSessionRecord(dateString = date, unlockTimestamp = 5, isCompulsive = true, firstAppPackageName = "com.instagram.android", lockTimestamp = 6, durationMillis = 1, unlockEventType = "TEST")
        )

        val insights = repository.generateInsights(date, unlockSessions, emptyList(), emptySet())

        val topCompulsiveAppInsight = insights.find { it.insightKey == "top_compulsive_app" }
        assertThat(topCompulsiveAppInsight).isNotNull()
        assertThat(topCompulsiveAppInsight?.stringValue).isEqualTo("com.twitter.android")
        assertThat(topCompulsiveAppInsight?.longValue).isEqualTo(2)
    }

    @Test
    fun `generateInsights - various insights - calculates correctly`() = runTest {
        val date = "2024-01-20"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val unlockSessions = listOf(
            UnlockSessionRecord(unlockTimestamp = startOfDay + 1000, dateString = date, unlockEventType = "TEST", triggeringNotificationPackageName = "com.app.notify"),
            UnlockSessionRecord(unlockTimestamp = startOfDay + 80000000, dateString = date, unlockEventType = "TEST")
        )
        val events = listOf(
            createRawEvent("com.app.first", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + 2000), // First app after first unlock
            createRawEvent("com.app.night", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + TimeUnit.HOURS.toMillis(3)), // 3 AM, night owl
            createRawEvent("com.app.last", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + TimeUnit.HOURS.toMillis(22)) // 10 PM, last app
        )

        val insights = repository.generateInsights(date, unlockSessions, events, emptySet())

        assertThat(insights.find { it.insightKey == "first_unlock_time" }?.longValue).isEqualTo(startOfDay + 1000)
        assertThat(insights.find { it.insightKey == "last_unlock_time" }?.longValue).isEqualTo(startOfDay + 80000000)
        assertThat(insights.find { it.insightKey == "first_app_used" }?.stringValue).isEqualTo("com.app.first")
        assertThat(insights.find { it.insightKey == "last_app_used" }?.stringValue).isEqualTo("com.app.last")
        assertThat(insights.find { it.insightKey == "top_notification_unlock_app" }?.stringValue).isEqualTo("com.app.notify")
        assertThat(insights.find { it.insightKey == "night_owl_last_app" }?.stringValue).isEqualTo("com.app.night")
    }

    // --- processUnlockEvents Tests ---

    @Test
    fun `processUnlockEvents - glance vs intentional - flags correctly`() = runTest {
        val date = "2024-01-20"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val unlockSessionDaoSpy = spyk(unlockSessionDao)

        repository = ScrollDataRepositoryImpl(
            appDatabase = db,
            appMetadataRepository = mockAppMetadataRepository,
            scrollSessionDao = scrollSessionDao,
            dailyAppUsageDao = dailyAppUsageDao,
            rawAppEventDao = rawAppEventDao,
            notificationDao = notificationDao,
            dailyDeviceSummaryDao = dailyDeviceSummaryDao,
            unlockSessionDao = unlockSessionDaoSpy,
            dailyInsightDao = dailyInsightDao,
            context = context,
            ioDispatcher = UnconfinedTestDispatcher()
        )

        // Act & Assert for Glance session
        val glanceUnlockTime = startOfDay + 1000L
        val glanceLockTime = glanceUnlockTime + AppConstants.MINIMUM_GLANCE_DURATION_MS - 1
        val glanceEvents = listOf(
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, glanceUnlockTime),
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE, glanceLockTime)
        )
        repository.processUnlockEvents(glanceEvents, emptyList(), emptySet(), setOf(RawAppEvent.EVENT_TYPE_USER_UNLOCKED), setOf(RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE))

        val glanceSessions = unlockSessionDaoSpy.getUnlockSessionsForDate(date)
        assertThat(glanceSessions).hasSize(1)
        assertThat(glanceSessions.first().sessionType).isEqualTo("Glance")

        // Act & Assert for Intentional session
        val intentionalUnlockTime = startOfDay + 10000L
        val intentionalLockTime = intentionalUnlockTime + AppConstants.MINIMUM_GLANCE_DURATION_MS
        val intentionalEvents = listOf(
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, intentionalUnlockTime),
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE, intentionalLockTime)
        )
        repository.processUnlockEvents(intentionalEvents, emptyList(), emptySet(), setOf(RawAppEvent.EVENT_TYPE_USER_UNLOCKED), setOf(RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE))

        val allSessions = unlockSessionDaoSpy.getUnlockSessionsForDate(date)
        assertThat(allSessions).hasSize(2)
        assertThat(allSessions.find { it.unlockTimestamp == intentionalUnlockTime }?.sessionType).isEqualTo("Intentional")
    }

    @Test
    fun `processUnlockEvents - compulsive check - flags correctly`() = runTest {
        val date = "2024-01-20"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val appA = "com.app.a"
        val unlockTime = startOfDay + 1000L
        val lockTime = unlockTime + AppConstants.COMPULSIVE_UNLOCK_THRESHOLD_MS - 1
        val events = listOf(
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, unlockTime),
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, unlockTime + 500),
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE, lockTime)
        )

        // Act
        repository.processUnlockEvents(events, emptyList(), emptySet(), setOf(RawAppEvent.EVENT_TYPE_USER_UNLOCKED), setOf(RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE))

        // Assert
        val sessions = unlockSessionDao.getUnlockSessionsForDate(date)
        assertThat(sessions).hasSize(1)
        assertThat(sessions.first().isCompulsive).isTrue()
    }

    @Test
    fun `processUnlockEvents - non-compulsive short session`() = runTest {
        val date = "2024-01-20"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val unlockTime = startOfDay + 1000L
        val lockTime = unlockTime + AppConstants.COMPULSIVE_UNLOCK_THRESHOLD_MS - 1
        val appA = "com.app.a"
        val appB = "com.app.b"

        val events = listOf(
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, unlockTime),
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, unlockTime + 500),
            createRawEvent(appB, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, unlockTime + 1000),
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE, lockTime)
        )

        repository.processUnlockEvents(events, emptyList(), emptySet(), setOf(RawAppEvent.EVENT_TYPE_USER_UNLOCKED), setOf(RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE))

        val sessions = unlockSessionDao.getUnlockSessionsForDate(date)
        assertThat(sessions).hasSize(1)
        assertThat(sessions.first().isCompulsive).isFalse()
    }

    @Test
    fun `processUnlockEvents - notification driven - flags correctly`() = runTest {
        val date = "2024-01-20"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val appA = "com.app.a"
        val unlockTime = startOfDay + AppConstants.NOTIFICATION_UNLOCK_WINDOW_MS
        val lockTime = unlockTime + 10000L
        val events = listOf(
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, unlockTime),
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, unlockTime + 500),
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE, lockTime)
        )
        val notifications = listOf(
            NotificationRecord(notificationKey = "key", packageName = appA, postTimeUTC = unlockTime - 1000, dateString = date, title = "t", text = "t", category = "c")
        )

        // Act
        repository.processUnlockEvents(events, notifications, emptySet(), setOf(RawAppEvent.EVENT_TYPE_USER_UNLOCKED), setOf(RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE))

        // Assert
        val sessions = unlockSessionDao.getUnlockSessionsForDate(date)
        assertThat(sessions).hasSize(1)
        assertThat(sessions.first().triggeringNotificationPackageName).isEqualTo(appA)
    }

    // --- calculateActiveTimeFromInteractions Tests ---

    @Test
    fun `calculateActiveTime - no events - returns zero`() = runTest {
        val activeTime = repository.calculateActiveTimeFromInteractions(emptyList(), 0, 10000)
        assertThat(activeTime).isEqualTo(0)
    }

    @Test
    fun `calculateActiveTime - single scroll event - returns correct window`() = runTest {
        val events = listOf(
            createRawEvent("app", RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1000)
        )
        val activeTime = repository.calculateActiveTimeFromInteractions(events, 0, 10000)
        assertThat(activeTime).isEqualTo(AppConstants.ACTIVE_TIME_SCROLL_WINDOW_MS)
    }

    @Test
    fun `calculateActiveTime - overlapping windows - merges correctly`() = runTest {
        val events = listOf(
            createRawEvent("app", RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1000), // Window: [1000, 4000]
            createRawEvent("app", RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_CLICKED, 1500) // Window: [1500, 3500]
        )
        // Merged window should be [1000, 4000], total time is 3000ms.
        val expectedTotalTime = (1000 + AppConstants.ACTIVE_TIME_SCROLL_WINDOW_MS) - 1000
        val activeTime = repository.calculateActiveTimeFromInteractions(events, 0, 10000)
        assertThat(activeTime).isEqualTo(expectedTotalTime)
    }

    @Test
    fun `calculateActiveTime - session capping - caps time correctly`() = runTest {
        val events = listOf(
            createRawEvent("app", RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1000)
        )
        // Session ends before the active time window does
        val activeTime = repository.calculateActiveTimeFromInteractions(events, 500, 1000 + AppConstants.ACTIVE_TIME_SCROLL_WINDOW_MS / 2)
        assertThat(activeTime).isEqualTo(AppConstants.ACTIVE_TIME_SCROLL_WINDOW_MS / 2)
    }

    @Test
    fun `calculateActiveTime - different event types`() = runTest {
        val scrollEvent = createRawEvent("app", RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1000)
        val typeEvent = createRawEvent("app", RawAppEvent.EVENT_TYPE_ACCESSIBILITY_TYPING, 1000)
        val clickEvent = createRawEvent("app", RawAppEvent.EVENT_TYPE_ACCESSIBILITY_VIEW_CLICKED, 1000)

        val scrollActiveTime = repository.calculateActiveTimeFromInteractions(listOf(scrollEvent), 0, 10000)
        val typeActiveTime = repository.calculateActiveTimeFromInteractions(listOf(typeEvent), 0, 10000)
        val clickActiveTime = repository.calculateActiveTimeFromInteractions(listOf(clickEvent), 0, 10000)

        assertThat(scrollActiveTime).isEqualTo(AppConstants.ACTIVE_TIME_SCROLL_WINDOW_MS)
        assertThat(typeActiveTime).isEqualTo(AppConstants.ACTIVE_TIME_TYPE_WINDOW_MS)
        assertThat(clickActiveTime).isEqualTo(AppConstants.ACTIVE_TIME_TAP_WINDOW_MS)
    }

    @Test
    fun `generateInsights - no data scenarios`() = runTest {
        val insights = repository.generateInsights("2024-01-20", emptyList(), emptyList(), emptySet())
        assertThat(insights).isEmpty()
    }
}
