package com.example.scrolltrack.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar

class GreetingUtilTest {

    private fun getCalendarForHour(hour: Int): Calendar {
        return Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour) }
    }

    @Test
    fun getGreeting_forMorningHours_returnsGoodMorning() {
        // Test boundaries and a value within the range (5 AM to 11 AM)
        assertThat(GreetingUtil.getGreeting(getCalendarForHour(5))).isEqualTo("Good Morning! ☀️")
        assertThat(GreetingUtil.getGreeting(getCalendarForHour(8))).isEqualTo("Good Morning! ☀️")
        assertThat(GreetingUtil.getGreeting(getCalendarForHour(11))).isEqualTo("Good Morning! ☀️")
    }

    @Test
    fun getGreeting_forAfternoonHours_returnsGoodAfternoon() {
        // Test boundaries and a value within the range (12 PM to 5 PM)
        assertThat(GreetingUtil.getGreeting(getCalendarForHour(12))).isEqualTo("Good Afternoon! 🌤️")
        assertThat(GreetingUtil.getGreeting(getCalendarForHour(15))).isEqualTo("Good Afternoon! 🌤️")
        assertThat(GreetingUtil.getGreeting(getCalendarForHour(17))).isEqualTo("Good Afternoon! 🌤️")
    }

    @Test
    fun getGreeting_forEveningHours_returnsGoodEvening() {
        // Test boundaries and a value within the range (6 PM to 9 PM)
        assertThat(GreetingUtil.getGreeting(getCalendarForHour(18))).isEqualTo("Good Evening! 🌙")
        assertThat(GreetingUtil.getGreeting(getCalendarForHour(20))).isEqualTo("Good Evening! 🌙")
        assertThat(GreetingUtil.getGreeting(getCalendarForHour(21))).isEqualTo("Good Evening! 🌙")
    }

    @Test
    fun getGreeting_forNightHours_returnsGoodNight() {
        // Test boundaries and values within the range (10 PM to 4 AM)
        assertThat(GreetingUtil.getGreeting(getCalendarForHour(22))).isEqualTo("Good Night! 😴")
        assertThat(GreetingUtil.getGreeting(getCalendarForHour(23))).isEqualTo("Good Night! 😴")
        assertThat(GreetingUtil.getGreeting(getCalendarForHour(0))).isEqualTo("Good Night! 😴")
        assertThat(GreetingUtil.getGreeting(getCalendarForHour(1))).isEqualTo("Good Night! 😴")
        assertThat(GreetingUtil.getGreeting(getCalendarForHour(4))).isEqualTo("Good Night! 😴")
    }

    @Test
    fun internalGetGreetingForHour_coversAllHours_andReturnsCorrectly() {
        for (hour in 0..23) {
            val expected = when (hour) {
                in 5..11 -> "Good Morning! ☀️"
                in 12..17 -> "Good Afternoon! 🌤️"
                in 18..21 -> "Good Evening! 🌙"
                else -> "Good Night! 😴"
            }
            assertThat(GreetingUtil.getGreetingForHour(hour)).isEqualTo(expected)
        }
    }
}
