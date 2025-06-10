package com.example.scrolltrack.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.TimeZone

class DateUtilTest {

    @Test
    fun `formatUtcTimestampToLocalDateString - returns correct date string for midday timestamp`() {
        // ARRANGE
        // Let's define a specific UTC timestamp: January 15, 2024 10:00:00 AM UTC
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val timestamp = formatter.parse("2024-01-15 10:00:00")!!.time

        // ACT
        val result = DateUtil.formatUtcTimestampToLocalDateString(timestamp)

        // ASSERT
        // Regardless of the local timezone of the machine running the test, the date derived
        // from this UTC timestamp should correspond to "2024-01-15".
        assertThat(result).isEqualTo("2024-01-15")
    }

    @Test
    fun `formatUtcTimestampToLocalDateString - returns correct date string for timestamp just after midnight`() {
        // ARRANGE
        // Timestamp for January 16, 2024 00:00:01 AM UTC
        val timestamp = 1705363201000L

        // ACT
        val result = DateUtil.formatUtcTimestampToLocalDateString(timestamp)

        // ASSERT
        assertThat(result).isEqualTo("2024-01-16")
    }

    @Test
    fun `formatUtcTimestampToLocalDateString - returns correct date string for timestamp just before midnight`() {
        // ARRANGE
        // Timestamp for January 15, 2024 23:59:59 PM UTC
        val timestamp = 1705363199000L

        // ACT
        val result = DateUtil.formatUtcTimestampToLocalDateString(timestamp)

        // ASSERT
        assertThat(result).isEqualTo("2024-01-15")
    }

    @Test
    fun `getStartOfDayUtcMillis - returns correct UTC millisecond for standard date string`() {
        // ARRANGE
        val dateString = "2024-07-20"
        // The expected timestamp for 2024-07-20T00:00:00.000Z
        val expectedTimestamp = 1721433600000L

        // ACT
        val result = DateUtil.getStartOfDayUtcMillis(dateString)

        // ASSERT
        assertThat(result).isEqualTo(expectedTimestamp)
    }

    @Test
    fun `getStartOfDayUtcMillis - handles leap year date string correctly`() {
        // ARRANGE
        val dateString = "2024-02-29"
        // The expected timestamp for 2024-02-29T00:00:00.000Z
        val expectedTimestamp = 1709164800000L

        // ACT
        val result = DateUtil.getStartOfDayUtcMillis(dateString)

        // ASSERT
        assertThat(result).isEqualTo(expectedTimestamp)
    }

    @Test(expected = java.text.ParseException::class)
    fun `getStartOfDayUtcMillis - throws exception for invalid date string format`() {
        // ARRANGE
        val invalidDateString = "2024/01/15"

        // ACT
        // This call should throw a ParseException, which is caught by the @Test annotation.
        DateUtil.getStartOfDayUtcMillis(invalidDateString)
    }
} 