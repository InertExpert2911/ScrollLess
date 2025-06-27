package com.example.scrolltrack.ui.theme

import androidx.compose.ui.graphics.Color

// Brand Palette
val BrandBlack = Color(0xFF18191e)
val BrandGreen = Color(0xFF44c98e)
val BrandOrange = Color(0xFFfd8537)
val BrandBlue = Color(0xFF479bba)
val BrandWhite = Color(0xFFFFFFFF)

// Light Theme Semantic Colors
val LightPrimary = BrandBlue
val LightOnPrimary = BrandWhite
val LightPrimaryContainer = Color(0xFFD0E6F0) // Lighter blue
val LightOnPrimaryContainer = BrandBlack // Or a darker shade of BrandBlue

val LightSecondary = BrandGreen
val LightOnSecondary = BrandBlack // Or BrandWhite if contrast is better. Black on this green is good.
val LightSecondaryContainer = Color(0xFFD6F5E3) // Lighter green
val LightOnSecondaryContainer = BrandBlack // Or a darker shade of BrandGreen

val LightTertiary = BrandOrange
val LightOnTertiary = BrandBlack // Black on this orange is good.
val LightTertiaryContainer = Color(0xFFFFE0CC) // Lighter orange
val LightOnTertiaryContainer = BrandBlack // Or a darker shade of BrandOrange

val LightBackground = Color(0xFFFDFEFF) // Very light, almost white
val LightOnBackground = BrandBlack

val LightSurface = BrandWhite // Pure white for cards, dialogs etc.
val LightOnSurface = BrandBlack
val LightSurfaceVariant = Color(0xFFE0E4E7) // For outlines, dividers, less prominent surfaces
val LightOnSurfaceVariant = BrandBlack.copy(alpha = 0.7f) // Slightly lighter black

val LightError = Color(0xFFB00020)
val LightOnError = BrandWhite
val LightErrorContainer = Color(0xFFFCDDE0)
val LightOnErrorContainer = Color(0xFF410E0B)

val LightOutline = BrandBlue.copy(alpha = 0.5f)
val LightOutlineVariant = BrandGreen.copy(alpha = 0.3f)

// Material 3 Surface Container Colors - Light Theme
val LightSurfaceDim = Color(0xFFDCD8E0) // Example if needed
val LightSurfaceBright = Color(0xFFFDF7FF) // Example if needed
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF7F2FA)
val LightSurfaceContainer = Color(0xFFF3EDF7)
val LightSurfaceContainerHigh = Color(0xFFECE6F0)
val LightSurfaceContainerHighest = Color(0xFFE6E0E9)
val LightOnSurfaceContainer = BrandBlack // General 'on' color for these containers

// Dark Theme Semantic Colors
val DarkPrimary = BrandBlue
val DarkOnPrimary = BrandWhite
val DarkPrimaryContainer = BrandBlue.copy(alpha = 0.3f) // Darker container, e.g. for FABs on dark
val DarkOnPrimaryContainer = BrandWhite // Text on that container

val DarkSecondary = BrandGreen
val DarkOnSecondary = BrandBlack
val DarkSecondaryContainer = BrandGreen.copy(alpha = 0.3f)
val DarkOnSecondaryContainer = BrandWhite

val DarkTertiary = BrandOrange
val DarkOnTertiary = BrandBlack
val DarkTertiaryContainer = BrandOrange.copy(alpha = 0.3f)
val DarkOnTertiaryContainer = BrandWhite

val DarkBackground = BrandBlack // Use the deepest brand black for main background
val DarkOnBackground = BrandWhite

val DarkSurface = Color(0xFF1F2024) // Slightly lighter than pure BrandBlack for cards etc.
val DarkOnSurface = BrandWhite
val DarkSurfaceVariant = Color(0xFF2A2C30) // For outlines, dividers on dark
val DarkOnSurfaceVariant = BrandWhite.copy(alpha = 0.7f)

val DarkError = Color(0xFFCF6679)
val DarkOnError = BrandBlack
val DarkErrorContainer = Color(0xFFB00020).copy(alpha = 0.3f) // Darker, less prominent error container
val DarkOnErrorContainer = Color(0xFFF9DEDC)

val DarkOutline = BrandBlue.copy(alpha = 0.5f)
val DarkOutlineVariant = BrandGreen.copy(alpha = 0.3f)

// Material 3 Surface Container Colors - Dark Theme
val DarkSurfaceDim = Color(0xFF141218) // Example if needed
val DarkSurfaceBright = Color(0xFF3A383F) // Example if needed
val DarkSurfaceContainerLowest = BrandBlack
val DarkSurfaceContainerLow = Color(0xFF1F2024)
val DarkSurfaceContainer = Color(0xFF232529)
val DarkSurfaceContainerHigh = Color(0xFF2A2C30)
val DarkSurfaceContainerHighest = Color(0xFF35373C)
val DarkOnSurfaceContainer = BrandWhite // General 'on' color for these containers


// Chart Colors (can be themed or fixed)
// For simplicity, using brand colors directly, but they could also be part of the semantic slots above
// Or defined per theme if needed for better contrast.
val ChartColor1 = BrandBlue
val ChartColor2 = BrandGreen
val ChartColor3 = BrandOrange
val ChartColor4 = Color(0xFFEF5350) // Contrasting Red (keep fixed or theme)
val ChartColor5 = Color(0xFFAB47BC) // Contrasting Purple (keep fixed or theme)
