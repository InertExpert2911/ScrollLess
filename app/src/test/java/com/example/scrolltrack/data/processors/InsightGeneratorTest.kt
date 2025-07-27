package com.example.scrolltrack.data.processors

import com.example.scrolltrack.db.RawAppEvent
import com.example.scrolltrack.db.UnlockSessionRecord
import com.example.scrolltrack.util.DateUtil
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.TimeUnit

class InsightGeneratorTest {

    private val generator = InsightGenerator()

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
    fun `invoke - night owl - correctly identifies last app used after midnight`() {
        val date = "2024-01-20"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val nightOwlTime = startOfDay + TimeUnit.HOURS.toMillis(2)
        val appA = "com.night.app"

        val events = listOf(
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, nightOwlTime)
        )

        val insights = generator(date, emptyList(), events, emptySet())

        val nightOwlInsight = insights.find { it.insightKey == "night_owl_last_app" }
        assertThat(nightOwlInsight).isNotNull()
        assertThat(nightOwlInsight?.stringValue).isEqualTo(appA)
        assertThat(nightOwlInsight?.longValue).isEqualTo(nightOwlTime)
    }


    @Test
    fun `invoke - first app used - finds first app after first unlock`() {
        val date = "2024-01-20"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val firstUnlockTime = startOfDay + TimeUnit.HOURS.toMillis(7)
        val firstAppTime = firstUnlockTime + 1000L
        val appA = "com.morning.app"

        val events = listOf(
            createRawEvent("app", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + 1000),
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, firstAppTime)
        )
        val unlockSessions = listOf(
            UnlockSessionRecord(id=1, unlockTimestamp = firstUnlockTime, dateString = date, unlockEventType = "TEST")
        )

        val insights = generator(date, unlockSessions, events, emptySet())

        val firstAppInsight = insights.find { it.insightKey == "first_app_used" }
        assertThat(firstAppInsight).isNotNull()
        assertThat(firstAppInsight?.stringValue).isEqualTo(appA)
        assertThat(firstAppInsight?.longValue).isEqualTo(firstAppTime)
    }

    @Test
    fun `invoke - busiest hour - calculates correctly`() {
        val date = "2024-01-20"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val unlockSessions = listOf(
            UnlockSessionRecord(unlockTimestamp = startOfDay + 20 * 3600 * 1000, dateString = date, unlockEventType = "TEST"),
            UnlockSessionRecord(unlockTimestamp = startOfDay + 20 * 3600 * 1000 + 1, dateString = date, unlockEventType = "TEST"),
            UnlockSessionRecord(unlockTimestamp = startOfDay + 10 * 3600 * 1000, dateString = date, unlockEventType = "TEST")
        )

        val insights = generator(date, unlockSessions, emptyList(), emptySet())

        val busiestHourInsight = insights.find { it.insightKey == "busiest_unlock_hour" }
        assertThat(busiestHourInsight).isNotNull()
        assertThat(busiestHourInsight?.longValue).isEqualTo(20)
    }

    @Test
    fun `invoke - top compulsive app - calculates correctly`() {
        val date = "2024-01-20"
        val unlockSessions = listOf(
            UnlockSessionRecord(dateString = date, unlockTimestamp = 1, isCompulsive = true, firstAppPackageName = "com.twitter.android", lockTimestamp = 2, durationMillis = 1, unlockEventType = "TEST"),
            UnlockSessionRecord(dateString = date, unlockTimestamp = 3, isCompulsive = true, firstAppPackageName = "com.twitter.android", lockTimestamp = 4, durationMillis = 1, unlockEventType = "TEST"),
            UnlockSessionRecord(dateString = date, unlockTimestamp = 5, isCompulsive = true, firstAppPackageName = "com.instagram.android", lockTimestamp = 6, durationMillis = 1, unlockEventType = "TEST")
        )

        val insights = generator(date, unlockSessions, emptyList(), emptySet())

        val topCompulsiveAppInsight = insights.find { it.insightKey == "top_compulsive_app" }
        assertThat(topCompulsiveAppInsight).isNotNull()
        assertThat(topCompulsiveAppInsight?.stringValue).isEqualTo("com.twitter.android")
        assertThat(topCompulsiveAppInsight?.longValue).isEqualTo(2)
    }

    @Test
    fun `invoke - various insights - calculates correctly`() {
        val date = "2024-01-20"
        val startOfDay = DateUtil.getStartOfDayUtcMillis(date)
        val unlockSessions = listOf(
            UnlockSessionRecord(unlockTimestamp = startOfDay + 1000, dateString = date, unlockEventType = "TEST", triggeringNotificationPackageName = "com.app.notify"),
            UnlockSessionRecord(unlockTimestamp = startOfDay + 80000000, dateString = date, unlockEventType = "TEST")
        )
        val events = listOf(
            createRawEvent("com.app.first", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + 2000),
            createRawEvent("com.app.night", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + TimeUnit.HOURS.toMillis(3)),
            createRawEvent("com.app.last", RawAppEvent.EVENT_TYPE_ACTIVITY_RESUMED, startOfDay + TimeUnit.HOURS.toMillis(22))
        )

        val insights = generator(date, unlockSessions, events, emptySet())

        assertThat(insights.find { it.insightKey == "first_unlock_time" }?.longValue).isEqualTo(startOfDay + 1000)
        assertThat(insights.find { it.insightKey == "last_unlock_time" }?.longValue).isEqualTo(startOfDay + 80000000)
        assertThat(insights.find { it.insightKey == "first_app_used" }?.stringValue).isEqualTo("com.app.first")
        assertThat(insights.find { it.insightKey == "last_app_used" }?.stringValue).isEqualTo("com.app.last")
        assertThat(insights.find { it.insightKey == "top_notification_unlock_app" }?.stringValue).isEqualTo("com.app.notify")
        assertThat(insights.find { it.insightKey == "night_owl_last_app" }?.stringValue).isEqualTo("com.app.night")
    }

    @Test
    fun `invoke - no data scenarios - does not crash`() {
        val insights = generator("2024-01-20", emptyList(), emptyList(), emptySet())
        assertThat(insights).isEmpty()
    }
}
