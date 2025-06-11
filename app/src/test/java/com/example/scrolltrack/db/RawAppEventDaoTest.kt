package com.example.scrolltrack.db

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RawAppEventDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var dao: RawAppEventDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries() // Using for tests only
            .build()
        dao = database.rawAppEventDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertEvents_and_getEventsForPeriod_retrievesCorrectEvents() = runTest {
        // Timestamps for day "2023-10-26"
        val event1 = RawAppEvent(packageName = "com.app.one", className = "c1", eventType = 1, eventTimestamp = 1698314400000, eventDateString = "2023-10-26") // 10:00:00
        val event2 = RawAppEvent(packageName = "com.app.one", className = "c1", eventType = 2, eventTimestamp = 1698318000000, eventDateString = "2023-10-26") // 11:00:00
        // Timestamps for day "2023-10-27"
        val event3 = RawAppEvent(packageName = "com.app.two", className = "c2", eventType = 1, eventTimestamp = 1698393600000, eventDateString = "2023-10-27") // 08:00:00
        val event4 = RawAppEvent(packageName = "com.app.two", className = "c2", eventType = 2, eventTimestamp = 1698400800000, eventDateString = "2023-10-27") // 10:00:00

        dao.insertEvents(listOf(event1, event2, event3, event4))

        // Retrieve events for the first day
        val startTime1 = 1698314400000 // 10:00:00
        val endTime1 = 1698318000001   // 11:00:01 (exclusive in query)
        val result1 = dao.getEventsForPeriod(startTime1, endTime1)

        assertEquals(2, result1.size)
        assertEquals(event1.eventTimestamp, result1[0].eventTimestamp)
        assertEquals(event2.eventTimestamp, result1[1].eventTimestamp)

        // Retrieve events for the second day, but only the first event
        val startTime2 = 1698393600000 // 08:00:00
        val endTime2 = 1698397200000   // 09:00:00
        val result2 = dao.getEventsForPeriod(startTime2, endTime2)
        assertEquals(1, result2.size)
        assertEquals(event3.eventTimestamp, result2[0].eventTimestamp)
    }

    @Test
    fun getLatestEventTimestampForDate_returnsCorrectTimestamp() = runTest {
        val date = "2023-10-28"
        val event1 = RawAppEvent(packageName = "com.app.one", className = "c1", eventType = 1, eventTimestamp = 1698483600000, eventDateString = date) // 10:00:00
        val event2 = RawAppEvent(packageName = "com.app.one", className = "c1", eventType = 2, eventTimestamp = 1698487200000, eventDateString = date) // 11:00:00 (latest)
        val event3 = RawAppEvent(packageName = "com.app.two", className = "c2", eventType = 1, eventTimestamp = 1698480000000, eventDateString = date) // 09:00:00

        dao.insertEvents(listOf(event1, event2, event3))

        val latestTimestamp = dao.getLatestEventTimestampForDate(date)

        assertEquals(event2.eventTimestamp, latestTimestamp)
    }

    @Test
    fun getLatestEventTimestampForDate_returnsNull_whenNoEventsForDate() = runTest {
        val dateWithEvents = "2023-10-29"
        val dateWithoutEvents = "2023-10-30"
        val event1 = RawAppEvent(packageName = "com.app.one", className = "c1", eventType = 1, eventTimestamp = 1698570000000, eventDateString = dateWithEvents) // 10:00:00

        dao.insertEvent(event1)

        val latestTimestamp = dao.getLatestEventTimestampForDate(dateWithoutEvents)

        assertNull(latestTimestamp)
    }
} 