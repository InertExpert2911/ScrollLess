package com.example.scrolltrack.data

import com.example.scrolltrack.ui.theme.AppTheme
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val selectedTheme: Flow<AppTheme>
    suspend fun setSelectedTheme(theme: AppTheme)

    val isDarkMode: Flow<Boolean>
    suspend fun setIsDarkMode(isDark: Boolean)

    val calibrationFactorX: Flow<Float?>
    val calibrationFactorY: Flow<Float?>
    suspend fun setCalibrationFactors(factorX: Float?, factorY: Float?)
} 