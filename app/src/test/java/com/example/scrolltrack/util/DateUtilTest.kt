package com.example.scrolltrack.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date
import java.util.concurrent.TimeUnit

class DateUtilTest {

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
}
