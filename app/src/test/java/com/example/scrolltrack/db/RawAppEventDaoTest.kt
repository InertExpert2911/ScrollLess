package com.example.scrolltrack.db

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class RawAppEventDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var dao: RawAppEventDao

    private val event1 = RawAppEvent(packageName = "com.app.one", className = "c1", eventType = 1, eventTimestamp = 1698314400000, eventDateString = "2023-10-26", source = "TEST") // 10:00:00
    private val event2 = RawAppEvent(packageName = "com.app.one", className = "c1", eventType = 2, eventTimestamp = 1698318000000, eventDateString = "2023-10-26", source = "TEST") // 11:00:00
    private val event3 = RawAppEvent(packageName = "com.app.two", className = "c2", eventType = 1, eventTimestamp = 1698393600000, eventDateString = "2023-10-27", source = "TEST") // 08:00:00
    private val event4 = RawAppEvent(packageName = "com.app.two", className = "c2", eventType = 2, eventTimestamp = 1698400800000, eventDateString = "2023-10-27", source = "TEST") // 10:00:00
    private val allEvents = listOf(event1, event2, event3, event4)

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
    fun `insertEvents and getEventsForPeriod retrieves correct events`() = runTest {
        dao.insertEvents(allEvents)

        // Retrieve events for the first day
        val result1 = dao.getEventsForPeriod(1698314400000, 1698318000000)
        assertThat(result1).hasSize(2)
        assertThat(result1.map { it.eventTimestamp }).containsExactly(event1.eventTimestamp, event2.eventTimestamp)

        // Retrieve events for the second day, but only the first event
        val result2 = dao.getEventsForPeriod(1698393600000, 1698397200000)
        assertThat(result2).hasSize(1)
        assertThat(result2.first().eventTimestamp).isEqualTo(event3.eventTimestamp)
    }

    @Test
    fun `getEventsForPeriodFlow emits correct events`() = runTest {
        dao.insertEvents(allEvents)

        dao.getEventsForPeriodFlow(1698314400000, 1698318000000).test {
            val items = awaitItem()
            assertThat(items).hasSize(2)
            assertThat(items.map { it.eventTimestamp }).containsExactly(event1.eventTimestamp, event2.eventTimestamp)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getEventsForDate retrieves all events for a given date string`() = runTest {
        dao.insertEvents(allEvents)
        val result = dao.getEventsForDate("2023-10-26")
        assertThat(result).hasSize(2)
        assertThat(result.map { it.eventTimestamp }).containsExactly(event1.eventTimestamp, event2.eventTimestamp)
    }

    @Test
    fun `getEventsOfTypeForPeriod retrieves correct events`() = runTest {
        dao.insertEvents(allEvents)
        val result = dao.getEventsOfTypeForPeriod(1, 1698300000000, 1698400000000)
        assertThat(result).hasSize(2)
        assertThat(result.map { it.eventTimestamp }).containsExactly(event1.eventTimestamp, event3.eventTimestamp)
    }

    @Test
    fun `countEventsOfTypeForDate counts correctly`() = runTest {
        dao.insertEvents(allEvents)
        dao.countEventsOfTypeForDate("2023-10-27", 2).test {
            assertThat(awaitItem()).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
        dao.countEventsOfTypeForDate("2023-10-27", 99).test {
            assertThat(awaitItem()).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getFirstEventTimestampOfTypeForDate gets correct timestamp`() = runTest {
        dao.insertEvents(allEvents)
        dao.getFirstEventTimestampOfTypeForDate("2023-10-27", 1).test {
            assertThat(awaitItem()).isEqualTo(event3.eventTimestamp)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getEventsOfTypeForDate gets correct events`() = runTest {
        dao.insertEvents(allEvents)
        dao.getEventsOfTypeForDate("2023-10-27", 1).test {
            val items = awaitItem()
            assertThat(items).hasSize(1)
            assertThat(items.first().eventTimestamp).isEqualTo(event3.eventTimestamp)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getLatestEventTimestampForDate returns correct timestamp`() = runTest {
        dao.insertEvents(allEvents)
        val latestTimestamp = dao.getLatestEventTimestampForDate("2023-10-26")
        assertThat(latestTimestamp).isEqualTo(event2.eventTimestamp)
    }

    @Test
    fun `getLatestEventTimestampForDate returns null when no events for date`() = runTest {
        dao.insertEvents(allEvents)
        val latestTimestamp = dao.getLatestEventTimestampForDate("2023-10-30")
        assertThat(latestTimestamp).isNull()
    }

    @Test
    fun `getLatestEventTimestamp returns overall latest timestamp`() = runTest {
        dao.insertEvents(allEvents)
        val latestTimestamp = dao.getLatestEventTimestamp()
        assertThat(latestTimestamp).isEqualTo(event4.eventTimestamp)
    }

    @Test
    fun `getLatestEventTimestamp returns null when database is empty`() = runTest {
        val latestTimestamp = dao.getLatestEventTimestamp()
        assertThat(latestTimestamp).isNull()
    }

    @Test
    fun `deleteEventsBefore removes old events`() = runTest {
        dao.insertEvents(allEvents)
        // Delete everything before event3
        dao.deleteEventsBefore(event3.eventTimestamp)
        val remainingEvents = dao.getEventsForPeriod(0, Long.MAX_VALUE)
        assertThat(remainingEvents).hasSize(2)
        assertThat(remainingEvents.map { it.eventTimestamp }).containsExactly(event3.eventTimestamp, event4.eventTimestamp)
    }
} 