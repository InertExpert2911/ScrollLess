package com.example.scrolltrack.util // Updated package name

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
        return dateFormat.format(Date())
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
    fun formatDate(millis: Long): String {
        return dateFormat.format(Date(millis))
    }

    /**
     * Parses a date string (YYYY-MM-DD) into a Date object.
     * Returns null if parsing fails.
     */
    fun parseDateString(dateString: String): Date? {
        return try {
            dateFormat.parse(dateString)
        } catch (e: Exception) {
            null // Handle parsing exception
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
    fun formatDuration(millis: Long): String {
        if (millis < 0) return "N/A" // Invalid duration
        if (millis < TimeUnit.MINUTES.toMillis(1)) return "< 1m" // Less than a minute

        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1)
        // val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1) // Optional: include seconds

        return when {
            hours > 0 -> String.format(Locale.getDefault(), "%dh %02dm", hours, minutes)
            // If you want to show seconds when hours is 0:
            // minutes > 0 -> String.format(Locale.getDefault(), "%02dm %02ds", minutes, seconds)
            minutes > 0 -> String.format(Locale.getDefault(), "%dm", minutes)
            // If you want to show seconds when minutes is also 0:
            // else -> String.format(Locale.getDefault(), "%ds", seconds)
            else -> "< 1m" // Fallback for very short durations already handled, but good to have else
        }
    }
}
