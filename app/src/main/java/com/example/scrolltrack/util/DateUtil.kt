package com.example.scrolltrack.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.concurrent.TimeUnit

object DateUtil {

    val localTimeZone = ZoneId.systemDefault()
    private val YYYY_MM_DD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
    private val HH_MM_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

    fun getUtcTimestamp(): Long {
        return Instant.now().toEpochMilli()
    }

    fun getCurrentLocalDateString(): String {
        return LocalDate.now(localTimeZone).format(YYYY_MM_DD_FORMATTER)
    }

    fun formatUtcTimestampToLocalDateString(utcTimestampMillis: Long): String {
        return Instant.ofEpochMilli(utcTimestampMillis).atZone(localTimeZone).toLocalDate().format(YYYY_MM_DD_FORMATTER)
    }

    fun parseLocalDate(dateString: String): LocalDate? {
        return try {
            LocalDate.parse(dateString, YYYY_MM_DD_FORMATTER)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    fun formatDateToYyyyMmDdString(date: LocalDate): String {
        return date.format(YYYY_MM_DD_FORMATTER)
    }

    fun getStartOfDayUtcMillis(dateString: String): Long {
        return parseLocalDate(dateString)?.atStartOfDay(localTimeZone)?.toInstant()?.toEpochMilli() ?: 0L
    }

    fun getEndOfDayUtcMillis(dateString: String): Long {
        val startOfDay = parseLocalDate(dateString)?.atStartOfDay(localTimeZone)
        return startOfDay?.plusDays(1)?.minusNanos(1)?.toInstant()?.toEpochMilli() ?: 0L
    }

    fun getStartOfToday(): ZonedDateTime {
        return LocalDate.now().atStartOfDay(localTimeZone)
    }

    fun formatUtcTimestampToLocalDateTime(utcTimestampMillis: Long): LocalDateTime {
        return Instant.ofEpochMilli(utcTimestampMillis).atZone(localTimeZone).toLocalDateTime()
    }

    fun getStartOfWeek(date: LocalDate): LocalDate {
        val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        return date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    }

    fun getEndOfWeek(date: LocalDate): LocalDate {
        val lastDayOfWeek = DayOfWeek.of(((WeekFields.of(Locale.getDefault()).firstDayOfWeek.value + 5) % 7) + 1)
        return date.with(TemporalAdjusters.nextOrSame(lastDayOfWeek))
    }

    fun getStartOfMonth(date: LocalDate): LocalDate {
        return date.with(TemporalAdjusters.firstDayOfMonth())
    }

    fun getEndOfMonth(date: LocalDate): LocalDate {
        return date.with(TemporalAdjusters.lastDayOfMonth())
    }

    fun formatDuration(millis: Long): String {
        if (millis < 0) return "N/A"
        if (millis == 0L) return "0m"
        if (millis < 60000) return "< 1m" // 60,000 ms = 1 minute

        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.HOURS.toMinutes(hours)

        return when {
            hours > 0 -> String.format(Locale.US, "%dh %dm", hours, minutes)
            else -> String.format(Locale.US, "%dm", minutes)
        }
    }

    fun formatDurationWithSeconds(millis: Long): String {
        if (millis < 0) return "0s"
        if (millis < 1000) return "0s"
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60

        return when {
            hours > 0 -> String.format(Locale.US, "%dh %dm", hours, minutes)
            minutes > 0 -> String.format(Locale.US, "%dm %ds", minutes, seconds)
            else -> String.format(Locale.US, "%ds", seconds)
        }
    }

    fun getWeekOfYear(date: LocalDate): Int {
        val weekFields = WeekFields.of(Locale.getDefault())
        return date.get(weekFields.weekOfYear())
    }

    fun formatUtcTimestampToTimeString(timestamp: Long): String {
        return Instant.ofEpochMilli(timestamp)
            .atZone(localTimeZone)
            .toLocalTime()
            .format(HH_MM_FORMATTER)
    }

    fun getYesterdayDateString(): String {
        return LocalDate.now(localTimeZone).minusDays(1).format(YYYY_MM_DD_FORMATTER)
    }

    fun getMillisUntilNextMidnight(): Long {
        val now = ZonedDateTime.now(localTimeZone)
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        return nextMidnight.toInstant().toEpochMilli() - now.toInstant().toEpochMilli()
    }

    fun getPastDateString(daysAgo: Int): String {
        return LocalDate.now(localTimeZone).minusDays(daysAgo.toLong()).format(YYYY_MM_DD_FORMATTER)
    }

    fun getPastDateString(daysAgo: Int, fromDate: LocalDate?): String {
        val date = fromDate ?: LocalDate.now(localTimeZone)
        return date.minusDays(daysAgo.toLong()).format(YYYY_MM_DD_FORMATTER)
    }

    fun getFormattedDate(): String {
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault())
        return today.format(formatter)
    }

    fun formatLocalDateToString(date: LocalDate): String {
        return date.format(YYYY_MM_DD_FORMATTER)
    }

    fun getWeekRange(date: LocalDate): Pair<LocalDate, LocalDate> {
        val startOfWeek = getStartOfWeek(date)
        val endOfWeek = getEndOfWeek(date)
        return Pair(startOfWeek, endOfWeek)
    }

    fun getMonthRange(date: LocalDate): Pair<LocalDate, LocalDate> {
        val startOfMonth = getStartOfMonth(date)
        val endOfMonth = getEndOfMonth(date)
        return Pair(startOfMonth, endOfMonth)
    }
}