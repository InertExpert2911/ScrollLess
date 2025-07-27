package com.example.scrolltrack.util

/**
 * A controllable clock implementation for use in tests.
 */
class TestClock(private var initialTime: Long = 0L) : Clock {

    override fun currentTimeMillis(): Long {
        return initialTime
    }

    fun setCurrentTimeMillis(time: Long) {
        initialTime = time
    }

    fun advanceTimeMillis(duration: Long) {
        initialTime += duration
    }
}