package com.example.scrolltrack.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.* //ktlint-disable no-wildcard-imports
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Assuming your Color.kt (artifact id: color_kt_scrolltrack) defines these:
// md_theme_dark_primary, md_theme_dark_onPrimary, etc.
// md_theme_light_primary, md_theme_light_onPrimary, etc.

// Default M3 Dark Colors (from Color.kt)
private val DefaultDarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
    errorContainer = md_theme_dark_errorContainer,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    outlineVariant = md_theme_dark_outlineVariant,
    inverseSurface = md_theme_dark_inverseSurface,
    inverseOnSurface = md_theme_dark_inverseOnSurface,
    inversePrimary = md_theme_dark_inversePrimary,
    surfaceTint = md_theme_dark_surfaceTint,
    scrim = md_theme_dark_scrim,
    surfaceContainerLowest = md_theme_dark_surfaceContainerLowest,
    surfaceContainerLow = md_theme_dark_surfaceContainerLow,
    surfaceContainer = md_theme_dark_surfaceContainer,
    surfaceContainerHigh = md_theme_dark_surfaceContainerHigh,
    surfaceContainerHighest = md_theme_dark_surfaceContainerHighest
)

// Custom Professional Light Color Scheme
private val ProfessionalLightColorScheme = lightColorScheme(
    primary = ProBlue,
    onPrimary = Color.White, // From M3: Text on strong blue should be white
    primaryContainer = ProLightBgPaleBlue, // Using pale blue as a light container
    onPrimaryContainer = ProBlue, // Text on pale blue container should be strong blue
    secondary = ProDarkTeal,
    onSecondary = Color.White, // Text on dark teal should be white
    secondaryContainer = ProLightBgGray, // Using light gray as a secondary container
    onSecondaryContainer = ProDarkTeal, // Text on light gray container
    tertiary = ProAccentGreen, // Accent for FABs, highlights
    onTertiary = ProTextCharcoal, // Text on light green accent
    tertiaryContainer = Color(0xFFB0EAC0), // Lighter green for tertiary container
    onTertiaryContainer = ProDarkTeal, // Text on tertiary container
    error = md_theme_light_error,
    onError = md_theme_light_onError,
    errorContainer = md_theme_light_errorContainer,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = ProLightBgPaleBlue,
    onBackground = ProTextCharcoal,
    surface = ProLightBgPaleBlue, // Or ProLightBgGray for cards if desired
    onSurface = ProTextCharcoal,
    surfaceVariant = ProLightBgGray, // For elements like dividers, card outlines on pale blue
    onSurfaceVariant = ProTextCharcoal,
    outline = ProDarkTeal.copy(alpha = 0.5f), // Softer outline
    outlineVariant = ProDarkTeal.copy(alpha = 0.3f), // Even softer
    inverseSurface = ProDarkBgCharcoal,
    inverseOnSurface = ProTextLightGray,
    inversePrimary = ProBlue.copy(alpha = 0.8f), // Lighter blue for inverse primary
    surfaceTint = ProBlue,
    scrim = Color.Black.copy(alpha = 0.32f)
)

// Custom Professional Dark Color Scheme
private val ProfessionalDarkColorScheme = darkColorScheme(
    primary = ProBlueDarkPrimary, // Was ProBlue
    onPrimary = OnProBlueDarkPrimary, // Was ProTextPaleBlue
    primaryContainer = Color(0xFF002A5C), 
    onPrimaryContainer = ProTextPaleBlue, 
    secondary = ProDarkTealDarkSecondary, // Was ProDarkTeal
    onSecondary = OnProDarkTealDarkSecondary, // Was ProTextPaleBlue
    secondaryContainer = Color(0xFF002F37), 
    onSecondaryContainer = ProTextPaleBlue, 
    tertiary = ProAccentGreen,
    onTertiary = ProTextCharcoal, // Charcoal text on light green accent
    tertiaryContainer = Color(0xFF003E20), // Darker green for tertiary container
    onTertiaryContainer = ProAccentGreen, // Light green text on dark green container
    error = UsageStatusRed, // md_theme_dark_error,
    onError = Color.White, // md_theme_dark_onError,
    errorContainer = Color(0xFFB00020),// md_theme_dark_errorContainer,
    onErrorContainer = ProTextLightGray, // md_theme_dark_onErrorContainer,
    background = ProDarkBgCharcoal,
    onBackground = ProTextLightGray,
    surface = ProDarkBgCharcoal,
    onSurface = ProTextLightGray,
    surfaceVariant = Color(0xFF2A363D), // Slightly lighter charcoal for variants
    onSurfaceVariant = ProTextPaleBlue, // Use pale blue for better contrast on variant
    outline = ProLightBgGray.copy(alpha = 0.5f),
    outlineVariant = ProLightBgGray.copy(alpha = 0.3f),
    inverseSurface = ProLightBgPaleBlue,
    inverseOnSurface = ProTextCharcoal,
    inversePrimary = ProBlue.copy(alpha = 0.8f) // Lighter blue for inverse
)

// Updated OLED Pitch Dark Theme with Professional Palette Accents
private val AppNewDarkColorScheme = darkColorScheme(
    primary = ProBlueDarkPrimary, // Was ProBlue
    onPrimary = OnProBlueDarkPrimary, // Was ProTextPaleBlue
    primaryContainer = Color(0xFF002A5C), 
    onPrimaryContainer = ProTextPaleBlue, 
    secondary = ProDarkTealDarkSecondary, // Was ProDarkTeal
    onSecondary = OnProDarkTealDarkSecondary, // Was ProTextPaleBlue
    secondaryContainer = Color(0xFF002F37), 
    onSecondaryContainer = ProTextPaleBlue, 
    tertiary = ProAccentGreen, 
    onTertiary = ProTextCharcoal, // Text on the light green accent
    tertiaryContainer = Color(0xFF003E20), // Dark green container for tertiary elements
    onTertiaryContainer = ProAccentGreen, // Light green text on dark green container
    error = UsageStatusRed,
    onError = Color.White,
    errorContainer = Color(0xFFB00020), // Standard dark error container
    onErrorContainer = NewLightGray, // Keep existing or use ProTextPaleBlue
    background = NewBlack, // True black for OLED
    onBackground = ProTextLightGray, // Light gray text on black
    surface = NewBlack, // True black for OLED surfaces (cards, sheets)
    onSurface = ProTextLightGray, // Light gray text on black surfaces
    surfaceVariant = NewDarkGray, // Dark gray for subtle variations (e.g., dividers on black)
    onSurfaceVariant = ProTextPaleBlue, // Lighter text for onSurfaceVariant if NewDarkGray is very dark
    outline = ProLightBgGray.copy(alpha = 0.3f), // Softer outline for OLED
    outlineVariant = NewDarkGray, // For dividers etc.
    inverseSurface = ProLightBgPaleBlue, // Light background for inverse
    inverseOnSurface = ProTextCharcoal, // Charcoal text on light inverse
    inversePrimary = ProBlue.copy(alpha = 0.8f) // Lighter blue for inverse
)

// AppLightColorScheme - Now maps to ProfessionalLightColorScheme
private val AppLightColorScheme = ProfessionalLightColorScheme

@Composable
fun ScrollTrackTheme(
    dynamicColor: Boolean = false, // Default to false to ensure custom themes are active
    themeVariant: String, // No default, should be provided by ViewModel
    content: @Composable () -> Unit
) {
    val useDarkUltimately = when (themeVariant) {
        "light" -> false
        else -> true // "dark" and "oled_dark" are dark themes
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        themeVariant == "light" -> ProfessionalLightColorScheme
        themeVariant == "dark" -> ProfessionalDarkColorScheme
        themeVariant == "oled_dark" -> AppNewDarkColorScheme
        else -> AppNewDarkColorScheme // Fallback to OLED dark if variant is unknown
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDarkUltimately // Adjusted based on final dark mode decision
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
