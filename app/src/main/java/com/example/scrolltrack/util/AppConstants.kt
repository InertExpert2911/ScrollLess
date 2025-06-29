package com.example.scrolltrack.util

object AppConstants {
    // --- Aggregation Logic ---
    const val MINIMUM_SIGNIFICANT_SESSION_DURATION_MS = 2000L // Ignore sessions shorter than this
    const val QUICK_SWITCH_THRESHOLD_MS = 2000L // Not currently in use, but kept for future logic refinement
    const val ACTIVE_TIME_INTERACTION_WINDOW_MS = 2000L // Define the active time interaction window

    // --- Data Fetching ---
    const val EVENT_FETCH_OVERLAP_MS = 10000L // 10 seconds overlap for iterative fetching

    // --- Event Processing ---
    const val NOTIFICATION_DEBOUNCE_WINDOW_MS = 30000L // 30 seconds
    const val UNLOCK_EVENT_FOLLOW_WINDOW_MS = 2000L // 2 seconds

    // --- Legacy/Unused ---
    // These were part of an older, more complex aggregation strategy and are not currently used
    // in the simplified version but are kept for historical context or potential reuse.
    const val CONFIG_CHANGE_PEEK_AHEAD_MS = 1000L
    const val CONFIG_CHANGE_MERGE_THRESHOLD_MS = 3000L
} 