package com.example.scrolltrack.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.example.scrolltrack.ui.theme.AppTheme // Make sure AppTheme is accessible
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import app.cash.turbine.test

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var repository: SettingsRepositoryImpl
    private lateinit var sharedPreferences: SharedPreferences

    private val testDispatcher = UnconfinedTestDispatcher() // Or StandardTestDispatcher if more control over time is needed

    // Must match keys and defaults in SettingsRepositoryImpl
    private companion object {
        const val PREFS_APP_SETTINGS = "ScrollTrackAppSettings"
        const val KEY_SELECTED_THEME = "selected_theme_palette"
        val DEFAULT_THEME = AppTheme.CalmLavender // Assuming this is the actual default from AppTheme
        const val KEY_IS_DARK_MODE = "is_dark_mode"
        const val DEFAULT_IS_DARK_MODE = true
        const val KEY_CALIBRATION_FACTOR = "calibration_factor"
    }


    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        repository = SettingsRepositoryImpl(context)
        sharedPreferences = context.getSharedPreferences(PREFS_APP_SETTINGS, Context.MODE_PRIVATE)
        // Clear shared preferences before each test
        sharedPreferences.edit().clear().apply() // Use apply() for Robolectric tests for immediate effect
    }

    @After
    fun tearDown() {
        sharedPreferences.edit().clear().apply()
        Dispatchers.resetMain()
    }

    @Test
    fun `selectedTheme - initial value is default`() = runTest(testDispatcher) {
        assertThat(repository.selectedTheme.first()).isEqualTo(DEFAULT_THEME)
    }

    @Test
    fun `setSelectedTheme - updates theme and flow emits new value`() = runTest(testDispatcher) {
                val newTheme = AppTheme.ClarityTeal

        repository.selectedTheme.test {
            assertThat(awaitItem()).isEqualTo(DEFAULT_THEME) // Initial emission

            repository.setSelectedTheme(newTheme)
            // Robolectric's SharedPreferences listener might need a moment or specific handling.
            // Using test {} from Turbine should handle emissions correctly.

            assertThat(awaitItem()).isEqualTo(newTheme) // Emission after change

            // Verify SharedPreferences directly
            assertThat(sharedPreferences.getString(KEY_SELECTED_THEME, null)).isEqualTo(newTheme.name)
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun `isDarkMode - initial value is default`() = runTest(testDispatcher) {
        assertThat(repository.isDarkMode.first()).isEqualTo(DEFAULT_IS_DARK_MODE)
    }

    @Test
    fun `setIsDarkMode - updates mode and flow emits new value`() = runTest(testDispatcher) {
        val newDarkModeState = false // Default is true

        repository.isDarkMode.test {
            assertThat(awaitItem()).isEqualTo(DEFAULT_IS_DARK_MODE) // Initial

            repository.setIsDarkMode(newDarkModeState)

            assertThat(awaitItem()).isEqualTo(newDarkModeState) // After change

            assertThat(sharedPreferences.getBoolean(KEY_IS_DARK_MODE, DEFAULT_IS_DARK_MODE)).isEqualTo(newDarkModeState)
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun `calibrationFactor - initial value is null`() = runTest(testDispatcher) {
        assertThat(repository.calibrationFactor.first()).isNull()
    }

    @Test
    fun `setCalibrationFactor - can be set and then cleared`() = runTest(testDispatcher) {
        val factor = 1.5f

        repository.calibrationFactor.test {
            assertThat(awaitItem()).isNull() // Initial value should be null

            // Set a value
            repository.setCalibrationFactor(factor)
            assertThat(awaitItem()).isEqualTo(factor) // Check emission
            // Verify persistence
            assertThat(sharedPreferences.getFloat(KEY_CALIBRATION_FACTOR, -1f)).isEqualTo(factor)

            // Clear the value by setting it to null
            repository.setCalibrationFactor(null)
            assertThat(awaitItem()).isNull() // Check emission
            // Verify persistence
            assertThat(sharedPreferences.contains(KEY_CALIBRATION_FACTOR)).isFalse()

            ensureAllEventsConsumed()
        }
    }

    @Test
    fun `multiple updates to a setting are reflected in flow`() = runTest(testDispatcher) {
        repository.isDarkMode.test {
            assertThat(awaitItem()).isEqualTo(DEFAULT_IS_DARK_MODE) // Initial true

            repository.setIsDarkMode(false)
            assertThat(awaitItem()).isEqualTo(false) // Change 1

            repository.setIsDarkMode(true)
            assertThat(awaitItem()).isEqualTo(true) // Change 2

            ensureAllEventsConsumed()
        }
    }
}
