package com.example.scrolltrack.ui.model

import com.example.scrolltrack.ui.limit.LimitInfo
import java.io.File

/**
 * Data class representing a usage-related item in the UI.
 */
data class AppUsageUiItem(
    val id: String,
    val appName: String,
    val icon: File?,
    val usageTimeMillis: Long,
    val packageName: String,
    val limitInfo: LimitInfo? = null
)