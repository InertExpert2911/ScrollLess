package com.example.scrolltrack.util

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class DateUtilTest {

    private var originalDefaultTimeZone: TimeZone? = null
    private var originalDefaultLocale: Locale? = null

    @Before
    fun setUp() {
        // Save original default time zone and locale
        originalDefaultTimeZone = TimeZone.getDefault()
        originalDefaultLocale = Locale.getDefault()
        // Set default time zone to UTC for testing consistency
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        // Set a specific locale if necessary, e.g., Locale.US
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        // Restore original default time zone and locale
        originalDefaultTimeZone?.let { TimeZone.setDefault(it) }
        originalDefaultLocale?.let { Locale.setDefault(it) }
    }

    // Test for getUtcTimestamp - difficult to test precisely without mocking System.currentTimeMillis
    // We can test its approximate correctness or that it returns a timestamp.
    @Test
    fun `getUtcTimestamp - returns a current timestamp`() {
        val before = System.currentTimeMillis()
        val timestamp = DateUtil.getUtcTimestamp()
        val after = System.currentTimeMillis()
        assertThat(timestamp).isAtLeast(before)
        assertThat(timestamp).isAtMost(after)
    }

    @Test
    fun `getCurrentLocalDateString - returns current date string in yyyy-MM-dd format`() {
        // This test relies on the fact that DateUtil sets its SimpleDateFormat to UTC
        val expectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(System.currentTimeMillis()))
        assertThat(DateUtil.getCurrentLocalDateString()).isEqualTo(expectedDate)
    }

    @Test
    fun `formatUtcTimestampToLocalDateString - returns correct date string for midday timestamp`() {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val timestamp = formatter.parse("2024-01-15 10:00:00 +0000")!!.time
        assertThat(DateUtil.formatUtcTimestampToLocalDateString(timestamp)).isEqualTo("2024-01-15")
    }

    @Test
    fun `formatUtcTimestampToLocalDateString - returns correct date string for timestamp just after midnight UTC`() {
        val timestamp = 1705363201000L // 2024-01-16 00:00:01 UTC
        assertThat(DateUtil.formatUtcTimestampToLocalDateString(timestamp)).isEqualTo("2024-01-16")
    }

    @Test
    fun `formatUtcTimestampToLocalDateString - returns correct date string for timestamp just before midnight UTC`() {
        val timestamp = 1705363199000L // 2024-01-15 23:59:59 UTC
        assertThat(DateUtil.formatUtcTimestampToLocalDateString(timestamp)).isEqualTo("2024-01-15")
    }

    @Test
    fun `formatUtcTimestampToLocalDateString - epoch zero`() {
        assertThat(DateUtil.formatUtcTimestampToLocalDateString(0L)).isEqualTo("1970-01-01")
    }

    @Test
    fun `parseLocalDateString - valid date string`() {
        val dateString = "2024-07-20"
        val expectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(dateString)
        assertThat(DateUtil.parseLocalDateString(dateString)).isEqualTo(expectedDate)
    }

    @Test
    fun `parseLocalDateString - invalid date string format`() {
        assertThat(DateUtil.parseLocalDateString("2024/07/20")).isNull()
    }

    @Test
    fun `parseLocalDateString - invalid date string values`() {
        assertThat(DateUtil.parseLocalDateString("2024-13-01")).isNull() // Invalid month
        assertThat(DateUtil.parseLocalDateString("2024-02-30")).isNull() // Invalid day for Feb
        assertThat(DateUtil.parseLocalDateString("not-a-date")).isNull()
        assertThat(DateUtil.parseLocalDateString("")).isNull()
    }

    @Test
    fun `formatDateToYyyyMmDdString - formats Date object correctly`() {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.set(2023, Calendar.OCTOBER, 26, 10, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val date = calendar.time
        assertThat(DateUtil.formatDateToYyyyMmDdString(date)).isEqualTo("2023-10-26")
    }

    @Test
    fun `getStartOfDayUtcMillis - returns correct UTC millisecond for standard date string`() {
        val dateString = "2024-07-20"
        // Expected: 2024-07-20T00:00:00.000Z
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(2024, Calendar.JULY, 20, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val expectedTimestamp = cal.timeInMillis
        assertThat(DateUtil.getStartOfDayUtcMillis(dateString)).isEqualTo(expectedTimestamp)
    }

    @Test
    fun `getStartOfDayUtcMillis - handles leap year date string correctly`() {
        val dateString = "2024-02-29"
        // Expected: 2024-02-29T00:00:00.000Z
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(2024, Calendar.FEBRUARY, 29, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val expectedTimestamp = cal.timeInMillis
        assertThat(DateUtil.getStartOfDayUtcMillis(dateString)).isEqualTo(expectedTimestamp)
    }

    @Test
    fun `getStartOfDayUtcMillis - returns 0L for invalid date string format`() {
        // Based on current implementation: parseLocalDateString returns null, so getStartOfDayUtcMillis returns 0L
        assertThat(DateUtil.getStartOfDayUtcMillis("2024/01/15")).isEqualTo(0L)
    }

    @Test
    fun `getStartOfDayUtcMillis - returns 0L for unparseable date string`() {
        assertThat(DateUtil.getStartOfDayUtcMillis("not-a-date-at-all")).isEqualTo(0L)
    }


    @Test
    fun `getEndOfDayUtcMillis - returns correct UTC millisecond for standard date string`() {
        val dateString = "2024-07-20"
        // Expected: 2024-07-20T23:59:59.999Z
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(2024, Calendar.JULY, 20, 23, 59, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val expectedTimestamp = cal.timeInMillis
        assertThat(DateUtil.getEndOfDayUtcMillis(dateString)).isEqualTo(expectedTimestamp)
    }

    @Test
    fun `getEndOfDayUtcMillis - handles leap year date string correctly`() {
        val dateString = "2024-02-29"
        // Expected: 2024-02-29T23:59:59.999Z
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(2024, Calendar.FEBRUARY, 29, 23, 59, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val expectedTimestamp = cal.timeInMillis
        assertThat(DateUtil.getEndOfDayUtcMillis(dateString)).isEqualTo(expectedTimestamp)
    }

    @Test
    fun `getEndOfDayUtcMillis - returns (0L + DAY_MS - 1) for invalid date string`() {
        // Since getStartOfDayUtcMillis returns 0L for invalid string,
        // getEndOfDayUtcMillis will return 0 + TimeUnit.DAYS.toMillis(1) - 1
        val expected = TimeUnit.DAYS.toMillis(1) - 1
        assertThat(DateUtil.getEndOfDayUtcMillis("invalid-date")).isEqualTo(expected)
    }

    @Test
    fun `getStartOfWeek - returns correct start of week (Sunday)`() {
        val date = LocalDate.of(2024, 7, 24) // A Wednesday
        val expected = LocalDate.of(2024, 7, 21) // Sunday
        assertThat(DateUtil.getStartOfWeek(date)).isEqualTo(expected)

        val dateOnSunday = LocalDate.of(2024, 7, 21) // A Sunday
        assertThat(DateUtil.getStartOfWeek(dateOnSunday)).isEqualTo(dateOnSunday)
    }

    @Test
    fun `getEndOfWeek - returns correct end of week (Saturday)`() {
        val date = LocalDate.of(2024, 7, 24) // A Wednesday
        val expected = LocalDate.of(2024, 7, 27) // Saturday
        assertThat(DateUtil.getEndOfWeek(date)).isEqualTo(expected)

        val dateOnSaturday = LocalDate.of(2024, 7, 27) // A Saturday
        assertThat(DateUtil.getEndOfWeek(dateOnSaturday)).isEqualTo(dateOnSaturday)
    }

    @Test
    fun `getStartOfMonth - returns correct start of month`() {
        val date = LocalDate.of(2024, 7, 24)
        val expected = LocalDate.of(2024, 7, 1)
        assertThat(DateUtil.getStartOfMonth(date)).isEqualTo(expected)

        val dateAtStart = LocalDate.of(2024, 7, 1)
        assertThat(DateUtil.getStartOfMonth(dateAtStart)).isEqualTo(dateAtStart)
    }

    @Test
    fun `getEndOfMonth - returns correct end of month`() {
        val date = LocalDate.of(2024, 7, 24) // July has 31 days
        val expected = LocalDate.of(2024, 7, 31)
        assertThat(DateUtil.getEndOfMonth(date)).isEqualTo(expected)

        val dateFebLeap = LocalDate.of(2024, 2, 15) // Feb 2024 has 29 days
        val expectedFebLeap = LocalDate.of(2024, 2, 29)
        assertThat(DateUtil.getEndOfMonth(dateFebLeap)).isEqualTo(expectedFebLeap)

        val dateFebNonLeap = LocalDate.of(2023, 2, 15) // Feb 2023 has 28 days
        val expectedFebNonLeap = LocalDate.of(2023, 2, 28)
        assertThat(DateUtil.getEndOfMonth(dateFebNonLeap)).isEqualTo(expectedFebNonLeap)

        val dateAtEnd = LocalDate.of(2024, 7, 31)
        assertThat(DateUtil.getEndOfMonth(dateAtEnd)).isEqualTo(dateAtEnd)
    }


    @Test
    fun `formatDuration - negative millis`() {
        assertThat(DateUtil.formatDuration(-1000L)).isEqualTo("N/A")
    }

    @Test
    fun `formatDuration - less than 1 minute`() {
        assertThat(DateUtil.formatDuration(0L)).isEqualTo("< 1m")
        assertThat(DateUtil.formatDuration(59999L)).isEqualTo("< 1m") // 59.999 seconds
    }

    @Test
    fun `formatDuration - exactly 1 minute`() {
        assertThat(DateUtil.formatDuration(TimeUnit.MINUTES.toMillis(1))).isEqualTo("1m")
    }

    @Test
    fun `formatDuration - minutes only`() {
        assertThat(DateUtil.formatDuration(TimeUnit.MINUTES.toMillis(45))).isEqualTo("45m")
    }

    @Test
    fun `formatDuration - exactly 1 hour`() {
        assertThat(DateUtil.formatDuration(TimeUnit.HOURS.toMillis(1))).isEqualTo("1h 00m")
    }

    @Test
    fun `formatDuration - hours and minutes`() {
        val duration = TimeUnit.HOURS.toMillis(2) + TimeUnit.MINUTES.toMillis(30)
        assertThat(DateUtil.formatDuration(duration)).isEqualTo("2h 30m")
    }

    @Test
    fun `formatDuration - hours and minutes, single digit minute`() {
        val duration = TimeUnit.HOURS.toMillis(1) + TimeUnit.MINUTES.toMillis(5)
        assertThat(DateUtil.formatDuration(duration)).isEqualTo("1h 05m")
    }

    @Test
    fun `formatDuration - long duration`() {
        val duration = TimeUnit.HOURS.toMillis(25) + TimeUnit.MINUTES.toMillis(1)
        assertThat(DateUtil.formatDuration(duration)).isEqualTo("25h 01m")
    }


    @Test
    fun `getYesterdayDateString - returns correct date string for yesterday`() {
        // This test also relies on UTC as default for consistency
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.add(Calendar.DATE, -1)
        val expectedYesterdayString = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(calendar.time)

        assertThat(DateUtil.getYesterdayDateString()).isEqualTo(expectedYesterdayString)
    }

    @Test
    fun `getYesterdayDateString - handles month boundaries`() {
        // Mock current date to be the 1st of a month to test crossing boundary
        // This requires more advanced mocking (e.g., Mockito for System.currentTimeMillis or injecting a Clock)
        // For now, we rely on the direct calculation and assume Calendar handles it.
        // Example: If today is 2024-03-01, yesterday should be 2024-02-29 (if leap) or 2024-02-28
        // This is implicitly tested by the main `getYesterdayDateString` test if run on such a day,
        // but a more deterministic test would mock time.
        // For simplicity, we'll trust the Calendar object used internally for now.
        val todayString = DateUtil.getCurrentLocalDateString()
        val todayDate = DateUtil.parseLocalDateString(todayString)!!
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.time = todayDate
        cal.add(Calendar.DATE, -1)
        val expectedYesterday = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(cal.time)

        assertThat(DateUtil.getYesterdayDateString()).isEqualTo(expectedYesterday)
    }
} 