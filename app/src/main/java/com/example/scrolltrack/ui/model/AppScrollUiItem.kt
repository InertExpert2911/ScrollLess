package com.example.scrolltrack.ui.model

import android.graphics.drawable.Drawable

/**
 * Data class representing a scroll-related item in the UI.
 */
data class AppScrollUiItem(
    val id: String,
    val appName: String,
    val icon: Drawable?,
    val totalScroll: Long,
    val packageName: String
) 