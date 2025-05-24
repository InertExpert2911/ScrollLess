package com.example.scrolltrack.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

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

// Custom Light Color Scheme based on the inspirational UI (COMMENTING OUT as its colors are not defined)
/*
private val AppLightColorScheme = lightColorScheme(
    primary = AppPrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = AppGraphBlueLight, // A lighter shade for containers, if needed
    onPrimaryContainer = AppPrimaryBlue,    // Text/icons on primary container
    secondary = AppSecondaryPurple,          // Example: Can be used for accents
    onSecondary = Color.White,
    secondaryContainer = AppGraphPurpleLight,
    onSecondaryContainer = AppSecondaryPurple,
    tertiary = AppAccentGreen,             // Example: Can be used for other accents
    onTertiary = Color.White,
    tertiaryContainer = AppGraphGreenLight,
    onTertiaryContainer = AppAccentGreen,
    error = md_theme_light_error,       // Keep Material default for error
    onError = md_theme_light_onError,
    errorContainer = md_theme_light_errorContainer,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = AppLightBackground,
    onBackground = AppTextPrimary,
    surface = AppCardBackground,          // Cards and main surfaces
    onSurface = AppTextPrimary,
    surfaceVariant = AppLightBackground,    // Can be used for slightly different background tones
    onSurfaceVariant = AppTextSecondary,    // Secondary text color
    outline = AppDividerColor,            // Dividers and borders
    outlineVariant = AppDividerColor,       // Slightly different variant if needed
    inverseSurface = md_theme_light_inverseSurface, // Keep Material defaults for inverse
    inverseOnSurface = md_theme_light_inverseOnSurface,
    inversePrimary = md_theme_light_inversePrimary,
    surfaceTint = AppPrimaryBlue,           // Tint color for surfaces, often primary
    scrim = Color.Black.copy(alpha = 0.32f) // Standard scrim color
    // surfaceContainer, etc. can be mapped to AppLightBackground or AppCardBackground
    // For simplicity, we'll rely on surface, background, and surfaceVariant for now.
)
*/

// Default M3 Light Colors (from Color.kt)
private val DefaultLightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    onError = md_theme_light_onError,
    errorContainer = md_theme_light_errorContainer,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    outlineVariant = md_theme_light_outlineVariant,
    inverseSurface = md_theme_light_inverseSurface,
    inverseOnSurface = md_theme_light_inverseOnSurface,
    inversePrimary = md_theme_light_inversePrimary,
    surfaceTint = md_theme_light_surfaceTint,
    scrim = md_theme_light_scrim,
    surfaceContainerLowest = md_theme_light_surfaceContainerLowest,
    surfaceContainerLow = md_theme_light_surfaceContainerLow,
    surfaceContainer = md_theme_light_surfaceContainer,
    surfaceContainerHigh = md_theme_light_surfaceContainerHigh,
    surfaceContainerHighest = md_theme_light_surfaceContainerHighest
)

// Custom "Vibrant Dark" Color Scheme using colors from Color.kt
private val AppNewDarkColorScheme = darkColorScheme(
    primary = VibrantPrimaryBlue,
    onPrimary = VibrantOnPrimaryBlue,
    primaryContainer = VibrantPrimaryBlueDarker,
    onPrimaryContainer = VibrantDarkOnSurface, // Text on primary container

    secondary = VibrantSecondaryGreen,
    onSecondary = VibrantOnSecondaryGreen,
    secondaryContainer = VibrantSecondaryGreenDarker,
    onSecondaryContainer = VibrantDarkOnSurface, // Text on secondary container

    tertiary = VibrantTertiaryPurple, // Using the renamed VibrantTertiaryPurple
    onTertiary = VibrantOnTertiaryPurple,
    tertiaryContainer = VibrantTertiaryPurpleDarker,
    onTertiaryContainer = VibrantDarkOnSurface, // Text on tertiary container

    error = VibrantErrorRed,
    onError = VibrantOnErrorRed,
    errorContainer = md_theme_dark_errorContainer, // Default M3 for error container
    onErrorContainer = md_theme_dark_onErrorContainer, // Default M3 for on error container

    background = VibrantDarkBackground,
    onBackground = VibrantDarkOnBackground,
    surface = VibrantDarkSurface,
    onSurface = VibrantDarkOnSurface,
    surfaceVariant = VibrantDarkSurfaceVariant,
    onSurfaceVariant = VibrantDarkOnSurfaceVariant,

    outline = VibrantDivider,
    outlineVariant = VibrantDarkSurfaceVariant, // Using a slightly darker variant for less emphasis on some outlines

    inverseSurface = md_theme_light_surface, // Material default for light surface as inverse
    inverseOnSurface = md_theme_light_onSurface, // Material default for text on light surface as inverse
    inversePrimary = VibrantPrimaryBlue, // Primary color when inverted

    surfaceTint = VibrantPrimaryBlue, // Tint for surfaces, usually primary
    scrim = Color.Black.copy(alpha = 0.6f) // Standard scrim for dark themes
    // Note: surfaceContainer roles can be implicitly handled by 'surface' and 'background'
    // or explicitly defined if more nuanced layering is needed. For now, relying on the main ones.
)

@Composable
fun ScrollTrackTheme(
    darkThemeUserPreference: Boolean = true, // Defaulting to dark as per current design focus
    dynamicColor: Boolean = true,      // Allowing dynamic color by default, user preference can override
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkThemeUserPreference) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkThemeUserPreference -> AppNewDarkColorScheme
        else -> DefaultLightColorScheme // Fallback to default M3 light if not dynamic and not dark preference
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
