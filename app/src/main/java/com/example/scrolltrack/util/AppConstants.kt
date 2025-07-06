package com.example.scrolltrack.util

import java.util.concurrent.TimeUnit

object AppConstants {
    /**
     * The time window in milliseconds to consider an interaction as part of the same "active" session.
     * For example, if a user clicks and then clicks again 4 seconds later, this is considered
     * continuous active time. A 31-second gap would result in two separate active blocks.
     */
    val ACTIVE_TIME_INTERACTION_WINDOW_MS = TimeUnit.SECONDS.toMillis(30)

    /**
     * The minimum duration for a foreground session to be considered significant enough to
     * be saved in the database. Helps filter out brief flashes of apps.
     */
    val MINIMUM_SIGNIFICANT_SESSION_DURATION_MS = TimeUnit.SECONDS.toMillis(1)

    /**
     * The debounce window for counting app opens. If an app is resumed within this window
     * of its last resume, it's not counted as a new "open". This helps avoid counting
     * quick app switches (e.g., to a password manager and back) as multiple opens.
     */
    val CONTEXTUAL_APP_OPEN_DEBOUNCE_MS = TimeUnit.SECONDS.toMillis(15)

    /**
     * The maximum time gap in milliseconds between two scroll events for them to be considered
     * part of the same continuous scrolling session.
     */
    val SESSION_MERGE_GAP_MS = TimeUnit.SECONDS.toMillis(30)

    /**
     * The multiplier used in the square root model for calculating inferred scroll distance.
     */
    const val INFERRED_SCROLL_MULTIPLIER = 50.0

    /**
     * The window over which to batch content change events for a single inferred scroll calculation.
     */
    const val INFERRED_SCROLL_DEBOUNCE_MS = 1500L

    // --- Aggregation Logic ---
    const val QUICK_SWITCH_THRESHOLD_MS = 2000L // Not currently in use, but kept for future logic refinement

    // --- Data Fetching ---
    const val EVENT_FETCH_OVERLAP_MS = 10000L // 10 seconds overlap for iterative fetching
    const val BACKGROUND_USAGE_FETCH_INTERVAL_MS = 60_000L

    // --- Event Processing ---
    const val NOTIFICATION_DEBOUNCE_WINDOW_MS = 30000L // 30 seconds
    const val UNLOCK_EVENT_FOLLOW_WINDOW_MS = 2000L // 2 seconds

    // --- Legacy/Unused ---
    // These were part of an older, more complex aggregation strategy and are not currently used
    // in the simplified version but are kept for historical context or potential reuse.
    const val CONFIG_CHANGE_PEEK_AHEAD_MS = 1000L
    const val CONFIG_CHANGE_MERGE_THRESHOLD_MS = 3000L
} 