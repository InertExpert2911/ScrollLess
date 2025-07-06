package com.example.scrolltrack.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class GreetingUtilTest {

    private val greetingUtil = GreetingUtil()

    @Test
    fun `getGreeting returns correct morning greeting`() {
        for (hour in 5..11) {
            assertEquals("Good Morning! ☀️", greetingUtil.getGreeting(LocalTime.of(hour, 0)))
        }
    }

    @Test
    fun `getGreeting returns correct afternoon greeting`() {
        for (hour in 12..17) {
            assertEquals("Good Afternoon! 🌤️", greetingUtil.getGreeting(LocalTime.of(hour, 0)))
        }
    }

    @Test
    fun `getGreeting returns correct evening greeting`() {
        for (hour in 18..21) {
            assertEquals("Good Evening! 🌙", greetingUtil.getGreeting(LocalTime.of(hour, 0)))
        }
    }

    @Test
    fun `getGreeting returns correct night greeting`() {
        val nightHours = (22..23) + (0..4)
        for (hour in nightHours) {
            assertEquals("Good Night! 😴", greetingUtil.getGreeting(LocalTime.of(hour, 0)))
        }
    }
}
