package com.example.scrolltrack.ui.model

import android.graphics.drawable.Drawable

/**
 * Data class representing a usage-related item in the UI.
 */
data class AppUsageUiItem(
    val id: String,
    val appName: String,
    val icon: Drawable?,
    val usageTimeMillis: Long,
    val packageName: String
) 