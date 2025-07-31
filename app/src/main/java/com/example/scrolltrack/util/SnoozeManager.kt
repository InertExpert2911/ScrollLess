package com.example.scrolltrack.util

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SnoozeManager @Inject constructor(
    @ApplicationContext context: Context,
    private val clock: Clock
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("snooze_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val SNOOZE_UNTIL_PREFIX = "snooze_until_timestamp_"
        val SNOOZE_DURATION_MS = 5 * 60 * 1000L // 5 minutes
    }

    fun snoozeGroup(groupId: Long) {
        val snoozeUntil = clock.currentTimeMillis() + SNOOZE_DURATION_MS
        prefs.edit().putLong("$SNOOZE_UNTIL_PREFIX$groupId", snoozeUntil).apply()
    }

    fun isGroupSnoozed(groupId: Long): Boolean {
        val snoozeUntil = prefs.getLong("$SNOOZE_UNTIL_PREFIX$groupId", 0L)
        return clock.currentTimeMillis() < snoozeUntil
    }
}