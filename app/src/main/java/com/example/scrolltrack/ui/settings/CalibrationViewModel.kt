package com.example.scrolltrack.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrolltrack.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val CREDIT_CARD_HEIGHT_INCHES = 2.125f // Standard ID-1 card height

data class CalibrationScreenState(
    val sliderPosition: Float = 0.5f, // Represents value from 0.0 to 1.0
    val calibrationInProgress: Boolean = false,
    val showInfoDialog: Boolean = false,
    val showConfirmation: Boolean = false
)

@HiltViewModel
class CalibrationViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalibrationScreenState())
    val uiState: StateFlow<CalibrationScreenState> = _uiState.asStateFlow()

    fun onSliderValueChanged(newValue: Float) {
        if (_uiState.value.calibrationInProgress) {
            _uiState.value = _uiState.value.copy(sliderPosition = newValue)
        }
    }

    fun startCalibration() {
        // Reset to a default position when starting
        _uiState.value = _uiState.value.copy(sliderPosition = 0.5f, calibrationInProgress = true)
    }

    fun stopCalibrationAndSave(sliderHeightPx: Float) {
        if (_uiState.value.calibrationInProgress) {
            viewModelScope.launch {
                // The calibrated height in pixels is the slider's total height * its position
                val calibratedHeightInPx = sliderHeightPx * _uiState.value.sliderPosition
                // DPI is the number of pixels per inch
                val newDpi = calibratedHeightInPx / CREDIT_CARD_HEIGHT_INCHES
                settingsRepository.setScreenDpi(newDpi.toInt())
                _uiState.value = _uiState.value.copy(calibrationInProgress = false, showConfirmation = true)
            }
        }
    }

    fun resetCalibration() {
        _uiState.value = _uiState.value.copy(sliderPosition = 0.5f, calibrationInProgress = false)
        viewModelScope.launch {
            settingsRepository.setScreenDpi(0) // Reset to default/uncalibrated
        }
    }

    fun showInfoDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showInfoDialog = show)
    }

    fun dismissConfirmation() {
        _uiState.value = _uiState.value.copy(showConfirmation = false)
    }
}
