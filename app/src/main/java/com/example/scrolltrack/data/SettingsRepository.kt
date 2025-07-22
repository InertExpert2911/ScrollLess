package com.example.scrolltrack.data

import com.example.scrolltrack.ui.theme.AppTheme
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val selectedTheme: Flow<AppTheme>
    suspend fun setSelectedTheme(theme: AppTheme)

    val isDarkMode: Flow<Boolean>
    suspend fun setIsDarkMode(isDark: Boolean)

    val screenDpi: Flow<Int>
    suspend fun setScreenDpi(dpi: Int)

    val calibrationSliderPosition: Flow<Float>
    suspend fun setCalibrationSliderPosition(position: Float)

    val calibrationSliderHeight: Flow<Float>
    suspend fun setCalibrationSliderHeight(height: Float)
}
