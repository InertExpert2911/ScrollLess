package com.example.scrolltrack.ui.settings

import android.graphics.drawable.Drawable

data class AppVisibilityItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val visibilityState: VisibilityState,
    val isSystemApp: Boolean,
    val isDefaultVisible: Boolean
)

enum class VisibilityState {
    DEFAULT,
    VISIBLE,
    HIDDEN
}
