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
        const val KEY_CALIBRATION_SLIDER_HEIGHT = "calibration_slider_height"
        const val DEFAULT_CALIBRATION_SLIDER_HEIGHT = 0f
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
    fun `calibrationSliderHeight - initial value is default`() = runTest {
        assertThat(repository.calibrationSliderHeight.first()).isEqualTo(DEFAULT_CALIBRATION_SLIDER_HEIGHT)
    }

    @Test
    fun `setCalibrationSliderHeight - updates height and flow emits new value`() = runTest {
        val newHeight = 1200f

        repository.calibrationSliderHeight.test {
            assertThat(awaitItem()).isEqualTo(DEFAULT_CALIBRATION_SLIDER_HEIGHT)

            repository.setCalibrationSliderHeight(newHeight)

            assertThat(awaitItem()).isEqualTo(newHeight)

            assertThat(sharedPreferences.getFloat(KEY_CALIBRATION_SLIDER_HEIGHT, 0f)).isEqualTo(newHeight)
            cancelAndIgnoreRemainingEvents()
        }
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
