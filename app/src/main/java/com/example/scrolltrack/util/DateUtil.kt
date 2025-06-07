package com.example.scrolltrack.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
// No need to import java.util.TimeZone explicitly unless using TimeZone.getTimeZone("UTC") for Calendar instances

object DateUtil {
    // SimpleDateFormat initialized with Locale.getDefault() will use the system's default TimeZone
    // when formatting Date objects or parsing date strings without explicit timezone information.
    private val localIsoDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * Gets the current UTC timestamp in milliseconds.
     */
    fun getUtcTimestamp(): Long {
        return System.currentTimeMillis()
    }

    /**
     * Gets the current local date as a formatted string (yyyy-MM-dd),
     * based on the device's current default timezone.
     */
    fun getCurrentLocalDateString(): String {
        // Date() constructor with no args uses current time, which is effectively UTC millis.
        // localIsoDateFormat then formats it using the default TimeZone.
        return localIsoDateFormat.format(Date(getUtcTimestamp()))
    }

    /**
     * Formats a given UTC timestamp (milliseconds since epoch) into a local date string (yyyy-MM-dd),
     * based on the device's current default timezone.
     */
    fun formatUtcTimestampToLocalDateString(utcTimestampMillis: Long): String {
        return localIsoDateFormat.format(Date(utcTimestampMillis))
    }

    /**
     * Parses a local date string (yyyy-MM-dd) into a Date object.
     * The resulting Date object represents milliseconds from epoch (UTC).
     * Its interpretation as a "local date" depends on how it's further processed or formatted.
     * Returns null if parsing fails.
     */
    fun parseLocalDateString(localDateString: String): Date? {
        return try {
            localIsoDateFormat.parse(localDateString)
        } catch (e: Exception) {
            null // Handle parsing exception
        }
    }

    /**
     * Formats a given Date object into a local date string (yyyy-MM-dd),
     * based on the device's current default timezone.
     * @param date The Date object to format.
     * @return Formatted date string "yyyy-MM-dd".
     */
    fun formatDateToYyyyMmDdString(date: Date): String {
        return localIsoDateFormat.format(date)
    }

    /**
     * Gets the UTC timestamp for the start of the day (00:00:00.000) for a given local date string,
     * interpreted in the device's current default timezone.
     * @param localDateString Date in "yyyy-MM-dd" format.
     * @return Milliseconds since epoch (UTC) for the start of that local day.
     */
    fun getStartOfDayUtcMillis(localDateString: String): Long {
        val date = parseLocalDateString(localDateString) ?: Date(getUtcTimestamp()) // Default to current time if parsing fails, effectively current day.
        val calendar = Calendar.getInstance() // getInstance() uses the default timezone and locale.
        calendar.time = date
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /**
     * Gets the UTC timestamp for the end of the day (23:59:59.999) for a given local date string,
     * interpreted in the device's current default timezone.
     * @param localDateString Date in "yyyy-MM-dd" format.
     * @return Milliseconds since epoch (UTC) for the end of that local day.
     */
    fun getEndOfDayUtcMillis(localDateString: String): Long {
        val date = parseLocalDateString(localDateString) ?: Date(getUtcTimestamp()) // Default to current time if parsing fails, effectively current day.
        val calendar = Calendar.getInstance() // getInstance() uses the default timezone and locale.
        calendar.time = date
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }

    /**
     * Formats a given Calendar object into a local date string (yyyy-MM-dd),
     * based on the device's current default timezone.
     */
    fun formatCalendarToLocalDateString(calendar: Calendar): String {
        return localIsoDateFormat.format(calendar.time)
    }

    /**
     * Gets the UTC timestamp for the start of the day (00:00:00.000) for a given Calendar instance.
     */
    fun getStartOfDayUtcMillisForCalendar(calendar: Calendar): Long {
        val cal = calendar.clone() as Calendar
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Gets the UTC timestamp for the end of the day (23:59:59.999) for a given Calendar instance.
     */
    fun getEndOfDayUtcMillisForCalendar(calendar: Calendar): Long {
        val cal = calendar.clone() as Calendar
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }

    /**
     * Formats a duration in milliseconds into a human-readable string (e.g., "2h 30m", "45m", "< 1m").
     * @param millis The duration in milliseconds.
     * @return Formatted string representation of the duration.
     */
    fun formatDuration(millis: Long): String {
        if (millis < 0) return "N/A" // Invalid duration
        if (millis < TimeUnit.MINUTES.toMillis(1)) return "< 1min"

        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1)

        return when {
            hours > 0 -> String.format(Locale.getDefault(), "%dhr %02dmin", hours, minutes)
            minutes > 0 -> String.format(Locale.getDefault(), "%dmin", minutes)
            else -> "< 1min"
        }
    }
}
