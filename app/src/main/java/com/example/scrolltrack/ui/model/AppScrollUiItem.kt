package com.example.scrolltrack.ui.model

import android.graphics.drawable.Drawable
import java.io.File

/**
 * Data class representing a scroll-related item in the UI.
 */
data class AppScrollUiItem(
    val id: String,
    val appName: String,
    val icon: File?,
    val totalScroll: Long,
    val packageName: String,
    val dataType: String
) 