package com.example.scrolltrack.util

/**
 * An interface to abstract the system clock, allowing for dependency injection in tests.
 */
interface Clock {
    fun currentTimeMillis(): Long
}

/**
 * The default implementation of the [Clock] interface, which uses the real system clock.
 */
class SystemClock : Clock {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
