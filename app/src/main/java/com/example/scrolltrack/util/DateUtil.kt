package com.example.scrolltrack.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object DateUtil {
    // Standard date format for storing and querying dates as strings
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * Gets the current date as a formatted string (YYYY-MM-DD).
     */
    fun getCurrentDateString(): String {
        val calendar = Calendar.getInstance()
        return dateFormat.format(calendar.time)
    }

    /**
     * Formats a given Date object into a string (YYYY-MM-DD).
     */
    fun formatDate(date: Date): String {
        return dateFormat.format(date)
    }

    /**
     * Formats a given timestamp (milliseconds since epoch) into a date string (YYYY-MM-DD).
     */
    fun formatDate(dateMillis: Long): String {
        return dateFormat.format(Date(dateMillis))
    }

    /**
     * Parses a date string (YYYY-MM-DD) into a Date object.
     * Returns null if parsing fails.
     */
    fun parseDateString(dateString: String?): Date? {
        if (dateString == null) return null
        return try {
            dateFormat.parse(dateString)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Gets the timestamp for the start of the day (midnight) for a given date string.
     * @param dateString Date in "YYYY-MM-DD" format.
     * @return Milliseconds since epoch for the start of that day.
     */
    fun getStartOfDayMillis(dateString: String): Long {
        val date = parseDateString(dateString) ?: Date() // Default to current date if parsing fails
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /**
     * Gets the timestamp for the end of the day (23:59:59.999) for a given date string.
     * @param dateString Date in "YYYY-MM-DD" format.
     * @return Milliseconds since epoch for the end of that day.
     */
    fun getEndOfDayMillis(dateString: String): Long {
        val date = parseDateString(dateString) ?: Date() // Default to current date if parsing fails
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }

    /**
     * Formats a duration in milliseconds into a human-readable string (e.g., "2h 30m", "45m", "< 1m").
     * @param millis The duration in milliseconds.
     * @return Formatted string representation of the duration.
     */
    fun formatDuration(millis: Long, withSeconds: Boolean = false): String {
        if (millis < 0) return "0m"
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60

        return when {
            hours > 0 -> if (withSeconds) String.format("%dh %dm %ds", hours, minutes, seconds) else String.format("%dh %dm", hours, minutes)
            minutes > 0 -> if (withSeconds) String.format("%dm %ds", minutes, seconds) else String.format("%dm", minutes)
            withSeconds -> String.format("%ds", seconds)
            else -> "0m" // if not withSeconds and less than a minute, show 0m
        }
    }

    fun getDaysAgo(days: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -days)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(calendar.time)
    }

    fun formatDateForDisplay(date: Date): String {
        val sdf = SimpleDateFormat("EEE, MMM d", Locale.getDefault()) // Example: "Sun, May 25"
        return sdf.format(date)
    }
}
