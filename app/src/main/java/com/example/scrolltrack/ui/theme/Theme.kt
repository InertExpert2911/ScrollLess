package com.example.scrolltrack.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color

// Explicitly import our custom AppTypography
import com.example.scrolltrack.ui.theme.AppTypography as Typography

enum class AppTheme(val visibleName: String) {
    ClarityTeal("Clarity Teal"),
    FocusBlue("Focus Blue"),
    CalmLavender("Calm Lavender"),
    VitalityOrange("Vitality Orange"),
    UpliftingGreen("Uplifting Green"),
    OptimisticYellow("Optimistic Yellow")
}

private val ClarityTealLightColorScheme = lightColorScheme(
    primary = ClarityTeal.primaryLight,
    onPrimary = ClarityTeal.onPrimaryLight,
    primaryContainer = ClarityTeal.primaryContainerLight,
    onPrimaryContainer = ClarityTeal.onPrimaryContainerLight,
    secondary = ClarityTeal.secondaryLight,
    onSecondary = ClarityTeal.onSecondaryLight,
    secondaryContainer = ClarityTeal.secondaryContainerLight,
    onSecondaryContainer = ClarityTeal.onSecondaryContainerLight,
    tertiary = ClarityTeal.tertiaryLight,
    onTertiary = ClarityTeal.onTertiaryLight,
    tertiaryContainer = ClarityTeal.tertiaryContainerLight,
    onTertiaryContainer = ClarityTeal.onTertiaryContainerLight,
    surface = NeutralSurfaceLight,
    onSurface = NeutralOnSurfaceLight,
    surfaceDim = NeutralSurfaceDimLight,
    surfaceBright = NeutralSurfaceBrightLight,
    surfaceContainerLowest = NeutralSurfaceDimLight,
    surfaceContainerLow = NeutralSurfaceContainerLowLight,
    surfaceContainer = NeutralSurfaceContainerLight,
    surfaceContainerHigh = NeutralSurfaceContainerHighLight,
    surfaceContainerHighest = NeutralSurfaceBrightLight,
    onSurfaceVariant = NeutralOnSurfaceVariantLight,
    outline = NeutralOutlineLight,
    outlineVariant = NeutralOutlineVariantLight
)

private val FocusBlueLightColorScheme = lightColorScheme(
    primary = FocusBlue.primaryLight,
    onPrimary = FocusBlue.onPrimaryLight,
    primaryContainer = FocusBlue.primaryContainerLight,
    onPrimaryContainer = FocusBlue.onPrimaryContainerLight,
    secondary = FocusBlue.secondaryLight,
    onSecondary = FocusBlue.onSecondaryLight,
    secondaryContainer = FocusBlue.secondaryContainerLight,
    onSecondaryContainer = FocusBlue.onSecondaryContainerLight,
    tertiary = FocusBlue.tertiaryLight,
    onTertiary = FocusBlue.onTertiaryLight,
    tertiaryContainer = FocusBlue.tertiaryContainerLight,
    onTertiaryContainer = FocusBlue.onTertiaryContainerLight,
    surface = NeutralSurfaceLight,
    onSurface = NeutralOnSurfaceLight,
    surfaceDim = NeutralSurfaceDimLight,
    surfaceBright = NeutralSurfaceBrightLight,
    surfaceContainerLowest = NeutralSurfaceDimLight,
    surfaceContainerLow = NeutralSurfaceContainerLowLight,
    surfaceContainer = NeutralSurfaceContainerLight,
    surfaceContainerHigh = NeutralSurfaceContainerHighLight,
    surfaceContainerHighest = NeutralSurfaceBrightLight,
    onSurfaceVariant = NeutralOnSurfaceVariantLight,
    outline = NeutralOutlineLight,
    outlineVariant = NeutralOutlineVariantLight
)

private val CalmLavenderLightColorScheme = lightColorScheme(
    primary = CalmLavender.primaryLight,
    onPrimary = CalmLavender.onPrimaryLight,
    primaryContainer = CalmLavender.primaryContainerLight,
    onPrimaryContainer = CalmLavender.onPrimaryContainerLight,
    secondary = CalmLavender.secondaryLight,
    onSecondary = CalmLavender.onSecondaryLight,
    secondaryContainer = CalmLavender.secondaryContainerLight,
    onSecondaryContainer = CalmLavender.onSecondaryContainerLight,
    tertiary = CalmLavender.tertiaryLight,
    onTertiary = CalmLavender.onTertiaryLight,
    tertiaryContainer = CalmLavender.tertiaryContainerLight,
    onTertiaryContainer = CalmLavender.onTertiaryContainerLight,
    surface = NeutralSurfaceLight,
    onSurface = NeutralOnSurfaceLight,
    surfaceDim = NeutralSurfaceDimLight,
    surfaceBright = NeutralSurfaceBrightLight,
    surfaceContainerLowest = NeutralSurfaceDimLight,
    surfaceContainerLow = NeutralSurfaceContainerLowLight,
    surfaceContainer = NeutralSurfaceContainerLight,
    surfaceContainerHigh = NeutralSurfaceContainerHighLight,
    surfaceContainerHighest = NeutralSurfaceBrightLight,
    onSurfaceVariant = NeutralOnSurfaceVariantLight,
    outline = NeutralOutlineLight,
    outlineVariant = NeutralOutlineVariantLight
)

private val VitalityOrangeLightColorScheme = lightColorScheme(
    primary = VitalityOrange.primaryLight,
    onPrimary = VitalityOrange.onPrimaryLight,
    primaryContainer = VitalityOrange.primaryContainerLight,
    onPrimaryContainer = VitalityOrange.onPrimaryContainerLight,
    secondary = VitalityOrange.secondaryLight,
    onSecondary = VitalityOrange.onSecondaryLight,
    secondaryContainer = VitalityOrange.secondaryContainerLight,
    onSecondaryContainer = VitalityOrange.onSecondaryContainerLight,
    tertiary = VitalityOrange.tertiaryLight,
    onTertiary = VitalityOrange.onTertiaryLight,
    tertiaryContainer = VitalityOrange.tertiaryContainerLight,
    onTertiaryContainer = VitalityOrange.onTertiaryContainerLight,
    surface = NeutralSurfaceLight,
    onSurface = NeutralOnSurfaceLight,
    surfaceDim = NeutralSurfaceDimLight,
    surfaceBright = NeutralSurfaceBrightLight,
    surfaceContainerLowest = NeutralSurfaceDimLight,
    surfaceContainerLow = NeutralSurfaceContainerLowLight,
    surfaceContainer = NeutralSurfaceContainerLight,
    surfaceContainerHigh = NeutralSurfaceContainerHighLight,
    surfaceContainerHighest = NeutralSurfaceBrightLight,
    onSurfaceVariant = NeutralOnSurfaceVariantLight,
    outline = NeutralOutlineLight,
    outlineVariant = NeutralOutlineVariantLight
)

private val UpliftingGreenLightColorScheme = lightColorScheme(
    primary = UpliftingGreen.primaryLight,
    onPrimary = UpliftingGreen.onPrimaryLight,
    primaryContainer = UpliftingGreen.primaryContainerLight,
    onPrimaryContainer = UpliftingGreen.onPrimaryContainerLight,
    secondary = UpliftingGreen.secondaryLight,
    onSecondary = UpliftingGreen.onSecondaryLight,
    secondaryContainer = UpliftingGreen.secondaryContainerLight,
    onSecondaryContainer = UpliftingGreen.onSecondaryContainerLight,
    tertiary = UpliftingGreen.tertiaryLight,
    onTertiary = UpliftingGreen.onTertiaryLight,
    tertiaryContainer = UpliftingGreen.tertiaryContainerLight,
    onTertiaryContainer = UpliftingGreen.onTertiaryContainerLight,
    surface = NeutralSurfaceLight,
    onSurface = NeutralOnSurfaceLight,
    surfaceDim = NeutralSurfaceDimLight,
    surfaceBright = NeutralSurfaceBrightLight,
    surfaceContainerLowest = NeutralSurfaceDimLight,
    surfaceContainerLow = NeutralSurfaceContainerLowLight,
    surfaceContainer = NeutralSurfaceContainerLight,
    surfaceContainerHigh = NeutralSurfaceContainerHighLight,
    surfaceContainerHighest = NeutralSurfaceBrightLight,
    onSurfaceVariant = NeutralOnSurfaceVariantLight,
    outline = NeutralOutlineLight,
    outlineVariant = NeutralOutlineVariantLight
)

private val OptimisticYellowLightColorScheme = lightColorScheme(
    primary = OptimisticYellow.primaryLight,
    onPrimary = OptimisticYellow.onPrimaryLight,
    primaryContainer = OptimisticYellow.primaryContainerLight,
    onPrimaryContainer = OptimisticYellow.onPrimaryContainerLight,
    secondary = OptimisticYellow.secondaryLight,
    onSecondary = OptimisticYellow.onSecondaryLight,
    secondaryContainer = OptimisticYellow.secondaryContainerLight,
    onSecondaryContainer = OptimisticYellow.onSecondaryContainerLight,
    tertiary = OptimisticYellow.tertiaryLight,
    onTertiary = OptimisticYellow.onTertiaryLight,
    tertiaryContainer = OptimisticYellow.tertiaryContainerLight,
    onTertiaryContainer = OptimisticYellow.onTertiaryContainerLight,
    surface = NeutralSurfaceLight,
    onSurface = NeutralOnSurfaceLight,
    surfaceDim = NeutralSurfaceDimLight,
    surfaceBright = NeutralSurfaceBrightLight,
    surfaceContainerLowest = NeutralSurfaceDimLight,
    surfaceContainerLow = NeutralSurfaceContainerLowLight,
    surfaceContainer = NeutralSurfaceContainerLight,
    surfaceContainerHigh = NeutralSurfaceContainerHighLight,
    surfaceContainerHighest = NeutralSurfaceBrightLight,
    onSurfaceVariant = NeutralOnSurfaceVariantLight,
    outline = NeutralOutlineLight,
    outlineVariant = NeutralOutlineVariantLight
)

private val ClarityTealDarkColorScheme = darkColorScheme(
    primary = ClarityTeal.primaryDark,
    onPrimary = ClarityTeal.onPrimaryDark,
    primaryContainer = ClarityTeal.primaryContainerDark,
    onPrimaryContainer = ClarityTeal.onPrimaryContainerDark,
    secondary = ClarityTeal.secondaryDark,
    onSecondary = ClarityTeal.onSecondaryDark,
    secondaryContainer = ClarityTeal.secondaryContainerDark,
    onSecondaryContainer = ClarityTeal.onSecondaryContainerDark,
    tertiary = ClarityTeal.tertiaryDark,
    onTertiary = ClarityTeal.onTertiaryDark,
    tertiaryContainer = ClarityTeal.tertiaryContainerDark,
    onTertiaryContainer = ClarityTeal.onTertiaryContainerDark,
    surface = NeutralSurfaceDark,
    onSurface = NeutralOnSurfaceDark,
    surfaceDim = NeutralSurfaceDimDark,
    surfaceBright = NeutralSurfaceBrightDark,
    surfaceContainerLowest = NeutralSurfaceDimDark,
    surfaceContainerLow = NeutralSurfaceContainerLowDark,
    surfaceContainer = NeutralSurfaceContainerDark,
    surfaceContainerHigh = NeutralSurfaceContainerHighDark,
    surfaceContainerHighest = NeutralSurfaceBrightDark,
    onSurfaceVariant = NeutralOnSurfaceVariantDark,
    outline = NeutralOutlineDark,
    outlineVariant = NeutralOutlineVariantDark
)

private val FocusBlueDarkColorScheme = darkColorScheme(
    primary = FocusBlue.primaryDark,
    onPrimary = FocusBlue.onPrimaryDark,
    primaryContainer = FocusBlue.primaryContainerDark,
    onPrimaryContainer = FocusBlue.onPrimaryContainerDark,
    secondary = FocusBlue.secondaryDark,
    onSecondary = FocusBlue.onSecondaryDark,
    secondaryContainer = FocusBlue.secondaryContainerDark,
    onSecondaryContainer = FocusBlue.onSecondaryContainerDark,
    tertiary = FocusBlue.tertiaryDark,
    onTertiary = FocusBlue.onTertiaryDark,
    tertiaryContainer = FocusBlue.tertiaryContainerDark,
    onTertiaryContainer = FocusBlue.onTertiaryContainerDark,
    surface = NeutralSurfaceDark,
    onSurface = NeutralOnSurfaceDark,
    surfaceDim = NeutralSurfaceDimDark,
    surfaceBright = NeutralSurfaceBrightDark,
    surfaceContainerLowest = NeutralSurfaceDimDark,
    surfaceContainerLow = NeutralSurfaceContainerLowDark,
    surfaceContainer = NeutralSurfaceContainerDark,
    surfaceContainerHigh = NeutralSurfaceContainerHighDark,
    surfaceContainerHighest = NeutralSurfaceBrightDark,
    onSurfaceVariant = NeutralOnSurfaceVariantDark,
    outline = NeutralOutlineDark,
    outlineVariant = NeutralOutlineVariantDark
)

private val CalmLavenderDarkColorScheme = darkColorScheme(
    primary = CalmLavender.primaryDark,
    onPrimary = CalmLavender.onPrimaryDark,
    primaryContainer = CalmLavender.primaryContainerDark,
    onPrimaryContainer = CalmLavender.onPrimaryContainerDark,
    secondary = CalmLavender.secondaryDark,
    onSecondary = CalmLavender.onSecondaryDark,
    secondaryContainer = CalmLavender.secondaryContainerDark,
    onSecondaryContainer = CalmLavender.onSecondaryContainerDark,
    tertiary = CalmLavender.tertiaryDark,
    onTertiary = CalmLavender.onTertiaryDark,
    tertiaryContainer = CalmLavender.tertiaryContainerDark,
    onTertiaryContainer = CalmLavender.onTertiaryContainerDark,
    surface = NeutralSurfaceDark,
    onSurface = NeutralOnSurfaceDark,
    surfaceDim = NeutralSurfaceDimDark,
    surfaceBright = NeutralSurfaceBrightDark,
    surfaceContainerLowest = NeutralSurfaceDimDark,
    surfaceContainerLow = NeutralSurfaceContainerLowDark,
    surfaceContainer = NeutralSurfaceContainerDark,
    surfaceContainerHigh = NeutralSurfaceContainerHighDark,
    surfaceContainerHighest = NeutralSurfaceBrightDark,
    onSurfaceVariant = NeutralOnSurfaceVariantDark,
    outline = NeutralOutlineDark,
    outlineVariant = NeutralOutlineVariantDark
)

private val VitalityOrangeDarkColorScheme = darkColorScheme(
    primary = VitalityOrange.primaryDark,
    onPrimary = VitalityOrange.onPrimaryDark,
    primaryContainer = VitalityOrange.primaryContainerDark,
    onPrimaryContainer = VitalityOrange.onPrimaryContainerDark,
    secondary = VitalityOrange.secondaryDark,
    onSecondary = VitalityOrange.onSecondaryDark,
    secondaryContainer = VitalityOrange.secondaryContainerDark,
    onSecondaryContainer = VitalityOrange.onSecondaryContainerDark,
    tertiary = VitalityOrange.tertiaryDark,
    onTertiary = VitalityOrange.onTertiaryDark,
    tertiaryContainer = VitalityOrange.tertiaryContainerDark,
    onTertiaryContainer = VitalityOrange.onTertiaryContainerDark,
    surface = NeutralSurfaceDark,
    onSurface = NeutralOnSurfaceDark,
    surfaceDim = NeutralSurfaceDimDark,
    surfaceBright = NeutralSurfaceBrightDark,
    surfaceContainerLowest = NeutralSurfaceDimDark,
    surfaceContainerLow = NeutralSurfaceContainerLowDark,
    surfaceContainer = NeutralSurfaceContainerDark,
    surfaceContainerHigh = NeutralSurfaceContainerHighDark,
    surfaceContainerHighest = NeutralSurfaceBrightDark,
    onSurfaceVariant = NeutralOnSurfaceVariantDark,
    outline = NeutralOutlineDark,
    outlineVariant = NeutralOutlineVariantDark
)

private val UpliftingGreenDarkColorScheme = darkColorScheme(
    primary = UpliftingGreen.primaryDark,
    onPrimary = UpliftingGreen.onPrimaryDark,
    primaryContainer = UpliftingGreen.primaryContainerDark,
    onPrimaryContainer = UpliftingGreen.onPrimaryContainerDark,
    secondary = UpliftingGreen.secondaryDark,
    onSecondary = UpliftingGreen.onSecondaryDark,
    secondaryContainer = UpliftingGreen.secondaryContainerDark,
    onSecondaryContainer = UpliftingGreen.onSecondaryContainerDark,
    tertiary = UpliftingGreen.tertiaryDark,
    onTertiary = UpliftingGreen.onTertiaryDark,
    tertiaryContainer = UpliftingGreen.tertiaryContainerDark,
    onTertiaryContainer = UpliftingGreen.onTertiaryContainerDark,
    surface = NeutralSurfaceDark,
    onSurface = NeutralOnSurfaceDark,
    surfaceDim = NeutralSurfaceDimDark,
    surfaceBright = NeutralSurfaceBrightDark,
    surfaceContainerLowest = NeutralSurfaceDimDark,
    surfaceContainerLow = NeutralSurfaceContainerLowDark,
    surfaceContainer = NeutralSurfaceContainerDark,
    surfaceContainerHigh = NeutralSurfaceContainerHighDark,
    surfaceContainerHighest = NeutralSurfaceBrightDark,
    onSurfaceVariant = NeutralOnSurfaceVariantDark,
    outline = NeutralOutlineDark,
    outlineVariant = NeutralOutlineVariantDark
)

private val OptimisticYellowDarkColorScheme = darkColorScheme(
    primary = OptimisticYellow.primaryDark,
    onPrimary = OptimisticYellow.onPrimaryDark,
    primaryContainer = OptimisticYellow.primaryContainerDark,
    onPrimaryContainer = OptimisticYellow.onPrimaryContainerDark,
    secondary = OptimisticYellow.secondaryDark,
    onSecondary = OptimisticYellow.onSecondaryDark,
    secondaryContainer = OptimisticYellow.secondaryContainerDark,
    onSecondaryContainer = OptimisticYellow.onSecondaryContainerDark,
    tertiary = OptimisticYellow.tertiaryDark,
    onTertiary = OptimisticYellow.onTertiaryDark,
    tertiaryContainer = OptimisticYellow.tertiaryContainerDark,
    onTertiaryContainer = OptimisticYellow.onTertiaryContainerDark,
    surface = NeutralSurfaceDark,
    onSurface = NeutralOnSurfaceDark,
    surfaceDim = NeutralSurfaceDimDark,
    surfaceBright = NeutralSurfaceBrightDark,
    surfaceContainerLowest = NeutralSurfaceDimDark,
    surfaceContainerLow = NeutralSurfaceContainerLowDark,
    surfaceContainer = NeutralSurfaceContainerDark,
    surfaceContainerHigh = NeutralSurfaceContainerHighDark,
    surfaceContainerHighest = NeutralSurfaceBrightDark,
    onSurfaceVariant = NeutralOnSurfaceVariantDark,
    outline = NeutralOutlineDark,
    outlineVariant = NeutralOutlineVariantDark
)

@Composable
fun getThemeColors(theme: AppTheme, darkTheme: Boolean = isSystemInDarkTheme()): ColorScheme {
    return when (theme) {
        AppTheme.ClarityTeal -> if (darkTheme) ClarityTealDarkColorScheme else ClarityTealLightColorScheme
        AppTheme.FocusBlue -> if (darkTheme) FocusBlueDarkColorScheme else FocusBlueLightColorScheme
        AppTheme.CalmLavender -> if (darkTheme) CalmLavenderDarkColorScheme else CalmLavenderLightColorScheme
        AppTheme.VitalityOrange -> if (darkTheme) VitalityOrangeDarkColorScheme else VitalityOrangeLightColorScheme
        AppTheme.UpliftingGreen -> if (darkTheme) UpliftingGreenDarkColorScheme else UpliftingGreenLightColorScheme
        AppTheme.OptimisticYellow -> if (darkTheme) OptimisticYellowDarkColorScheme else OptimisticYellowLightColorScheme
    }
}

@Composable
fun ScrollTrackTheme(
    appTheme: AppTheme = AppTheme.CalmLavender,
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> getThemeColors(appTheme, darkTheme)
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
