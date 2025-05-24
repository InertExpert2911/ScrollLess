package com.example.scrolltrack.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.scrolltrack.R // Import your R class

// Define the Inter variable font family
// Make sure you have 'inter_variable.ttf' (or your chosen filename) in res/font/
val InterFontFamily = FontFamily(
    Font(R.font.inter_variable, FontWeight.Thin),      // Example: W100
    Font(R.font.inter_variable, FontWeight.ExtraLight),// Example: W200
    Font(R.font.inter_variable, FontWeight.Light),     // Example: W300
    Font(R.font.inter_variable, FontWeight.Normal),    // Example: W400 (Regular)
    Font(R.font.inter_variable, FontWeight.Medium),    // Example: W500
    Font(R.font.inter_variable, FontWeight.SemiBold),  // Example: W600
    Font(R.font.inter_variable, FontWeight.Bold),      // Example: W700
    Font(R.font.inter_variable, FontWeight.ExtraBold), // Example: W800
    Font(R.font.inter_variable, FontWeight.Black)      // Example: W900
    // The variable font file contains all these weights.
    // You specify the weight you want, and the renderer selects it from the variable font.
)

// Define the Caveat variable font family
// Make sure you have 'caveat_variable.ttf' in res/font/
val CaveatFontFamily = FontFamily(
    Font(R.font.caveat_variable, FontWeight.Normal), // Caveat is often used at normal or bold
    Font(R.font.caveat_variable, FontWeight.Bold)
    // Add other weights if your variable font file for Caveat supports them and you need them.
)

// Set of Material typography styles using Inter font
// You can customize these further based on Material 3 type scale guidelines.
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal, // Or a specific light weight like FontWeight.W300
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold, // Often a bit bolder for headlines
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium, // Or FontWeight.Normal as per M3 spec
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
