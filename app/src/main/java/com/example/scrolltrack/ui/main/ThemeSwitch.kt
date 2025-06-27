package com.example.scrolltrack.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Define constants for theme variants for clarity
private const val THEME_LIGHT = "light"
private const val THEME_OLED_DARK = "oled_dark"

@Composable
fun ThemeSwitch(
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = currentTheme != THEME_LIGHT
    val interactionSource = remember { MutableInteractionSource() }

    // Animate the alignment of the thumb inside the track
    val alignment by animateFloatAsState(
        targetValue = if (isDarkTheme) 1f else -1f,
        label = "ThemeSwitchAlignment"
    )

    // Use semantic colors from the M3 theme
    val trackColor = if (isDarkTheme) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    val thumbColor = MaterialTheme.colorScheme.onPrimary
    val iconColor = MaterialTheme.colorScheme.primary


    Box(
        modifier = modifier
            .width(52.dp)
            .height(32.dp)
            .clip(CircleShape)
            .background(trackColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null // No ripple effect
            ) {
                // Toggle between the two defined themes
                val newTheme = if (isDarkTheme) THEME_LIGHT else THEME_OLED_DARK
                onThemeChange(newTheme)
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .align(BiasAlignment(alignment, 0f))
                .padding(4.dp)
                .clip(CircleShape)
                .background(thumbColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isDarkTheme) Icons.Default.Nightlight else Icons.Default.WbSunny,
                contentDescription = if (isDarkTheme) "Switch to Light Mode" else "Switch to Dark Mode",
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
} 