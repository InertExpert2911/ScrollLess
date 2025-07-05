package com.example.scrolltrack.util

import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit


object DateUtil {

    // Using a single, well-defined date formatter for consistency.
    private val utcIsoDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
        // Setting isLenient to false is a good practice to prevent parsing of invalid dates.
        isLenient = false
    }

    /**
     * Gets the current UTC timestamp in milliseconds.
     */
    fun getUtcTimestamp(): Long {
        return System.currentTimeMillis()
    }

    /**
     * Gets the current date as a formatted string (yyyy-MM-dd) in UTC.
     */
    fun getCurrentLocalDateString(): String {
        return utcIsoDateFormat.format(Date(getUtcTimestamp()))
    }

    /**
     * Formats a given UTC timestamp into a date string (yyyy-MM-dd) in UTC.
     */
    fun formatUtcTimestampToLocalDateString(utcTimestampMillis: Long): String {
        return utcIsoDateFormat.format(Date(utcTimestampMillis))
    }

    /**
     * Parses a date string (yyyy-MM-dd) as UTC and returns a Date object.
     * Returns null if parsing fails.
     */
    fun parseLocalDateString(dateString: String): Date? {
        return try {
            utcIsoDateFormat.parse(dateString)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Formats a given Date object into a UTC date string (yyyy-MM-dd).
     */
    fun formatDateToYyyyMmDdString(date: Date): String {
        return utcIsoDateFormat.format(date)
    }

    /**
     * Gets the UTC timestamp for the start of the day (00:00:00.000) for a given date string.
     */
    fun getStartOfDayUtcMillis(dateString: String): Long {
        return parseLocalDateString(dateString)?.time ?: 0L
    }

    /**
     * Gets the UTC timestamp for the end of the day (23:59:59.999) for a given date string.
     */
    fun getEndOfDayUtcMillis(dateString: String): Long {
        val startMillis = getStartOfDayUtcMillis(dateString)
        return startMillis + TimeUnit.DAYS.toMillis(1) - 1
    }

    fun getStartOfWeek(date: LocalDate): LocalDate {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    }

    fun getEndOfWeek(date: LocalDate): LocalDate {
        return date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
    }

    fun getStartOfMonth(date: LocalDate): LocalDate {
        return date.withDayOfMonth(1)
    }

    fun getEndOfMonth(date: LocalDate): LocalDate {
        return date.with(TemporalAdjusters.lastDayOfMonth())
    }

    /**
     * Formats a duration in milliseconds into a human-readable string (e.g., "2h 30m", "45m", "< 1m").
     */
    fun formatDuration(millis: Long): String {
        if (millis < 0) return "N/A"

        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis)

        if (totalMinutes < 1) return "< 1m"

        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        return when {
            hours > 0 -> String.format("%dh %02dm", hours, minutes)
            else -> "${totalMinutes}m"
        }
    }

    fun getWeekOfYear(date: Date): Int {
        val calendar = Calendar.getInstance()
        calendar.time = date
        return calendar.get(Calendar.WEEK_OF_YEAR)
    }

    fun formatUtcTimestampToTimeString(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault() // Display in user's local time
        return sdf.format(Date(timestamp))
    }

    fun getYesterdayDateString(): String {
        // Using java.time for a clearer and more robust implementation.
        val yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1)
        return utcIsoDateFormat.format(Date.from(yesterday.atStartOfDay().toInstant(ZoneOffset.UTC)))
    }

    fun getMillisUntilNextMidnight(): Long {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(ZoneId.systemDefault())
        return nextMidnight.toInstant().toEpochMilli() - now.toInstant().toEpochMilli()
    }

    fun getPastDateString(daysAgo: Int): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.add(Calendar.DATE, -daysAgo)
        return utcIsoDateFormat.format(calendar.time)
    }
}
