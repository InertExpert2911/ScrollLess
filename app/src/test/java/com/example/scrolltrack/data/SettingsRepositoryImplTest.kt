package com.example.scrolltrack.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.scrolltrack.ui.theme.AppTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class SettingsRepositoryImplTest {

    private lateinit var repository: SettingsRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // The repository uses a specific SharedPreferences file name, we will clear it after each test.
        repository = SettingsRepositoryImpl(context)
    }

    @After
    fun tearDown() {
        // Access the same SharedPreferences file the repository uses and clear it
        val prefs = context.getSharedPreferences("ScrollTrackAppSettings", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    @Test
    fun `initial state - flows emit default values`() = runTest {
        assertThat(repository.selectedTheme.first()).isEqualTo(AppTheme.CalmLavender)
        assertThat(repository.isDarkMode.first()).isTrue()
        assertThat(repository.screenDpi.first()).isEqualTo(0)
        assertThat(repository.calibrationSliderPosition.first()).isEqualTo(0.5f)
        assertThat(repository.calibrationSliderHeight.first()).isEqualTo(0f)
    }

    @Test
    fun `setSelectedTheme - updates and persists theme`() = runTest {
        repository.setSelectedTheme(AppTheme.UpliftingGreen)
        assertThat(repository.selectedTheme.first()).isEqualTo(AppTheme.UpliftingGreen)

        // Re-instantiate repository to check for persistence
        val newRepository = SettingsRepositoryImpl(context)
        assertThat(newRepository.selectedTheme.first()).isEqualTo(AppTheme.UpliftingGreen)
    }

    @Test
    fun `setIsDarkMode - updates and persists dark mode`() = runTest {
        repository.setIsDarkMode(false)
        assertThat(repository.isDarkMode.first()).isFalse()

        // Re-instantiate repository to check for persistence
        val newRepository = SettingsRepositoryImpl(context)
        assertThat(newRepository.isDarkMode.first()).isFalse()
    }

    @Test
    fun `setScreenDpi - updates and persists screen dpi`() = runTest {
        repository.setScreenDpi(420)
        assertThat(repository.screenDpi.first()).isEqualTo(420)

        // Re-instantiate repository to check for persistence
        val newRepository = SettingsRepositoryImpl(context)
        assertThat(newRepository.screenDpi.first()).isEqualTo(420)
    }

    @Test
    fun `setCalibrationSliderPosition - updates and persists position`() = runTest {
        repository.setCalibrationSliderPosition(0.75f)
        assertThat(repository.calibrationSliderPosition.first()).isEqualTo(0.75f)

        val newRepository = SettingsRepositoryImpl(context)
        assertThat(newRepository.calibrationSliderPosition.first()).isEqualTo(0.75f)
    }

    @Test
    fun `setCalibrationSliderHeight - updates and persists height`() = runTest {
        repository.setCalibrationSliderHeight(150.5f)
        assertThat(repository.calibrationSliderHeight.first()).isEqualTo(150.5f)

        val newRepository = SettingsRepositoryImpl(context)
        assertThat(newRepository.calibrationSliderHeight.first()).isEqualTo(150.5f)
    }
}
