package com.example.scrolltrack.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

object DateUtil {

    private val utcIsoDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
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
     * The date string is interpreted as being in UTC.
     */
    fun getStartOfDayUtcMillis(dateString: String): Long {
        val date = parseLocalDateString(dateString) ?: return 0L
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.time = date
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /**
     * Gets the UTC timestamp for the end of the day (23:59:59.999) for a given date string.
     * The date string is interpreted as being in UTC.
     */
    fun getEndOfDayUtcMillis(dateString: String): Long {
        return getStartOfDayUtcMillis(dateString) + TimeUnit.DAYS.toMillis(1) - 1
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
        if (millis < TimeUnit.MINUTES.toMillis(1)) return "< 1m"

        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1)

        return when {
            hours > 0 -> String.format(Locale.getDefault(), "%dh %02dm", hours, minutes)
            minutes > 0 -> String.format(Locale.getDefault(), "%dm", minutes)
            else -> "< 1m"
        }
    }
}
