package com.example.scrolltrack.data.processors

import com.example.scrolltrack.db.RawAppEvent
import com.example.scrolltrack.util.AppConstants
import com.example.scrolltrack.util.DateUtil
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScrollSessionCalculatorTest {

    private val calculator = ScrollSessionCalculator()

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

    @Test
    fun `invoke - mixed events for one app - prioritizes MEASURED`() {
        val appA = "com.app.a"
        val events = listOf(
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1000, scrollDeltaY = 100),
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_SCROLL_INFERRED, 2000, scrollDeltaY = 500)
        )

        val sessions = calculator(events, emptySet())

        assertThat(sessions).hasSize(1)
        assertThat(sessions.first().packageName).isEqualTo(appA)
        assertThat(sessions.first().dataType).isEqualTo("MEASURED")
        assertThat(sessions.first().scrollAmountY).isEqualTo(100)
    }

    @Test
    fun `invoke - with inferred scroll - creates INFERRED session`() {
        val appA = "com.app.a"
        val events = listOf(
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_SCROLL_INFERRED, 1000, scrollDeltaY = 100)
        )

        val sessions = calculator(events, emptySet())

        assertThat(sessions).hasSize(1)
        assertThat(sessions.first().packageName).isEqualTo(appA)
        assertThat(sessions.first().dataType).isEqualTo("INFERRED")
    }

    @Test
    fun `invoke - mixed events for multiple apps - handles each app independently`() {
        val appA = "com.app.a"
        val appB = "com.app.b"
        val events = listOf(
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1000, scrollDeltaY = 100),
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_SCROLL_INFERRED, 2000, scrollDeltaY = 500),
            createRawEvent(appB, RawAppEvent.EVENT_TYPE_SCROLL_INFERRED, 3000, scrollDeltaY = 200)
        )

        val sessions = calculator(events, emptySet())

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

    @Test
    fun `invoke - measured scroll - correctly calculates and merges session`() {
        val appA = "com.app.a"
        val events = listOf(
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1000, scrollDeltaX = 20, scrollDeltaY = 100),
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1500, scrollDeltaX = 30, scrollDeltaY = 150)
        )

        val sessions = calculator(events, emptySet())

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
    fun `invoke - inferred scroll - correctly calculates and merges session`() {
        val appB = "com.app.b"
        val events = listOf(
            createRawEvent(appB, RawAppEvent.EVENT_TYPE_SCROLL_INFERRED, 1000, scrollDeltaY = 50),
            createRawEvent(appB, RawAppEvent.EVENT_TYPE_SCROLL_INFERRED, 1500, scrollDeltaY = 60),
            createRawEvent(appB, RawAppEvent.EVENT_TYPE_SCROLL_INFERRED, 2000, scrollDeltaY = 70)
        )

        val sessions = calculator(events, emptySet())

        assertThat(sessions).hasSize(1)
        val session = sessions.first()
        assertThat(session.packageName).isEqualTo(appB)
        assertThat(session.scrollAmountX).isEqualTo(0)
        assertThat(session.scrollAmountY).isEqualTo(180)
        assertThat(session.scrollAmount).isEqualTo(180)
    }

    @Test
    fun `invoke - session breaks due to time gap`() {
        val appA = "com.app.a"
        val events = listOf(
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1000, scrollDeltaY = 100),
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1000 + AppConstants.SESSION_MERGE_GAP_MS + 1, scrollDeltaY = 100)
        )

        val sessions = calculator(events, emptySet())

        assertThat(sessions).hasSize(2)
    }

    @Test
    fun `invoke - session breaks due to different app`() {
        val appA = "com.app.a"
        val appB = "com.app.b"
        val events = listOf(
            createRawEvent(appA, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1000, scrollDeltaY = 100),
            createRawEvent(appB, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1500, scrollDeltaY = 100)
        )

        val sessions = calculator(events, emptySet())

        assertThat(sessions).hasSize(2)
    }
    @Test
    fun `invoke - with legacy inferred event - uses value field`() {
        val event = RawAppEvent(
            packageName = "com.legacy.app",
            eventType = RawAppEvent.EVENT_TYPE_SCROLL_INFERRED,
            eventTimestamp = 1000,
            value = 250L, // Legacy field
            scrollDeltaY = null, // New field is null
            eventDateString = "2024-01-01",
            source = "TEST",
            className = "TestClass",
            scrollDeltaX = null
        )
        val sessions = calculator(listOf(event), emptySet())
        assertThat(sessions).hasSize(1)
        assertThat(sessions.first().scrollAmountY).isEqualTo(250)
    }

    @Test
    fun `invoke - with filtered app - ignores events from filtered app`() {
        val hiddenApp = "com.hidden.app"
        val visibleApp = "com.visible.app"
        val events = listOf(
            createRawEvent(hiddenApp, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1000, scrollDeltaY = 100),
            createRawEvent(visibleApp, RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 2000, scrollDeltaY = 200)
        )
        val filterSet = setOf(hiddenApp)

        val sessions = calculator(events, filterSet)

        assertThat(sessions).hasSize(1)
        assertThat(sessions.first().packageName).isEqualTo(visibleApp)
    }

    @Test
    fun `invoke - with zero delta event - ignores event`() {
        val events = listOf(
            createRawEvent("com.app.a", RawAppEvent.EVENT_TYPE_SCROLL_MEASURED, 1000, scrollDeltaX = 0, scrollDeltaY = 0)
        )
        val sessions = calculator(events, emptySet())
        assertThat(sessions).isEmpty()
    }
}