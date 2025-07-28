package com.example.scrolltrack.ui.settings

import app.cash.turbine.test
import com.example.scrolltrack.data.SettingsRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.math.roundToInt

@ExperimentalCoroutinesApi
class CalibrationViewModelTest {

    private lateinit var viewModel: CalibrationViewModel
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private val screenDpiFlow = MutableStateFlow(0)
    private val sliderPositionFlow = MutableStateFlow(0.5f)
    private val sliderHeightFlow = MutableStateFlow(0f)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { settingsRepository.screenDpi } returns screenDpiFlow
        every { settingsRepository.calibrationSliderPosition } returns sliderPositionFlow
        every { settingsRepository.calibrationSliderHeight } returns sliderHeightFlow
        viewModel = CalibrationViewModel(settingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is correct`() = runTest {
        viewModel.uiState.test {
            val initialState = awaitItem()
            assertThat(initialState.calibratedDpi).isEqualTo(0)
            assertThat(initialState.sliderPosition).isEqualTo(0.5f)
            assertThat(initialState.isHeightLocked).isFalse()
        }
    }

    @Test
    fun `stopCalibrationAndSave calculates DPI and saves to repository`() = runTest {
        viewModel.startCalibration()
        viewModel.setSliderHeight(1080f)
        viewModel.onSliderValueChanged(0.75f)

        viewModel.uiState.test {
            // Consume states from startCalibration, setSliderHeight, and onSliderValueChanged
            awaitItem()
            awaitItem()
            awaitItem()

            viewModel.stopCalibrationAndSave()

            val finalState = awaitItem()

            val expectedDpi = (0.75f * 1080f / 3.375f).roundToInt()
            coVerify { settingsRepository.setScreenDpi(expectedDpi) }
            coVerify { settingsRepository.setCalibrationSliderPosition(0.75f) }
            coVerify { settingsRepository.setCalibrationSliderHeight(1080f) }

            assertThat(finalState.calibrationInProgress).isFalse()
            assertThat(finalState.calibratedDpi).isEqualTo(expectedDpi)
        }
    }
}