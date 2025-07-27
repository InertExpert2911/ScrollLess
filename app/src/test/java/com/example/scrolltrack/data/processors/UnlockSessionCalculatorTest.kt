package com.example.scrolltrack.data.processors

import com.example.scrolltrack.db.RawAppEvent
import com.example.scrolltrack.util.AppConstants
import com.example.scrolltrack.util.DateUtil
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UnlockSessionCalculatorTest {

    private val calculator = UnlockSessionCalculator()

    private fun createRawEvent(
        pkg: String,
        type: Int,
        timestamp: Long,
        className: String? = "TestClass"
    ): RawAppEvent {
        return RawAppEvent(
            packageName = pkg,
            className = className,
            eventType = type,
            eventTimestamp = timestamp,
            eventDateString = DateUtil.formatUtcTimestampToLocalDateString(timestamp),
            source = "TEST"
        )
    }

    @Test
    fun `invoke - with glance session - correctly identifies glance`() {
        val date = "2024-01-20"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val unlockTime = startOfDay + 1000L
        val lockTime = unlockTime + AppConstants.MINIMUM_GLANCE_DURATION_MS - 1

        val events = listOf(
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, unlockTime),
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE, lockTime)
        )

        val sessions = calculator(events, emptyList(), emptySet(), setOf(RawAppEvent.EVENT_TYPE_USER_UNLOCKED), setOf(RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE))

        assertThat(sessions).hasSize(1)
        assertThat(sessions.first().sessionType).isEqualTo("Glance")
    }
    @Test
    fun `invoke - intentional session - correctly identifies intentional`() {
        val date = "2024-01-20"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val unlockTime = startOfDay + 1000L
        val lockTime = unlockTime + AppConstants.MINIMUM_GLANCE_DURATION_MS + 1

        val events = listOf(
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, unlockTime),
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE, lockTime)
        )

        val sessions = calculator(events, emptyList(), emptySet(), setOf(RawAppEvent.EVENT_TYPE_USER_UNLOCKED), setOf(RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE))

        assertThat(sessions).hasSize(1)
        assertThat(sessions.first().sessionType).isEqualTo("Intentional")
    }

    @Test
    fun `invoke - compulsive check - correctly identifies compulsive`() {
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

        val sessions = calculator(events, emptyList(), emptySet(), setOf(RawAppEvent.EVENT_TYPE_USER_UNLOCKED), setOf(RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE))

        assertThat(sessions).hasSize(1)
        assertThat(sessions.first().isCompulsive).isTrue()
    }

    @Test
    fun `invoke - notification driven - correctly identifies trigger`() {
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
            com.example.scrolltrack.db.NotificationRecord(notificationKey = "key1", packageName = appA, postTimeUTC = notificationTime, dateString = date, title = "title", text = "text", category = "cat")
        )

        val sessions = calculator(events, notifications, emptySet(), setOf(RawAppEvent.EVENT_TYPE_USER_UNLOCKED), setOf(RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE))

        assertThat(sessions).hasSize(1)
        assertThat(sessions.first().triggeringNotificationPackageName).isEqualTo(appA)
    }

    @Test
    fun `invoke - glance vs intentional - flags correctly`() {
        val date = "2024-01-20"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)

        val glanceUnlockTime = startOfDay + 1000L
        val glanceLockTime = glanceUnlockTime + AppConstants.MINIMUM_GLANCE_DURATION_MS - 1
        val glanceEvents = listOf(
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, glanceUnlockTime),
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE, glanceLockTime)
        )
        val glanceSessions = calculator(glanceEvents, emptyList(), emptySet(), setOf(RawAppEvent.EVENT_TYPE_USER_UNLOCKED), setOf(RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE))
        assertThat(glanceSessions).hasSize(1)
        assertThat(glanceSessions.first().sessionType).isEqualTo("Glance")

        val intentionalUnlockTime = startOfDay + 10000L
        val intentionalLockTime = intentionalUnlockTime + AppConstants.MINIMUM_GLANCE_DURATION_MS
        val intentionalEvents = listOf(
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, intentionalUnlockTime),
            createRawEvent("android", RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE, intentionalLockTime)
        )
        val intentionalSessions = calculator(intentionalEvents, emptyList(), emptySet(), setOf(RawAppEvent.EVENT_TYPE_USER_UNLOCKED), setOf(RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE))
        assertThat(intentionalSessions).hasSize(1)
        assertThat(intentionalSessions.first().sessionType).isEqualTo("Intentional")
    }

    @Test
    fun `invoke - non-compulsive short session`() {
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

        val sessions = calculator(events, emptyList(), emptySet(), setOf(RawAppEvent.EVENT_TYPE_USER_UNLOCKED), setOf(RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE))

        assertThat(sessions).hasSize(1)
        assertThat(sessions.first().isCompulsive).isFalse()
    }
    @Test
    fun `invoke - with ghost session - closes previous session and starts new one`() {
        val events = listOf(
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, 1000),
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, 2000) // Ghost unlock
        )
        val sessions = calculator(events, emptyList(), emptySet(), setOf(RawAppEvent.EVENT_TYPE_USER_UNLOCKED), setOf(RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE))

        assertThat(sessions).hasSize(2)
        assertThat(sessions[0].sessionEndReason).isEqualTo("GHOST")
        assertThat(sessions[0].lockTimestamp).isEqualTo(2000)
        assertThat(sessions[1].lockTimestamp).isNull() // The new session is open
    }

    @Test
    fun `invoke - session open at end of period - adds open session`() {
        val events = listOf(
            createRawEvent("android", RawAppEvent.EVENT_TYPE_USER_UNLOCKED, 1000)
        )
        val sessions = calculator(events, emptyList(), emptySet(), setOf(RawAppEvent.EVENT_TYPE_USER_UNLOCKED), setOf(RawAppEvent.EVENT_TYPE_SCREEN_NON_INTERACTIVE))

        assertThat(sessions).hasSize(1)
        assertThat(sessions.first().lockTimestamp).isNull()
    }
}
