package com.example.scrolltrack.ui.settings

import android.graphics.drawable.Drawable

data class AppVisibilityItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val visibilityState: VisibilityState
)

enum class VisibilityState {
    DEFAULT,
    VISIBLE,
    HIDDEN
} 