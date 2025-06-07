package com.example.scrolltrack.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

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
        val date = utcIsoDateFormat.parse(dateString) ?: throw java.text.ParseException("Invalid date format", 0)
        return date.time
    }

    /**
     * Gets the UTC timestamp for the end of the day (23:59:59.999) for a given date string.
     * The date string is interpreted as being in UTC.
     */
    fun getEndOfDayUtcMillis(dateString: String): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.time = utcIsoDateFormat.parse(dateString) ?: throw java.text.ParseException("Invalid date format", 0)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
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
