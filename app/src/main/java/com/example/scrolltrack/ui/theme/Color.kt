package com.example.scrolltrack.ui.theme

import androidx.compose.ui.graphics.Color

// New Cherry Red Palette
val CherryBeige = Color(0xFFF0E7DA)
val CherryOlive = Color(0xFF4E6813)
val CherryLavender = Color(0xFFB0A6DF)
val CherryYellow = Color(0xFFFFEDA8)
val CherryMaroon = Color(0xFF74070E)
val BrandBlack = Color(0xFF000000) // True black for OLED
val BrandWhite = Color(0xFFFFFFFF)

// Light Theme Semantic Colors - Expressive & Popping
val LightPrimary = CherryMaroon // Maroon as a strong primary
val LightOnPrimary = BrandWhite
val LightPrimaryContainer = Color(0xFFFADADD) // Lighter shade of Maroon/Red
val LightOnPrimaryContainer = CherryMaroon.copy(alpha = 0.8f) // Darker Maroon for text on light container

val LightSecondary = CherryOlive // Olive as secondary
val LightOnSecondary = BrandWhite
val LightSecondaryContainer = Color(0xFFDCE8BB) // Lighter Olive
val LightOnSecondaryContainer = CherryOlive.copy(alpha = 0.8f)

val LightTertiary = CherryLavender // Lavender as tertiary/accent
val LightOnTertiary = BrandBlack // Black text on Lavender
val LightTertiaryContainer = Color(0xFFE8E0FF) // Lighter Lavender
val LightOnTertiaryContainer = CherryLavender.copy(red = 0.4f, green = 0.35f, blue = 0.6f) // Darker Lavender

val LightBackground = CherryBeige // Beige as the main light background
val LightOnBackground = BrandBlack // High contrast black text on beige

val LightSurface = BrandWhite // White for cards, dialogs for a clean pop
val LightOnSurface = BrandBlack
val LightSurfaceVariant = CherryBeige.copy(alpha=0.7f) // Slightly darker/variant of beige for dividers etc.
val LightOnSurfaceVariant = BrandBlack.copy(alpha = 0.7f)

val LightError = Color(0xFFB00020) // Standard M3 error
val LightOnError = BrandWhite
val LightErrorContainer = Color(0xFFFCDDE0)
val LightOnErrorContainer = Color(0xFF410E0B)

val LightOutline = CherryMaroon.copy(alpha = 0.5f)
val LightOutlineVariant = CherryOlive.copy(alpha = 0.3f)

val LightSurfaceDim = CherryBeige.copy(red = 0.9f, green = 0.85f, blue = 0.8f) // Dimmer Beige
val LightSurfaceBright = BrandWhite // Brightest surface
val LightSurfaceContainerLowest = CherryBeige.copy(red = 0.95f, green = 0.92f, blue = 0.88f) // Very light beige
val LightSurfaceContainerLow = CherryBeige.copy(red = 0.92f, green = 0.88f, blue = 0.84f)
val LightSurfaceContainer = CherryBeige // Beige as standard container
val LightSurfaceContainerHigh = CherryBeige.copy(red = 0.85f, green = 0.80f, blue = 0.75f)
val LightSurfaceContainerHighest = CherryBeige.copy(red = 0.8f, green = 0.75f, blue = 0.7f) // Darkest beige variant for surfaces

// Dark Theme (OLED - True Black Background) Semantic Colors - Expressive & Popping
val DarkPrimary = CherryYellow // Yellow as primary for dark theme pop
val DarkOnPrimary = BrandBlack
val DarkPrimaryContainer = CherryYellow.copy(alpha = 0.3f) // Dimmer yellow container
val DarkOnPrimaryContainer = BrandBlack

val DarkSecondary = CherryLavender // Lavender as secondary
val DarkOnSecondary = BrandBlack
val DarkSecondaryContainer = CherryLavender.copy(alpha = 0.3f)
val DarkOnSecondaryContainer = BrandBlack

val DarkTertiary = CherryOlive.copy(red = 0.5f, green = 0.7f, blue = 0.3f) // Brighter/adjusted Olive for dark
val DarkOnTertiary = BrandWhite
val DarkTertiaryContainer = CherryOlive.copy(alpha = 0.2f)
val DarkOnTertiaryContainer = BrandWhite

val DarkBackground = BrandBlack // True OLED black
val DarkOnBackground = BrandWhite

val DarkSurface = BrandBlack.copy(red = 0.05f, green = 0.05f, blue = 0.05f) // Very dark gray, almost black, for cards
val DarkOnSurface = BrandWhite
val DarkSurfaceVariant = BrandBlack.copy(red = 0.1f, green = 0.1f, blue = 0.1f) // Slightly lighter dark gray
val DarkOnSurfaceVariant = BrandWhite.copy(alpha = 0.7f)

val DarkError = Color(0xFFCF6679) // Standard M3 dark error
val DarkOnError = BrandBlack
val DarkErrorContainer = Color(0xFFB00020).copy(alpha = 0.4f)
val DarkOnErrorContainer = Color(0xFFF9DEDC)

val DarkOutline = CherryYellow.copy(alpha = 0.5f)
val DarkOutlineVariant = CherryLavender.copy(alpha = 0.3f)

val DarkSurfaceDim = BrandBlack.copy(red=0.03f, green=0.03f, blue=0.03f) // Even dimmer than surface
val DarkSurfaceBright = BrandBlack.copy(red=0.1f, green=0.1f, blue=0.1f) // Brighter than surface for some accents
val DarkSurfaceContainerLowest = BrandBlack // Pure black
val DarkSurfaceContainerLow = BrandBlack.copy(red = 0.05f, green = 0.05f, blue = 0.05f) // Slightly off black
val DarkSurfaceContainer = BrandBlack.copy(red = 0.08f, green = 0.08f, blue = 0.08f) // Standard dark container
val DarkSurfaceContainerHigh = BrandBlack.copy(red = 0.12f, green = 0.12f, blue = 0.12f)
val DarkSurfaceContainerHighest = BrandBlack.copy(red = 0.15f, green = 0.15f, blue = 0.15f) // For elements like TopAppBar if not primary

// Chart Colors - can be themed or fixed. For now, let's use some from the palette.
val ChartColor1 = CherryMaroon
val ChartColor2 = CherryOlive
val ChartColor3 = CherryLavender
val ChartColor4 = CherryYellow // Could be too light on light bg, might need adjustment or fixed color
val ChartColor5 = Color(0xFF479bba) // Previous brand blue as a contrasting option
