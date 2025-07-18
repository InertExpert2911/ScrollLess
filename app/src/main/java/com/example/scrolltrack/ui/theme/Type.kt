package com.example.scrolltrack.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.scrolltrack.R

// Define Analogue OS font family
val AnalogueOsFontFamily = FontFamily(
    Font(R.font.analogue_os_regular, FontWeight.Normal),
    Font(R.font.analogue_os_regular, FontWeight.Bold)
)

// Define Inter font family (ensure inter_variable.ttf and inter_italic_variable.ttf are in res/font)
val InterFontFamily = FontFamily(
    Font(R.font.inter_variable, FontWeight.Thin),
    Font(R.font.inter_variable, FontWeight.ExtraLight),
    Font(R.font.inter_variable, FontWeight.Light),
    Font(R.font.inter_variable, FontWeight.Normal),
    Font(R.font.inter_variable, FontWeight.Medium),
    Font(R.font.inter_variable, FontWeight.SemiBold),
    Font(R.font.inter_variable, FontWeight.Bold),
    Font(R.font.inter_variable, FontWeight.ExtraBold),
    Font(R.font.inter_variable, FontWeight.Black),
    Font(R.font.inter_italic_variable, FontWeight.Normal, style = androidx.compose.ui.text.font.FontStyle.Italic) // For italic
)

// Updated Material 3 Typography
val AppTypography = Typography(
    // Display styles - typically larger, for short, important text
    displayLarge = TextStyle(
        fontFamily = AnalogueOsFontFamily, // Analogue OS for display
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = AnalogueOsFontFamily, // Analogue OS for display
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = AnalogueOsFontFamily, // Analogue OS for display
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),

    // Headline styles - for moderately important text, like section titles
    headlineLarge = TextStyle(
        fontFamily = AnalogueOsFontFamily, // Analogue OS for headlines
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = AnalogueOsFontFamily, // Analogue OS for headlines
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = AnalogueOsFontFamily, // Analogue OS for headlines
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),

    // Title styles - for less prominent titles, like in app bars or dialogs
    titleLarge = TextStyle(
        fontFamily = AnalogueOsFontFamily, // Analogue OS for titles
        fontWeight = FontWeight.Normal, // M3 default is Normal for titleLarge
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = InterFontFamily, // Inter for smaller titles
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = InterFontFamily, // Inter for smaller titles
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),

    // Body styles - for longer-form text
    bodyLarge = TextStyle(
        fontFamily = InterFontFamily, // Inter for body
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp // M3 default letter spacing for bodyLarge
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily, // Inter for body
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp // M3 default letter spacing for bodyMedium
    ),
    bodySmall = TextStyle(
        fontFamily = InterFontFamily, // Inter for body
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp // M3 default letter spacing for bodySmall
    ),

    // Label styles - for call-to-action text, like in buttons or links
    labelLarge = TextStyle(
        fontFamily = InterFontFamily, // Inter for labels
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp // M3 default letter spacing for labelLarge
    ),
    labelMedium = TextStyle(
        fontFamily = InterFontFamily, // Inter for labels
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp // M3 default letter spacing for labelMedium
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily, // Inter for labels
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp // M3 default letter spacing for labelSmall
    )
)
