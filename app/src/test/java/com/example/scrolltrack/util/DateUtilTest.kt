package com.example.scrolltrack.util

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class DateUtilTest {

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @Test
    fun getPastDateString_returnsCorrectlyFormattedString() {
        // We can't control the exact time, so we test the format and length
        val dateString = DateUtil.getPastDateString(1) // Yesterday
        assertThat(dateString).matches("^\\d{4}-\\d{2}-\\d{2}$")
    }

    @Test
    fun getYesterdayDateString_returnsCorrectDate() {
        val yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1)
        val expected = DateUtil.formatDateToYyyyMmDdString(yesterday)
        assertThat(DateUtil.getYesterdayDateString()).isEqualTo(expected)
    }

    @Test
    fun formatDuration_lessThanOneMinute_returnsLessThanOneMinuteString() {
        val millis = TimeUnit.SECONDS.toMillis(30)
        assertThat(DateUtil.formatDuration(millis)).isEqualTo("< 1m")
    }

    @Test
    fun formatDuration_onlyMinutes_returnsCorrectMinutesString() {
        val millis = TimeUnit.MINUTES.toMillis(45)
        assertThat(DateUtil.formatDuration(millis)).isEqualTo("45m")
    }

    @Test
    fun formatDuration_hoursAndMinutes_returnsCorrectHoursAndMinutesString() {
        val millis = TimeUnit.HOURS.toMillis(2) + TimeUnit.MINUTES.toMillis(30)
        assertThat(DateUtil.formatDuration(millis)).isEqualTo("2h 30m")
    }

    @Test
    fun `formatDuration with zero millis returns less than 1m`() {
        assertThat(DateUtil.formatDuration(0)).isEqualTo("0m")
    }

    @Test
    fun `formatUtcTimestampToLocalDateString works correctly`() {
        // A known timestamp for Jan 1, 2023
        val timestamp = 1672531200000L
        assertThat(DateUtil.formatUtcTimestampToLocalDateString(timestamp)).isEqualTo("2023-01-01")
    }

    @Test
    fun `parseLocalDateString and back gives same timestamp`() {
        val dateString = "2023-05-20"
        val parsedDate = DateUtil.parseLocalDate(dateString)
        assertThat(parsedDate).isNotNull()
        val formattedBack = DateUtil.formatDateToYyyyMmDdString(parsedDate!!)
        assertThat(formattedBack).isEqualTo(dateString)
    }

    @Test
    fun `formatDurationWithSeconds only seconds`() {
        val millis = 45000L
        assertThat(DateUtil.formatDurationWithSeconds(millis)).isEqualTo("45s")
    }

    @Test
    fun `formatDurationWithSeconds minutes and seconds`() {
        val millis = 95000L
        assertThat(DateUtil.formatDurationWithSeconds(millis)).isEqualTo("1m 35s")
    }

    @Test
    fun `formatDurationWithSeconds hours and minutes`() {
        val millis = 3690000L
        assertThat(DateUtil.formatDurationWithSeconds(millis)).isEqualTo("1h 1m")
    }

    @Test
    fun `getStartOfWeek and getEndOfWeek`() {
        val date = LocalDate.of(2024, 7, 24) // A Wednesday
        val startOfWeek = date.with(java.time.DayOfWeek.MONDAY)
        val endOfWeek = date.with(java.time.DayOfWeek.SUNDAY)

        assertThat(DateUtil.getStartOfWeek(date)).isEqualTo(startOfWeek)
        assertThat(DateUtil.getEndOfWeek(date)).isEqualTo(endOfWeek)
    }

    @Test
    fun `getStartOfMonth and getEndOfMonth`() {
        val date = LocalDate.of(2024, 7, 24)
        val startOfMonth = date.withDayOfMonth(1)
        val endOfMonth = date.withDayOfMonth(date.lengthOfMonth())

        assertThat(DateUtil.getStartOfMonth(date)).isEqualTo(startOfMonth)
        assertThat(DateUtil.getEndOfMonth(date)).isEqualTo(endOfMonth)
    }

    @Test
    fun `getEndOfMonth for leap year`() {
        val date = LocalDate.of(2024, 2, 15)
        val endOfMonth = date.withDayOfMonth(29)
        assertThat(DateUtil.getEndOfMonth(date)).isEqualTo(endOfMonth)
    }

    @Test
    fun `getWeekOfYear works correctly`() {
        val firstWeekDate = LocalDate.of(2023, 1, 2)
        val lastWeekDate = LocalDate.of(2023, 12, 31)
        assertThat(DateUtil.getWeekOfYear(firstWeekDate)).isEqualTo(1)
        assertThat(DateUtil.getWeekOfYear(lastWeekDate)).isEqualTo(52)
    }

    @Test
    fun `getFormattedDate returns correct format`() {
        val date = LocalDate.of(2024, 7, 24)
        val expected = "Wednesday, July 24, 2024"
        assertThat(DateUtil.getFormattedDate(date)).isEqualTo(expected)
    }
    @Test
    fun `formatUtcTimestampToTimeString returns correct format`() {
        val timestamp = 1672581600000L // 2023-01-01T14:00:00Z
        val expected = "2:00 PM" // in UTC
        val formattedTime = DateUtil.formatUtcTimestampToTimeString(timestamp)
        assertThat(formattedTime.uppercase(Locale.getDefault())).isEqualTo(expected)
    }
}
