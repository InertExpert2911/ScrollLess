package com.example.scrolltrack.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// 1. Brand Color Palette
val CherryRed = Color(0xFF74070E)
val DillGreen = Color(0xFF4E6813)
val AuraIndigo = Color(0xFF80A6DF)
val AlpineOat = Color(0xFFF0E7DA)
val ButterYellow = Color(0xFFFFEDA8) // Keep for custom accents if needed

// 2. M3 Light Theme Color Scheme
val LightColorScheme = lightColorScheme(
    primary = Color(0xFF9A4045),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDADB),
    onPrimaryContainer = Color(0xFF400008),
    secondary = Color(0xFF4E6813),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFEE8C),
    onSecondaryContainer = Color(0xFF141F00),
    tertiary = Color(0xFF0061A2),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD0E4FF),
    onTertiaryContainer = Color(0xFF001D36),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = AlpineOat,
    onBackground = Color(0xFF201A1A),
    surface = AlpineOat,
    onSurface = Color(0xFF201A1A),
    surfaceVariant = Color(0xFFF4DDDD),
    onSurfaceVariant = Color(0xFF524343),
    outline = Color(0xFF847373),
    inverseOnSurface = Color(0xFFFBEEED),
    inverseSurface = Color(0xFF362F2F),
    inversePrimary = Color(0xFFFFB3B4),
    surfaceTint = Color(0xFF9A4045),
)

// 3. M3 Dark (OLED) Theme Color Scheme
val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB3B4),
    onPrimary = Color(0xFF5F121B),
    primaryContainer = Color(0xFF7C292F),
    onPrimaryContainer = Color(0xFFFFDADB),
    secondary = Color(0xFFB4D173),
    onSecondary = Color(0xFF253600),
    secondaryContainer = Color(0xFF384E00),
    onSecondaryContainer = Color(0xFFCFEE8C),
    tertiary = Color(0xFF9ACBFF),
    onTertiary = Color(0xFF003258),
    tertiaryContainer = Color(0xFF00497D),
    onTertiaryContainer = Color(0xFFD0E4FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color.Black,
    onBackground = Color(0xFFECE0DF),
    surface = Color.Black, // Pure black for OLED
    onSurface = Color(0xFFECE0DF),
    surfaceVariant = Color(0xFF524343),
    onSurfaceVariant = Color(0xFFD6C2C2),
    outline = Color(0xFF9F8C8C),
    inverseOnSurface = Color(0xFF201A1A),
    inverseSurface = Color(0xFFECE0DF),
    inversePrimary = Color(0xFF9A4045),
    surfaceTint = Color(0xFFFFB3B4),
    // A dark gray for cards to stand out from the pure black background
    surfaceContainer = Color(0xFF1D1B20)
)
