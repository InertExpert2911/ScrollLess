package com.example.scrolltrack.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Explicitly import our custom AppTypography
import com.example.scrolltrack.ui.theme.AppTypography as Typography

@Composable
fun ScrollTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // dynamicColor is not currently used from MainActivity, but we keep it for future M3 alignment
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme // Use our new, robust DarkColorScheme from Color.kt
        else -> LightColorScheme      // Use our new, robust LightColorScheme from Color.kt
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Set status bar color to match the app's background
            window.statusBarColor = colorScheme.background.toArgb()
            // Set status bar icons to be light or dark based on the theme
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // This will now correctly resolve to our AppTypography
        shapes = Shapes,         // Assuming Shapes is defined in Shapes.kt
        content = content
    )
}
