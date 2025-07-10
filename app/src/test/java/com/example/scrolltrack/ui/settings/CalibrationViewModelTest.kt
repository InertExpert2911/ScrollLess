package com.example.scrolltrack.ui.settings

import app.cash.turbine.test
import com.example.scrolltrack.data.SettingsRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class CalibrationViewModelTest {

    private lateinit var viewModel: CalibrationViewModel
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private val calibrationFactorXFlow = MutableStateFlow<Float?>(null)
    private val calibrationFactorYFlow = MutableStateFlow<Float?>(null)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { settingsRepository.calibrationFactorX } returns calibrationFactorXFlow
        coEvery { settingsRepository.calibrationFactorY } returns calibrationFactorYFlow
        viewModel = CalibrationViewModel(settingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is not calibrated`() = runTest {
        viewModel.uiState.test {
            val initialState = awaitItem()
            assertThat(initialState.isCalibrated).isFalse()
            assertThat(initialState.statusText).isEqualTo("Not yet calibrated")
        }
    }

    @Test
    fun `uiState reflects calibrated state from repository`() = runTest {
        viewModel.uiState.test {
            // Initial state
            assertThat(awaitItem().isCalibrated).isFalse()

            // Update flows to simulate calibration
            calibrationFactorXFlow.value = 160.0f
            calibrationFactorYFlow.value = 160.0f
            testDispatcher.scheduler.advanceUntilIdle()

            val calibratedState = awaitItem()
            assertThat(calibratedState.isCalibrated).isTrue()
            assertThat(calibratedState.horizontalDpi).isEqualTo(160)
            assertThat(calibratedState.verticalDpi).isEqualTo(160)
        }
    }

    @Test
    fun `addScrollDelta updates accumulated values`() = runTest {
        viewModel.addScrollDelta(100, 200)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.accumulatedScrollX.value).isEqualTo(100)
        assertThat(viewModel.accumulatedScrollY.value).isEqualTo(200)

        viewModel.addScrollDelta(50, 75)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.accumulatedScrollX.value).isEqualTo(150)
        assertThat(viewModel.accumulatedScrollY.value).isEqualTo(275)
    }

    @Test
    fun `resetScroll resets the correct axis`() = runTest {
        viewModel.addScrollDelta(100, 200)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.resetScroll("X")
        testDispatcher.scheduler.advanceUntilIdle()
        assertThat(viewModel.accumulatedScrollX.value).isEqualTo(0)
        assertThat(viewModel.accumulatedScrollY.value).isEqualTo(200)

        viewModel.resetScroll("Y")
        testDispatcher.scheduler.advanceUntilIdle()
        assertThat(viewModel.accumulatedScrollY.value).isEqualTo(0)
    }

    @Test
    fun `saveCalibration calls repository with accumulated values`() = runTest {
        val targetWidth = 1080f
        val targetHeight = 1920f

        viewModel.addScrollDelta(10, 20)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.saveCalibration(targetHeight, targetWidth)
        testDispatcher.scheduler.advanceUntilIdle()

        // The viewmodel stores the raw accumulated scroll, not the calculated DPI
        coVerify { settingsRepository.setCalibrationFactors(10f, 20f) }
    }
} 