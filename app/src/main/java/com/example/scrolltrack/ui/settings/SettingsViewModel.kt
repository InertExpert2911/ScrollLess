package com.example.scrolltrack.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrolltrack.data.SettingsRepository
import com.example.scrolltrack.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val selectedTheme: StateFlow<AppTheme> = settingsRepository.selectedTheme
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppTheme.CalmLavender
        )

    val isDarkMode: StateFlow<Boolean> = settingsRepository.isDarkMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val calibrationStatusText: StateFlow<String> = combine(
        settingsRepository.calibrationFactorX,
        settingsRepository.calibrationFactorY
    ) { x, y ->
        if (x != null && y != null) {
            "Custom (${x.roundToInt()}x, ${y.roundToInt()}y px)"
        } else {
            "Using device default"
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "Loading..."
    )

    fun setSelectedTheme(theme: AppTheme) {
        viewModelScope.launch {
            settingsRepository.setSelectedTheme(theme)
        }
    }

    fun setIsDarkMode(isDark: Boolean) {
        viewModelScope.launch {
            settingsRepository.setIsDarkMode(isDark)
        }
    }
} 