package com.example.scrolltrack.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Light Color Scheme using Brand Colors
private val AppLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    error = LightError,
    onError = LightOnError
    // You can also define primaryContainer, secondaryContainer, tertiary, etc.
    // if needed, using combinations of your brand colors or neutrals.
    // For example:
    // primaryContainer = BrandBlue.copy(alpha = 0.1f),
    // onPrimaryContainer = BrandBlue,
)

// Dark Color Scheme using Brand Colors
private val AppDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    error = DarkError,
    onError = DarkOnError
    // Similarly, define container colors if needed.
    // For example:
    // primaryContainer = BrandBlue.copy(alpha = 0.2f), // A slightly more opaque container for dark theme
    // onPrimaryContainer = BrandWhite, // Or a lighter shade of BrandBlue
)

@Composable
fun ScrollTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> AppDarkColorScheme
        else -> AppLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Set status bar color
            window.statusBarColor = colorScheme.background.toArgb()
            // Set status bar icons (light or dark)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            // Optional: Set navigation bar color and icons (if you're not using edge-to-edge)
            // window.navigationBarColor = colorScheme.background.toArgb() // Or a specific nav bar color
            // WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography, // Use the AppTypography defined in Type.kt
        shapes = Shapes, // Assuming Shapes.kt is defined and suitable
        content = content
    )
}
