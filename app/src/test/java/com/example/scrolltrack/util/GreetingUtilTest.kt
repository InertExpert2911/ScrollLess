package com.example.scrolltrack.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar

class GreetingUtilTest {

    // Test cases for getGreetingForHour, covering boundary conditions

    @Test
    fun `getGreetingForHour - morning hours (5-11)`() {
        assertThat(GreetingUtil.getGreetingForHour(5)).isEqualTo("Good Morning! ☀️")
        assertThat(GreetingUtil.getGreetingForHour(8)).isEqualTo("Good Morning! ☀️")
        assertThat(GreetingUtil.getGreetingForHour(11)).isEqualTo("Good Morning! ☀️")
    }

    @Test
    fun `getGreetingForHour - afternoon hours (12-17)`() {
        assertThat(GreetingUtil.getGreetingForHour(12)).isEqualTo("Good Afternoon! 🌤️")
        assertThat(GreetingUtil.getGreetingForHour(15)).isEqualTo("Good Afternoon! 🌤️")
        assertThat(GreetingUtil.getGreetingForHour(17)).isEqualTo("Good Afternoon! 🌤️")
    }

    @Test
    fun `getGreetingForHour - evening hours (18-21)`() {
        assertThat(GreetingUtil.getGreetingForHour(18)).isEqualTo("Good Evening! 🌙")
        assertThat(GreetingUtil.getGreetingForHour(20)).isEqualTo("Good Evening! 🌙")
        assertThat(GreetingUtil.getGreetingForHour(21)).isEqualTo("Good Evening! 🌙")
    }

    @Test
    fun `getGreetingForHour - night hours (22-4)`() {
        // Range includes 22, 23, 0, 1, 2, 3, 4
        assertThat(GreetingUtil.getGreetingForHour(22)).isEqualTo("Good Night! 😴")
        assertThat(GreetingUtil.getGreetingForHour(23)).isEqualTo("Good Night! 😴")
        assertThat(GreetingUtil.getGreetingForHour(0)).isEqualTo("Good Night! 😴")
        assertThat(GreetingUtil.getGreetingForHour(1)).isEqualTo("Good Night! 😴")
        assertThat(GreetingUtil.getGreetingForHour(4)).isEqualTo("Good Night! 😴")
    }

    @Test
    fun `getGreetingForHour - edge case hour 0`() {
        assertThat(GreetingUtil.getGreetingForHour(0)).isEqualTo("Good Night! 😴")
    }

    @Test
    fun `getGreetingForHour - edge case hour 4`() {
        assertThat(GreetingUtil.getGreetingForHour(4)).isEqualTo("Good Night! 😴")
    }

    @Test
    fun `getGreetingForHour - edge case hour 5`() {
        assertThat(GreetingUtil.getGreetingForHour(5)).isEqualTo("Good Morning! ☀️")
    }

    @Test
    fun `getGreetingForHour - edge case hour 11`() {
        assertThat(GreetingUtil.getGreetingForHour(11)).isEqualTo("Good Morning! ☀️")
    }

    @Test
    fun `getGreetingForHour - edge case hour 12`() {
        assertThat(GreetingUtil.getGreetingForHour(12)).isEqualTo("Good Afternoon! 🌤️")
    }

    @Test
    fun `getGreetingForHour - edge case hour 17`() {
        assertThat(GreetingUtil.getGreetingForHour(17)).isEqualTo("Good Afternoon! 🌤️")
    }

    @Test
    fun `getGreetingForHour - edge case hour 18`() {
        assertThat(GreetingUtil.getGreetingForHour(18)).isEqualTo("Good Evening! 🌙")
    }

    @Test
    fun `getGreetingForHour - edge case hour 21`() {
        assertThat(GreetingUtil.getGreetingForHour(21)).isEqualTo("Good Evening! 🌙")
    }

    @Test
    fun `getGreetingForHour - edge case hour 22`() {
        assertThat(GreetingUtil.getGreetingForHour(22)).isEqualTo("Good Night! 😴")
    }


    // Test for getGreeting()
    // This test is more of an integration test for GreetingUtil,
    // as it relies on the correctness of getGreetingForHour and Calendar.getInstance().
    // To truly unit test getGreeting(), Calendar.getInstance() would need to be mocked.
    // However, we can verify that it returns one of the expected greetings.
    @Test
    fun `getGreeting - returns a valid greeting string`() {
        val greeting = GreetingUtil.getGreeting()
        val possibleGreetings = listOf(
            "Good Morning! ☀️",
            "Good Afternoon! 🌤️",
            "Good Evening! 🌙",
            "Good Night! 😴"
        )
        assertThat(greeting).isIn(possibleGreetings)
    }

    @Test
    fun `getGreeting - output consistency with getGreetingForHour`() {
        // This test checks that getGreeting's output is consistent with getGreetingForHour
        // for the current hour of the day.
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val expectedGreetingForCurrentHour = GreetingUtil.getGreetingForHour(currentHour)
        assertThat(GreetingUtil.getGreeting()).isEqualTo(expectedGreetingForCurrentHour)
    }
} 