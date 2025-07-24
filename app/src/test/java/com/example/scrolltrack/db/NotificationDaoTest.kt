package com.example.scrolltrack.db

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.example.scrolltrack.data.NotificationCountPerApp
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private val notification1 = NotificationRecord(notificationKey = "key1", packageName = "com.app.one", postTimeUTC = 1000L, dateString = "2024-01-01", category = "social", title = null, text = null)
    private val notification2 = NotificationRecord(notificationKey = "key2", packageName = "com.app.one", postTimeUTC = 2000L, dateString = "2024-01-01", category = "social", title = null, text = null)
    private val notification3 = NotificationRecord(notificationKey = "key3", packageName = "com.app.two", postTimeUTC = 3000L, dateString = "2024-01-01", category = "promo", title = null, text = null)
    private val notification4 = NotificationRecord(notificationKey = "key4", packageName = "com.app.three", postTimeUTC = 4000L, dateString = "2024-01-02", category = "social", title = null, text = null)
    private val notification_null_category = NotificationRecord(notificationKey = "key5", packageName = "com.app.four", postTimeUTC = 5000L, dateString = "2024-01-01", category = null, title = null, text = null)

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.notificationDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `insert and getNotificationsForDateList work correctly`() = runTest {
        dao.insert(notification1)
        dao.insert(notification2)
        dao.insert(notification4) // different date

        val notifications = dao.getNotificationsForDateList("2024-01-01")
        assertThat(notifications).hasSize(2)
        
        // Fix: Compare the content, ignoring the auto-generated IDs.
        val retrievedWithoutIds = notifications.map { it.copy(id = 0) }
        val expectedOriginals = listOf(notification1, notification2)
        assertThat(retrievedWithoutIds).containsExactlyElementsIn(expectedOriginals)
    }

    @Test
    fun `updateRemovalReason updates the correct record`() = runTest {
        dao.insert(notification1)
        dao.updateRemovalReason(notification1.packageName, notification1.postTimeUTC, 1)

        val updatedNotification = dao.getNotificationsForDateList("2024-01-01").first()
        assertThat(updatedNotification.removalReason).isEqualTo(1)
    }

    @Test
    fun `getNotificationCountPerAppForDate groups by package correctly`() = runTest {
        dao.insert(notification1) // app one
        dao.insert(notification2) // app one
        dao.insert(notification3) // app two
        dao.insert(notification4) // app three, different date

        val counts = dao.getNotificationCountsPerAppForDate("2024-01-01")

        assertThat(counts).hasSize(2)
        assertThat(counts).containsExactly(
            NotificationCountPerApp("com.app.one", 2),
            NotificationCountPerApp("com.app.two", 1)
        ).inOrder()
    }

    @Test
    fun `deleteNotificationsForDate removes correct records`() = runTest {
        dao.insert(notification1)
        dao.insert(notification4)

        dao.deleteNotificationsOlderThan(3000L)

        val remaining = dao.getNotificationsForDateList("2024-01-02")
        assertThat(remaining).hasSize(1)
        assertThat(dao.getNotificationsForDateList("2024-01-01")).isEmpty()
    }
}
