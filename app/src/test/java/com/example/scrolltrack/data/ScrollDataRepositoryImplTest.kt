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
import com.example.scrolltrack.db.UnlockSessionRecord

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

        val unlockTimestamp = startOfDay + 1000
        val lockTimestamp = startOfDay + 5000
        val events = listOf(
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_PRESENT, unlockTimestamp),
            createRawEvent(app1, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, unlockTimestamp + 500),
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE, lockTimestamp)
        )

        repository.processUnlockSessions(events, emptyList(), date, emptySet())

        val sessions = unlockSessionDao.getUnlockSessionsForDate(date)
        assertThat(sessions).hasSize(1)
        val session = sessions.first()
        assertThat(session.unlockTimestamp).isEqualTo(unlockTimestamp)
        assertThat(session.lockTimestamp).isEqualTo(lockTimestamp)
        assertThat(session.durationMillis).isEqualTo(4000)
        assertThat(session.firstAppPackageName).isEqualTo(app1)
    }

    @Test
    fun `processUnlockSessions - debounces multiple unlock events`() = runTest {
        val date = "2024-01-23"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val unlockTime = startOfDay + 1000

        val events = listOf(
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_INTERACTIVE, unlockTime),
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_PRESENT, unlockTime + 50), // Should be ignored
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, unlockTime + 100), // Should be the one recorded
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE, unlockTime + 5000)
        )

        repository.processUnlockSessions(events, emptyList(), date, emptySet())

        val sessions = unlockSessionDao.getUnlockSessionsForDate(date)
        assertThat(sessions).hasSize(1)
        assertThat(sessions.first().unlockEventType).isEqualTo(RawAppEvent.EVENT_TYPE_USER_UNLOCKED.toString())
    }

    @Test
    fun `processUnlockSessions - handles smart lock unlock (no KEYGUARD_HIDDEN)`() = runTest {
        val date = "2024-01-24"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val unlockTime = startOfDay + 1000

        // Smart lock: SCREEN_INTERACTIVE -> USER_PRESENT, but no explicit unlock/keyguard events
        val events = listOf(
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_INTERACTIVE, unlockTime),
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_PRESENT, unlockTime + 100),
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE, unlockTime + 5000)
        )

        repository.processUnlockSessions(events, emptyList(), date, emptySet())

        val sessions = unlockSessionDao.getUnlockSessionsForDate(date)
        assertThat(sessions).hasSize(1)
        // We expect it to be recorded, likely as USER_PRESENT since it's higher priority
        assertThat(sessions.first().unlockEventType).isEqualTo(RawAppEvent.EVENT_TYPE_USER_PRESENT.toString())
        assertThat(sessions.first().lockTimestamp).isNotNull()
    }

    @Test
    fun `processUnlockSessions - ignores lock screen peek (INTERACTIVE then KEYGUARD_SHOWN)`() = runTest {
        val date = "2024-01-25"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val peekTime = startOfDay + 1000

        val events = listOf(
            // User peeks at lock screen, it becomes interactive
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_INTERACTIVE, peekTime),
            // But then the keyguard is shown again, meaning they didn't unlock
            createRawEvent("android", RawAppEvent.EVENT_TYPE_KEYGUARD_SHOWN, peekTime + 200),
            // A real unlock happens later
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, peekTime + 10000),
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE, peekTime + 20000)
        )

        repository.processUnlockSessions(events, emptyList(), date, emptySet())

        val sessions = unlockSessionDao.getUnlockSessionsForDate(date)
        assertThat(sessions).hasSize(1)
        assertThat(sessions.first().unlockTimestamp).isEqualTo(peekTime + 10000) // Only the real unlock is counted
    }

    @Test
    fun `processUnlockSessions - only closes most recent open session`() = runTest {
        val date = "2024-01-26"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val session1UnlockTime = startOfDay + 1000
        val session2UnlockTime = startOfDay + 2000 // A second unlock before the first one is locked
        val lockTime = startOfDay + 5000

        // 1. Manually insert two open sessions
        unlockSessionDao.insert(UnlockSessionRecord(unlockTimestamp = session1UnlockTime, dateString = date, unlockEventType = "MANUAL"))
        unlockSessionDao.insert(UnlockSessionRecord(unlockTimestamp = session2UnlockTime, dateString = date, unlockEventType = "MANUAL"))

        // 2. Process a single lock event
        val events = listOf(
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE, lockTime)
        )
        repository.processUnlockSessions(events, emptyList(), date, emptySet())


        // 3. Assert that the second (most recent) session is closed
        val closedSession = unlockSessionDao.getUnlockSessionsForDate(date).find { it.unlockTimestamp == session2UnlockTime }
        assertThat(closedSession).isNotNull()
        assertThat(closedSession?.lockTimestamp).isEqualTo(lockTime)

        // 4. Assert that the first session remains open
        val stillOpenSession = unlockSessionDao.getUnlockSessionsForDate(date).find { it.unlockTimestamp == session1UnlockTime }
        assertThat(stillOpenSession).isNotNull()
        assertThat(stillOpenSession?.lockTimestamp).isNull()
    }

    @Test
    fun `processUnlockSessions - ignores lock event with no prior open session`() = runTest {
        val date = "2024-01-26"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val lockTime = startOfDay + 5000

        val events = listOf(
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE, lockTime)
        )

        repository.processUnlockSessions(events, emptyList(), date, emptySet())

        val sessions = unlockSessionDao.getUnlockSessionsForDate(date)
        assertThat(sessions).isEmpty() // No session should be created or closed
    }

    @Test
    fun `processUnlockSessions - associates unlock with recent notification`() = runTest {
        val date = "2024-01-27"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val app1 = "com.app.one"
        val notificationTime = startOfDay + 500
        val unlockTime = startOfDay + 1000 // 500ms after notification
        val lockTime = startOfDay + 10000

        val events = listOf(
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, unlockTime),
            createRawEvent(app1, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, unlockTime + 500),
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE, lockTime)
        )
        val notifications = listOf(
            NotificationRecord(
                notificationKey = "test_key_1",
                packageName = app1,
                postTimeUTC = notificationTime,
                dateString = date, title = "t", text = "t", category = "c"
            )
        )

        repository.processUnlockSessions(events, notifications, date, emptySet())

        val sessions = unlockSessionDao.getUnlockSessionsForDate(date)
        assertThat(sessions).hasSize(1)
        val session = sessions.first()
        assertThat(session.triggeringNotificationKey).isEqualTo("test_key_1")
        assertThat(session.firstAppPackageName).isEqualTo(app1)
    }

    @Test
    fun `processUnlockSessions - does not associate unlock with old notification`() = runTest {
        val date = "2024-01-28"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val app1 = "com.app.one"
        // Notification is well outside the association window
        val notificationTime = startOfDay + 500
        val unlockTime = startOfDay + 500 + AppConstants.NOTIFICATION_UNLOCK_WINDOW_MS + 1000
        val lockTime = unlockTime + 5000

        val events = listOf(
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, unlockTime),
            createRawEvent(app1, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, unlockTime + 500),
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE, lockTime)
        )
        val notifications = listOf(
            NotificationRecord(
                notificationKey = "test_key_old",
                packageName = app1,
                postTimeUTC = notificationTime,
                dateString = date, title = "t", text = "t", category = "c"
            )
        )

        repository.processUnlockSessions(events, notifications, date, emptySet())

        val sessions = unlockSessionDao.getUnlockSessionsForDate(date)
        assertThat(sessions).hasSize(1)
        assertThat(sessions.first().triggeringNotificationKey).isNull()
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

    private fun createNotificationRecord(pkg: String, key: String, timestamp: Long): NotificationRecord {
        return NotificationRecord(
            notificationKey = key,
            packageName = pkg,
            postTimeUTC = timestamp,
            title = "Test Title",
            text = "Test text",
            category = "social",
            dateString = DateUtil.formatUtcTimestampToLocalDateString(timestamp)
        )
    }

    // --- Tests for processScrollEvents ---

    @Test
    fun `processScrollEvents - single measured scroll event`() {
        val event = RawAppEvent(
            id = 1,
            packageName = "com.app.one",
            className = "Test",
            eventType = RawAppEvent.EVENT_TYPE_SCROLL_MEASURED,
            eventTimestamp = 1000L,
            eventDateString = "2024-01-01",
            source = RawAppEvent.SOURCE_ACCESSIBILITY,
            scrollDeltaX = 50,
            scrollDeltaY = 100
        )
        val result = repository.processScrollEvents(listOf(event), emptySet())
        assertThat(result).hasSize(1)
        with(result.first()) {
            assertThat(packageName).isEqualTo("com.app.one")
            assertThat(scrollAmount).isEqualTo(150L) // 50 + 100
            assertThat(scrollAmountX).isEqualTo(50L)
            assertThat(scrollAmountY).isEqualTo(100L)
            assertThat(dataType).isEqualTo("MEASURED")
        }
    }

    @Test
    fun `processScrollEvents - single inferred scroll event`() {
        val event = RawAppEvent(
            id = 1,
            packageName = "com.app.one",
            className = null,
            eventType = RawAppEvent.EVENT_TYPE_SCROLL_INFERRED,
            eventTimestamp = 1000L,
            eventDateString = "2024-01-01",
            source = RawAppEvent.SOURCE_ACCESSIBILITY,
            value = 250 // Inferred events use 'value'
        )
        val result = repository.processScrollEvents(listOf(event), emptySet())
        assertThat(result).hasSize(1)
        with(result.first()) {
            assertThat(packageName).isEqualTo("com.app.one")
            assertThat(scrollAmount).isEqualTo(250L)
            assertThat(scrollAmountX).isEqualTo(0L) // Inferred is Y-only for now
            assertThat(scrollAmountY).isEqualTo(250L)
            assertThat(dataType).isEqualTo("INFERRED")
        }
    }

    @Test
    fun `processScrollEvents - merges multiple measured events`() {
        val events = listOf(
            RawAppEvent(1, "com.app.one", "Test", RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1000L, "2024-01-01", RawAppEvent.SOURCE_ACCESSIBILITY, null, 10, 20),
            RawAppEvent(2, "com.app.one", "Test", RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 2000L, "2024-01-01", RawAppEvent.SOURCE_ACCESSIBILITY, null, 30, 40)
        )
        val result = repository.processScrollEvents(events, emptySet())
        assertThat(result).hasSize(1)
        with(result.first()) {
            assertThat(scrollAmount).isEqualTo(100L) // (10+20) + (30+40)
            assertThat(scrollAmountX).isEqualTo(40L)
            assertThat(scrollAmountY).isEqualTo(60L)
            assertThat(sessionStartTime).isEqualTo(1000L)
            assertThat(sessionEndTime).isEqualTo(2000L)
        }
    }

    @Test
    fun `processScrollEvents - merges multiple inferred events`() {
        val events = listOf(
            RawAppEvent(1, "com.app.one", null, RawAppEvent.EVENT_TYPE_SCROLL_INFERRED, 1000L, "2024-01-01", RawAppEvent.SOURCE_ACCESSIBILITY, 100),
            RawAppEvent(2, "com.app.one", null, RawAppEvent.EVENT_TYPE_SCROLL_INFERRED, 2000L, "2024-01-01", RawAppEvent.SOURCE_ACCESSIBILITY, 150)
        )
        val result = repository.processScrollEvents(events, emptySet())
        assertThat(result).hasSize(1)
        with(result.first()) {
            assertThat(scrollAmount).isEqualTo(250L)
            assertThat(dataType).isEqualTo("INFERRED")
        }
    }

    @Test
    fun `processScrollEvents - does not merge different packages`() {
        val events = listOf(
            RawAppEvent(1, "com.app.one", "Test", RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1000L, "2024-01-01", RawAppEvent.SOURCE_ACCESSIBILITY, null, 10, 20),
            RawAppEvent(2, "com.app.two", "Test", RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 2000L, "2024-01-01", RawAppEvent.SOURCE_ACCESSIBILITY, null, 30, 40)
        )
        val result = repository.processScrollEvents(events, emptySet())
        assertThat(result).hasSize(2)
    }

    @Test
    fun `processScrollEvents - does not merge different data types`() {
        val events = listOf(
            RawAppEvent(1, "com.app.one", "Test", RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1000L, "2024-01-01", RawAppEvent.SOURCE_ACCESSIBILITY, null, 10, 20),
            RawAppEvent(2, "com.app.one", null, RawAppEvent.EVENT_TYPE_SCROLL_INFERRED, 2000L, "2024-01-01", RawAppEvent.SOURCE_ACCESSIBILITY, 100)
        )
        val result = repository.processScrollEvents(events, emptySet())
        assertThat(result).hasSize(2)
    }

    @Test
    fun `processScrollEvents - does not merge if time gap is too large`() {
        val events = listOf(
            RawAppEvent(1, "com.app.one", "Test", RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1000L, "2024-01-01", RawAppEvent.SOURCE_ACCESSIBILITY, null, 10, 20),
            RawAppEvent(2, "com.app.one", "Test", RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1000L + AppConstants.SESSION_MERGE_GAP_MS + 1, "2024-01-01", RawAppEvent.SOURCE_ACCESSIBILITY, null, 30, 40)
        )
        val result = repository.processScrollEvents(events, emptySet())
        assertThat(result).hasSize(2)
    }

    @Test
    fun `processScrollEvents - ignores events with no scroll delta`() {
        val event = RawAppEvent(
            id = 1,
            packageName = "com.app.one",
            className = "Test",
            eventType = RawAppEvent.EVENT_TYPE_SCROLL_MEASURED,
            eventTimestamp = 1000L,
            eventDateString = "2024-01-01",
            source = RawAppEvent.SOURCE_ACCESSIBILITY,
            scrollDeltaX = 0,
            scrollDeltaY = 0
        )
        val result = repository.processScrollEvents(listOf(event), emptySet())
        assertThat(result).isEmpty()
    }

    @Test
    fun `processScrollEvents - complex scenario`() {
        val events = listOf(
            // App one measured session
            RawAppEvent(1, "com.app.one", "Test", RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1000L, "2024-01-01", RawAppEvent.SOURCE_ACCESSIBILITY, null, 10, 20), // 30
            RawAppEvent(2, "com.app.one", "Test", RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 2000L, "2024-01-01", RawAppEvent.SOURCE_ACCESSIBILITY, null, 30, 40), // 70 -> total 100
            // App one inferred session (should not merge)
            RawAppEvent(3, "com.app.one", null, RawAppEvent.EVENT_TYPE_SCROLL_INFERRED, 3000L, "2024-01-01", RawAppEvent.SOURCE_ACCESSIBILITY, 100),
            // App two measured session
            RawAppEvent(4, "com.app.two", "Test", RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 4000L, "2024-01-01", RawAppEvent.SOURCE_ACCESSIBILITY, null, 50, 50), // 100
            // App one measured again, but after long gap
            RawAppEvent(5, "com.app.one", "Test", RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 4000L + AppConstants.SESSION_MERGE_GAP_MS, "2024-01-01", RawAppEvent.SOURCE_ACCESSIBILITY, null, 5, 5) // 10
        )
        val result = repository.processScrollEvents(events, emptySet())
        assertThat(result).hasSize(4)

        val appOneMeasured1 = result.find { it.packageName == "com.app.one" && it.dataType == "MEASURED" && it.sessionStartTime == 1000L }
        assertThat(appOneMeasured1).isNotNull()
        assertThat(appOneMeasured1!!.scrollAmount).isEqualTo(100L)

        val appOneInferred = result.find { it.packageName == "com.app.one" && it.dataType == "INFERRED" }
        assertThat(appOneInferred).isNotNull()
        assertThat(appOneInferred!!.scrollAmount).isEqualTo(100L)

        val appTwoMeasured = result.find { it.packageName == "com.app.two" && it.dataType == "MEASURED" }
        assertThat(appTwoMeasured).isNotNull()
        assertThat(appTwoMeasured!!.scrollAmount).isEqualTo(100L)

        val appOneMeasured2 = result.find { it.packageName == "com.app.one" && it.dataType == "MEASURED" && it.sessionStartTime > 4000L }
        assertThat(appOneMeasured2).isNotNull()
        assertThat(appOneMeasured2!!.scrollAmount).isEqualTo(10L)
    }

}
