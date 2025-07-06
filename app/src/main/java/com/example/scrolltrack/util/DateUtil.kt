package com.example.scrolltrack.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.concurrent.TimeUnit

object DateUtil {

    val UTC_ZONE_ID = ZoneOffset.UTC
    private val YYYY_MM_DD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US).withZone(UTC_ZONE_ID)
    private val HH_MM_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

    fun getUtcTimestamp(): Long {
        return Instant.now().toEpochMilli()
    }

    fun getCurrentLocalDateString(): String {
        return LocalDate.now(UTC_ZONE_ID).format(YYYY_MM_DD_FORMATTER)
    }

    fun formatUtcTimestampToLocalDateString(utcTimestampMillis: Long): String {
        return Instant.ofEpochMilli(utcTimestampMillis).atZone(UTC_ZONE_ID).toLocalDate().format(YYYY_MM_DD_FORMATTER)
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
        return parseLocalDate(dateString)?.atStartOfDay(UTC_ZONE_ID)?.toInstant()?.toEpochMilli() ?: 0L
    }

    fun getEndOfDayUtcMillis(dateString: String): Long {
        val startOfDay = parseLocalDate(dateString)?.atStartOfDay(UTC_ZONE_ID)
        return startOfDay?.plusDays(1)?.minusNanos(1)?.toInstant()?.toEpochMilli() ?: 0L
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
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        if (totalMinutes < 1) return "< 1m"
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) String.format("%dh %02dm", hours, minutes) else "${minutes}m"
    }

    fun getWeekOfYear(date: LocalDate): Int {
        val weekFields = WeekFields.of(Locale.getDefault())
        return date.get(weekFields.weekOfYear())
    }

    fun formatUtcTimestampToTimeString(timestamp: Long): String {
        return Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(HH_MM_FORMATTER)
    }

    fun getYesterdayDateString(): String {
        return LocalDate.now(UTC_ZONE_ID).minusDays(1).format(YYYY_MM_DD_FORMATTER)
    }

    fun getMillisUntilNextMidnight(): Long {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        return nextMidnight.toInstant().toEpochMilli() - now.toInstant().toEpochMilli()
    }

    fun getPastDateString(daysAgo: Int): String {
        return LocalDate.now(UTC_ZONE_ID).minusDays(daysAgo.toLong()).format(YYYY_MM_DD_FORMATTER)
    }
}
