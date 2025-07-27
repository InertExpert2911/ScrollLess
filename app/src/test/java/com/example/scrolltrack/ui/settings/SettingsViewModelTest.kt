package com.example.scrolltrack.ui.settings

import app.cash.turbine.test
import com.example.scrolltrack.data.SettingsRepository
import com.example.scrolltrack.ui.theme.AppTheme
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

@ExperimentalCoroutinesApi
class SettingsViewModelTest {
    private lateinit var viewModel: SettingsViewModel
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private val themeFlow = MutableStateFlow(AppTheme.CalmLavender)
    private val darkModeFlow = MutableStateFlow(true)
    private val screenDpiFlow = MutableStateFlow(0)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { settingsRepository.selectedTheme } returns themeFlow
        every { settingsRepository.isDarkMode } returns darkModeFlow
        every { settingsRepository.screenDpi } returns screenDpiFlow
        viewModel = SettingsViewModel(settingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `viewModel exposes theme and dark mode from repository`() = runTest {
        viewModel.selectedTheme.test {
            assertThat(awaitItem()).isEqualTo(AppTheme.CalmLavender)
            themeFlow.value = AppTheme.FocusBlue
            assertThat(awaitItem()).isEqualTo(AppTheme.FocusBlue)
        }
    }

    @Test
    fun `calibrationStatusText reflects DPI value`() = runTest {
        viewModel.calibrationStatusText.test {
            assertThat(awaitItem()).isEqualTo("Loading...") // Initial value
            
            screenDpiFlow.value = 0
            assertThat(awaitItem()).isEqualTo("Not calibrated")

            screenDpiFlow.value = 320
            assertThat(awaitItem()).isEqualTo("Calibrated (320 DPI)")
        }
    }

    @Test
    fun `setter methods update repository`() = runTest {
        viewModel.setSelectedTheme(AppTheme.FocusBlue)
        advanceUntilIdle()
        coVerify { settingsRepository.setSelectedTheme(AppTheme.FocusBlue) }

        viewModel.setIsDarkMode(false)
        advanceUntilIdle()
        coVerify { settingsRepository.setIsDarkMode(false) }
    }
}