package com.example.scrolltrack.util

import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingUtilTest {

    @Test
    fun `getGreetingForHour returns Good Morning for morning hours`() {
        assertEquals("Good Morning! ☀️", GreetingUtil.getGreetingForHour(5))
        assertEquals("Good Morning! ☀️", GreetingUtil.getGreetingForHour(11))
    }

    @Test
    fun `getGreetingForHour returns Good Afternoon for afternoon hours`() {
        assertEquals("Good Afternoon! 🌤️", GreetingUtil.getGreetingForHour(12))
        assertEquals("Good Afternoon! 🌤️", GreetingUtil.getGreetingForHour(17))
    }

    @Test
    fun `getGreetingForHour returns Good Evening for evening hours`() {
        assertEquals("Good Evening! 🌙", GreetingUtil.getGreetingForHour(18))
        assertEquals("Good Evening! 🌙", GreetingUtil.getGreetingForHour(21))
    }

    @Test
    fun `getGreetingForHour returns Good Night for night hours`() {
        assertEquals("Good Night! 😴", GreetingUtil.getGreetingForHour(22))
        assertEquals("Good Night! 😴", GreetingUtil.getGreetingForHour(4))
    }
} 