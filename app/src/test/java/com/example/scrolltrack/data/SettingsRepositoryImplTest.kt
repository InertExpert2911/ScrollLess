package com.example.scrolltrack.data

import android.content.Context
import android.content.SharedPreferences
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.scrolltrack.ui.theme.AppTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import app.cash.turbine.test

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SettingsRepositoryImplTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var context: Context
    private lateinit var repository: SettingsRepositoryImpl
    private lateinit var sharedPreferences: SharedPreferences

    private val testDispatcher = UnconfinedTestDispatcher()

    private companion object {
        const val PREFS_APP_SETTINGS = "ScrollTrackAppSettings"
        const val KEY_SELECTED_THEME = "selected_theme_palette"
        val DEFAULT_THEME = AppTheme.CalmLavender
        const val KEY_IS_DARK_MODE = "is_dark_mode"
        const val DEFAULT_IS_DARK_MODE = true
        const val KEY_CALIBRATION_FACTOR_X = "calibration_factor_x"
        const val KEY_CALIBRATION_FACTOR_Y = "calibration_factor_y"
    }


    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        repository = SettingsRepositoryImpl(context)
        sharedPreferences = context.getSharedPreferences(PREFS_APP_SETTINGS, Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        sharedPreferences.edit().clear().commit()
        Dispatchers.resetMain()
    }

    @Test
    fun `selectedTheme - initial value is default`() = runTest {
        assertThat(repository.selectedTheme.first()).isEqualTo(DEFAULT_THEME)
    }

    @Test
    fun `setSelectedTheme - updates theme and flow emits new value`() = runTest {
        val newTheme = AppTheme.ClarityTeal

        repository.selectedTheme.test {
            assertThat(awaitItem()).isEqualTo(DEFAULT_THEME)

            repository.setSelectedTheme(newTheme)

            assertThat(awaitItem()).isEqualTo(newTheme)

            assertThat(sharedPreferences.getString(KEY_SELECTED_THEME, null)).isEqualTo(newTheme.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isDarkMode - initial value is default`() = runTest {
        assertThat(repository.isDarkMode.first()).isEqualTo(DEFAULT_IS_DARK_MODE)
    }

    @Test
    fun `setIsDarkMode - updates mode and flow emits new value`() = runTest {
        val newDarkModeState = false

        repository.isDarkMode.test {
            assertThat(awaitItem()).isEqualTo(DEFAULT_IS_DARK_MODE)

            repository.setIsDarkMode(newDarkModeState)

            assertThat(awaitItem()).isEqualTo(newDarkModeState)

            assertThat(sharedPreferences.getBoolean(KEY_IS_DARK_MODE, DEFAULT_IS_DARK_MODE)).isEqualTo(newDarkModeState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `calibrationFactor - initial values are null`() = runTest {
        assertThat(repository.calibrationFactorX.first()).isNull()
        assertThat(repository.calibrationFactorY.first()).isNull()
    }

    @Test
    fun `setCalibrationFactors - can be set and then cleared`() = runTest {
        val factorX = 1.5f
        val factorY = 0.8f

        // Use separate collectors to avoid race conditions between flows
        val xValues = mutableListOf<Float?>()
        val yValues = mutableListOf<Float?>()

        val jobX = launch {
            repository.calibrationFactorX.collect { xValues.add(it) }
        }
        val jobY = launch {
            repository.calibrationFactorY.collect { yValues.add(it) }
        }

        // Allow initial values to be collected
        advanceUntilIdle()

        // Initial state
        assertThat(xValues.first()).isNull()
        assertThat(yValues.first()).isNull()

        // Set values
        repository.setCalibrationFactors(factorX, factorY)
        // Wait for flows to emit the new value
        advanceUntilIdle()

        assertThat(xValues.last()).isEqualTo(factorX)
        assertThat(yValues.last()).isEqualTo(factorY)
        assertThat(sharedPreferences.getFloat(KEY_CALIBRATION_FACTOR_X, -1f)).isEqualTo(factorX)
        assertThat(sharedPreferences.getFloat(KEY_CALIBRATION_FACTOR_Y, -1f)).isEqualTo(factorY)

        // Clear values
        repository.setCalibrationFactors(null, null)
        advanceUntilIdle()

        assertThat(xValues.last()).isNull()
        assertThat(yValues.last()).isNull()
        assertThat(sharedPreferences.contains(KEY_CALIBRATION_FACTOR_X)).isFalse()
        assertThat(sharedPreferences.contains(KEY_CALIBRATION_FACTOR_Y)).isFalse()

        jobX.cancel()
        jobY.cancel()
    }

    @Test
    fun `multiple updates to a setting are reflected in flow`() = runTest {
        repository.isDarkMode.test {
            assertThat(awaitItem()).isEqualTo(DEFAULT_IS_DARK_MODE)

            repository.setIsDarkMode(false)
            assertThat(awaitItem()).isEqualTo(false)

            repository.setIsDarkMode(true)
            assertThat(awaitItem()).isEqualTo(true)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
