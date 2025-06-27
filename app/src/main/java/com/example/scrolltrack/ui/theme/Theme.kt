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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Updated Light Color Scheme using the new palette
private val AppLightColorScheme = lightColorScheme(
    primary = CherryRed,
    onPrimary = BrandWhite,
    primaryContainer = CherryRed.copy(alpha = 0.2f),
    onPrimaryContainer = CherryRed,

    secondary = DillGreen,
    onSecondary = BrandWhite,
    secondaryContainer = DillGreen.copy(alpha = 0.2f),
    onSecondaryContainer = DillGreen,

    tertiary = AuraIndigo,
    onTertiary = OledBlack,
    tertiaryContainer = AuraIndigo.copy(alpha = 0.3f),
    onTertiaryContainer = Color(0xFF282335),

    background = AlpineOat,
    onBackground = OledBlack,

    surface = BrandWhite,
    onSurface = OledBlack,
    surfaceVariant = Color(0xFFF5F0E9), // Slightly off-white, derived from AlpineOat
    onSurfaceVariant = OledBlack.copy(alpha = 0.7f),

    error = Color(0xFFB00020),
    onError = BrandWhite,

    outline = CherryRed.copy(alpha = 0.5f)
)

// Updated Dark Color Scheme using the new palette
private val AppDarkColorScheme = darkColorScheme(
    primary = ButterYellow,
    onPrimary = OledBlack,
    primaryContainer = ButterYellow.copy(alpha = 0.2f),
    onPrimaryContainer = ButterYellow,

    secondary = AuraIndigo,
    onSecondary = OledBlack,
    secondaryContainer = AuraIndigo.copy(alpha = 0.2f),
    onSecondaryContainer = AuraIndigo,

    tertiary = DillGreen,
    onTertiary = BrandWhite,
    tertiaryContainer = DillGreen.copy(alpha = 0.2f),
    onTertiaryContainer = DillGreen,

    background = OledBlack,
    onBackground = BrandWhite,

    surface = TrueGray,
    onSurface = BrandWhite,
    surfaceVariant = TrueGray.copy(alpha = 0.8f),
    onSurfaceVariant = BrandWhite.copy(alpha = 0.8f),

    error = Color(0xFFCF6679),
    onError = OledBlack,

    outline = ButterYellow.copy(alpha = 0.5f)
)

@Composable
fun ScrollTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // Dynamic color is available on Android 12+
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
            window.statusBarColor = colorScheme.background.toArgb() // Use background for status bar
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            // For edge-to-edge, navigation bar color might be set to transparent or a translucent surface
            // window.navigationBarColor = Color.Transparent.toArgb() // Example for edge-to-edge
            // WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography, // From Type.kt
        shapes = Shapes,      // Assuming Shapes.kt is defined
        content = content
    )
}
