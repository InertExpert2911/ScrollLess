package com.example.scrolltrack.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scrolltrack.data.SettingsRepository
import com.example.scrolltrack.db.RawAppEventDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

data class CalibrationUiState(
    val statusText: String = "Not yet calibrated",
    val verticalDpi: Int? = null,
    val horizontalDpi: Int? = null,
    val isCalibrated: Boolean = false
)

@HiltViewModel
class CalibrationViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val rawAppEventDao: RawAppEventDao
) : ViewModel() {

    private val _calibrationInProgress = MutableStateFlow(false)

    private val _accumulatedScrollX = MutableStateFlow(0)
    val accumulatedScrollX: StateFlow<Int> = _accumulatedScrollX.asStateFlow()

    private val _accumulatedScrollY = MutableStateFlow(0)
    val accumulatedScrollY: StateFlow<Int> = _accumulatedScrollY.asStateFlow()

    val uiState: StateFlow<CalibrationUiState> = combine(
        settingsRepository.calibrationFactorX,
        settingsRepository.calibrationFactorY
    ) { factorX, factorY ->
        if (factorX != 1.0f || factorY != 1.0f) {
            CalibrationUiState(
                statusText = "Device calibrated",
                verticalDpi = factorY?.roundToInt(),
                horizontalDpi = factorX?.roundToInt(),
                isCalibrated = true
            )
        } else {
            CalibrationUiState()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CalibrationUiState()
    )

    init {
        viewModelScope.launch {
            _calibrationInProgress.flatMapLatest { inProgress ->
                if (inProgress) {
                    rawAppEventDao.getEventsForPeriodFlow(System.currentTimeMillis() - 10000, System.currentTimeMillis() + 10000)
                } else {
                    emptyFlow()
                }
            }.collect { events ->
                val scrollY = events.sumOf { it.scrollDeltaY ?: 0 }
                _accumulatedScrollY.value = scrollY
            }
        }
    }

    fun startCalibration() {
        _calibrationInProgress.value = true
    }

    fun addScrollDelta(deltaX: Int, deltaY: Int) {
        if (deltaX != 0) {
            _accumulatedScrollX.value += deltaX
        }
        if (deltaY != 0) {
            _accumulatedScrollY.value += deltaY
        }
    }

    fun resetScroll(axis: String) {
        if (axis == "X") {
            _accumulatedScrollX.value = 0
        } else {
            _accumulatedScrollY.value = 0
        }
    }

    fun saveCalibration(
        targetPixelHeight: Float,
        targetPixelWidth: Float
    ) {
        viewModelScope.launch {
            val factorY = if (_accumulatedScrollY.value > 0) {
                targetPixelHeight / _accumulatedScrollY.value
            } else {
                1.0f
            }

            val factorX = if (_accumulatedScrollX.value > 0) {
                targetPixelWidth / _accumulatedScrollX.value
            } else {
                1.0f
            }
            settingsRepository.setCalibrationFactors(factorX, factorY)
        }
    }
}
