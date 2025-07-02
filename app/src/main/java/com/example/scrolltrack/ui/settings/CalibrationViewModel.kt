package com.example.scrolltrack.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrolltrack.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CalibrationViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val calibrationFactor = settingsRepository.calibrationFactor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun saveCalibrationFactor(pixelsScrolled: Float, distanceCm: Float) {
        if (distanceCm <= 0) return
        val factor = pixelsScrolled / (distanceCm / 100f) // factor is pixels per meter
        viewModelScope.launch {
            settingsRepository.setCalibrationFactor(factor)
        }
    }

    fun resetCalibration() {
        viewModelScope.launch {
            settingsRepository.setCalibrationFactor(null)
        }
    }
} 