package com.example.scrolltrack.services

import android.app.Notification as AndroidNotification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.service.notification.StatusBarNotification
import com.example.scrolltrack.db.NotificationDao
import com.example.scrolltrack.db.NotificationRecord
import com.example.scrolltrack.db.RawAppEvent
import com.example.scrolltrack.db.RawAppEventDao
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import android.content.Context
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P]) // Use a specific SDK version
class NotificationListenerTest {

    private lateinit var notificationListener: NotificationListener
    private val notificationDao: NotificationDao = mockk(relaxed = true)
    private val rawAppEventDao: RawAppEventDao = mockk(relaxed = true)
    private val mockContext: Context = spyk(RuntimeEnvironment.getApplication())

    @Before
    fun setUp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = mockContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel("default_channel", "Default", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        notificationListener = spyk(NotificationListener())
        notificationListener.notificationDao = notificationDao
        notificationListener.rawAppEventDao = rawAppEventDao
        every { notificationListener.applicationContext } returns mockContext
        // We no longer need to mock getPackageName separately if we use a spy on the real context
    }

    private fun createMockSbn(
        packageName: String,
        key: String,
        postTime: Long,
        title: String?,
        text: String?,
        category: String?
    ): StatusBarNotification {
        val builder = AndroidNotification.Builder(mockContext, "default_channel")
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)

        if (category != null) {
            builder.setCategory(category)
        }

        val realNotification = builder.build()

        // We still mock StatusBarNotification because it's hard to construct.
        val sbn: StatusBarNotification = mockk(relaxed = true)
        every { sbn.packageName } returns packageName
        every { sbn.key } returns key
        every { sbn.postTime } returns postTime
        every { sbn.notification } returns realNotification
        return sbn
    }

    @Test
    fun `onNotificationPosted saves record and logs event`() = runTest {
        val sbn = createMockSbn(
            "com.test.app",
            "test_key_1",
            1672531200000L,
            "Test Title",
            "Test Text",
            "test_category"
        )

        notificationListener.onNotificationPosted(sbn)

        val notificationRecordSlot = slot<NotificationRecord>()
        coVerify { notificationDao.insert(capture(notificationRecordSlot)) }
        assertThat(notificationRecordSlot.captured.packageName).isEqualTo("com.test.app")
        assertThat(notificationRecordSlot.captured.title).isEqualTo("Test Title")

        val rawEventSlot = slot<RawAppEvent>()
        coVerify { rawAppEventDao.insertEvent(capture(rawEventSlot)) }
        assertThat(rawEventSlot.captured.eventType).isEqualTo(RawAppEvent.EVENT_TYPE_NOTIFICATION_POSTED)
    }

    @Test
    fun `onNotificationRemoved updates record and logs event`() = runTest {
        val sbn = createMockSbn(
            "com.test.app",
            "test_key_2",
            1672531200000L,
            null, null, null
        )

        notificationListener.onNotificationRemoved(sbn, null, 1)

        coVerify { notificationDao.updateRemovalReason("com.test.app", 1672531200000L, 1) }

        val rawEventSlot = slot<RawAppEvent>()
        coVerify { rawAppEventDao.insertEvent(capture(rawEventSlot)) }
        assertThat(rawEventSlot.captured.eventType).isEqualTo(RawAppEvent.EVENT_TYPE_NOTIFICATION_REMOVED)
        assertThat(rawEventSlot.captured.value).isEqualTo(1L)
    }

    @Test
    fun `onNotificationPosted ignores own app notifications`() = runTest {
        val sbn = createMockSbn(
            mockContext.packageName, // Use the real package name from the context
            "test_key_3",
            1672531200000L,
            "Internal Title",
            "Internal Text",
            null
        )

        notificationListener.onNotificationPosted(sbn)

        coVerify(exactly = 0) { notificationDao.insert(any()) }
        coVerify(exactly = 0) { rawAppEventDao.insertEvent(any()) }
    }
} 