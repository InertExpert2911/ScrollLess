package com.example.scrolltrack.db

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.example.scrolltrack.data.NotificationCountPerApp
import com.example.scrolltrack.data.NotificationSummary
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
class NotificationDaoTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var dao: NotificationDao

    private val notification1 = NotificationRecord(notificationKey = "key1", packageName = "com.app.one", postTimeUTC = 1000L, dateString = "2024-01-01", category = "social", title = "t1", text = "t1")
    private val notification2 = NotificationRecord(notificationKey = "key2", packageName = "com.app.one", postTimeUTC = 2000L, dateString = "2024-01-01", category = "promo", title = "t2", text = "t2")
    private val notification3 = NotificationRecord(notificationKey = "key3", packageName = "com.app.two", postTimeUTC = 3000L, dateString = "2024-01-01", category = "social", title = "t3", text = "t3")
    private val notification4 = NotificationRecord(notificationKey = "key4", packageName = "com.app.two", postTimeUTC = 4000L, dateString = "2024-01-02", category = "social", title = "t4", text = "t4")

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.notificationDao()
        runTest { dao.insert(notification1); dao.insert(notification2); dao.insert(notification3); dao.insert(notification4) }
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `insert ignore works correctly`() = runTest {
        val initialCount = dao.getAllNotifications().first().size
        val duplicateNotif1 = notification1.copy(id = 5L) // same key
        dao.insert(duplicateNotif1)
        val finalCount = dao.getAllNotifications().first().size
        assertThat(finalCount).isEqualTo(initialCount)
    }

    @Test
    fun `getNotificationCountForDate returns correct count`() = runTest {
        dao.getNotificationCountForDate("2024-01-01").test {
            assertThat(awaitItem()).isEqualTo(3)
        }
    }

    @Test
    fun `getNotificationCountForDateImmediate returns correct count`() = runTest {
        val count = dao.getNotificationCountForDateImmediate("2024-01-01")
        assertThat(count).isEqualTo(3)
    }
    
    @Test
    fun `getNotificationsForDateList returns correct list`() = runTest {
        val notifications = dao.getNotificationsForDateList("2024-01-01")
        assertThat(notifications).hasSize(3)
        assertThat(notifications.map { it.notificationKey }).containsExactly("key1", "key2", "key3")
    }

    @Test
    fun `getNotificationSummaryForPeriod groups by category correctly`() = runTest {
        dao.getNotificationSummaryForPeriod("2024-01-01", "2024-01-01").test {
            val result = awaitItem()
            assertThat(result).containsExactly(
                NotificationSummary("social", 2),
                NotificationSummary("promo", 1)
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getNotificationCountPerAppForPeriod groups by package correctly`() = runTest {
        dao.getNotificationCountPerAppForPeriod("2024-01-01", "2024-01-02").test {
            val result = awaitItem()
            val expected = listOf(
                NotificationCountPerApp("com.app.one", 2),
                NotificationCountPerApp("com.app.two", 2)
            )
            assertThat(result).containsExactlyElementsIn(expected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteNotificationsOlderThan removes correct records`() = runTest {
        dao.deleteNotificationsOlderThan(5000L) // delete notif1, notif2, notif3, notif4
        val remaining = dao.getAllNotifications().first()
        assertThat(remaining).isEmpty()
    }

    @Test
    fun `updateRemovalReason updates the correct notification`() = runTest {
        val reason = 4 // REASON_LISTENER_CANCEL
        dao.updateRemovalReason(notification1.packageName, notification1.postTimeUTC, reason)

        val allNotifications = dao.getAllNotifications().first()
        val updatedNotification = allNotifications.find { it.notificationKey == "key1" }

        assertThat(updatedNotification?.removalReason).isEqualTo(reason)
    }
    
    @Test
    fun `getNotificationCountsPerAppForDate groups correctly`() = runTest {
        val result = dao.getNotificationCountsPerAppForDate("2024-01-01")

        val expected = listOf(
            NotificationCountPerApp("com.app.one", 2),
            NotificationCountPerApp("com.app.two", 1)
        )
        assertThat(result).containsExactlyElementsIn(expected)
    }
} 