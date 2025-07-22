package com.example.scrolltrack.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrolltrack.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

private const val CARD_LONG_EDGE_INCHES = 3.375f // Standard ID-1 card width

data class CalibrationUiState(
    val calibratedDpi: Int = 0,
    val sliderPosition: Float = 0.5f,
    val sliderHeightPx: Float = 0f,
    val calibrationInProgress: Boolean = false,
    val showConfirmation: Boolean = false,
    val showInfoDialog: Boolean = false,
    val isHeightLocked: Boolean = false
)

@HiltViewModel
class CalibrationViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalibrationUiState())
    val uiState: StateFlow<CalibrationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.screenDpi,
                settingsRepository.calibrationSliderPosition,
                settingsRepository.calibrationSliderHeight
            ) { dpi, sliderPos, sliderHeight ->
                Triple(dpi, sliderPos, sliderHeight)
            }.collect { (dpi, sliderPos, sliderHeight) ->
                _uiState.update {
                    it.copy(
                        calibratedDpi = dpi,
                        sliderPosition = if (dpi > 0) sliderPos else 0.5f,
                        sliderHeightPx = if (dpi > 0) sliderHeight else it.sliderHeightPx,
                        isHeightLocked = dpi > 0
                    )
                }
            }
        }
    }

    fun startCalibration() {
        // When starting, unlock the height and reset the saved height so it can be re-measured
        _uiState.update {
            it.copy(
                calibrationInProgress = true,
                isHeightLocked = false,
                sliderHeightPx = 0f
            )
        }
    }

    fun stopCalibrationAndSave() {
        viewModelScope.launch {
            val currentState = _uiState.value
            val sliderPosition = currentState.sliderPosition
            val sliderHeightPx = currentState.sliderHeightPx
            if (sliderHeightPx > 0) {
                val dpi = (sliderPosition * sliderHeightPx / CARD_LONG_EDGE_INCHES).roundToInt()
                settingsRepository.setScreenDpi(dpi)
                settingsRepository.setCalibrationSliderPosition(sliderPosition)
                settingsRepository.setCalibrationSliderHeight(sliderHeightPx) // Save slider height
                _uiState.update {
                    it.copy(
                        calibrationInProgress = false,
                        showConfirmation = true,
                        calibratedDpi = dpi,
                        isHeightLocked = true
                    )
                }
            } else {
                _uiState.update { it.copy(calibrationInProgress = false) }
            }
        }
    }

    fun resetCalibration() {
        viewModelScope.launch {
            val currentState = _uiState.value
            settingsRepository.setScreenDpi(0)
            settingsRepository.setCalibrationSliderPosition(0.5f)
            settingsRepository.setCalibrationSliderHeight(0f) // Reset slider height
            _uiState.update {
                it.copy(
                    sliderPosition = 0.5f,
                    calibratedDpi = 0,
                    isHeightLocked = false,
                    sliderHeightPx = if (currentState.calibrationInProgress) it.sliderHeightPx else 0f
                )
            }
        }
    }

    fun onSliderValueChanged(value: Float) {
        if (_uiState.value.calibrationInProgress) {
            _uiState.update { it.copy(sliderPosition = value) }
        }
    }

    fun setSliderHeight(height: Float) {
        // Only update the height if it's not locked or if it hasn't been set yet.
        if (!_uiState.value.isHeightLocked || _uiState.value.sliderHeightPx == 0f) {
            _uiState.update { it.copy(sliderHeightPx = height) }
        }
    }

    fun dismissConfirmation() {
        _uiState.update { it.copy(showConfirmation = false) }
    }
    fun showInfoDialog(show: Boolean) {
        _uiState.update { it.copy(showInfoDialog = show) }
    }
}
