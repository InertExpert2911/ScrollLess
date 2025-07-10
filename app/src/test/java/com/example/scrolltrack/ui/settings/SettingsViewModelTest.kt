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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { settingsRepository.selectedTheme } returns themeFlow
        every { settingsRepository.isDarkMode } returns darkModeFlow
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

        viewModel.isDarkMode.test {
            assertThat(awaitItem()).isTrue()
            darkModeFlow.value = false
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun `setSelectedTheme updates repository`() = runTest {
        viewModel.setSelectedTheme(AppTheme.FocusBlue)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { settingsRepository.setSelectedTheme(AppTheme.FocusBlue) }
    }

    @Test
    fun `setIsDarkMode updates repository`() = runTest {
        viewModel.setIsDarkMode(false)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { settingsRepository.setIsDarkMode(false) }

        viewModel.setIsDarkMode(true)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { settingsRepository.setIsDarkMode(true) }
    }
} 