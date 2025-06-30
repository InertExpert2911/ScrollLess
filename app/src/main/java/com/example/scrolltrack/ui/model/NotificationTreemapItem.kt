package com.example.scrolltrack.ui.model

import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color

/**
 * A data class representing a single item in the notification treemap visualization.
 * It contains all the necessary data for the UI to render one rectangle.
 */
data class NotificationTreemapItem(
    val packageName: String,
    val appName: String,
    val count: Int,
    val icon: Drawable?,
    val color: Color
) 