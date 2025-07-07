package com.example.scrolltrack.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.scrolltrack.ui.theme.AppTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(@ApplicationContext context: Context) : SettingsRepository {

    private val appPrefs: SharedPreferences

    private companion object {
        const val PREFS_APP_SETTINGS = "ScrollTrackAppSettings"
        const val KEY_SELECTED_THEME = "selected_theme_palette"
        val DEFAULT_THEME = AppTheme.CalmLavender.name
        const val KEY_IS_DARK_MODE = "is_dark_mode"
        const val DEFAULT_IS_DARK_MODE = true // Default to dark mode
        const val KEY_CALIBRATION_FACTOR_X = "calibration_factor_x"
        const val KEY_CALIBRATION_FACTOR_Y = "calibration_factor_y"
    }

    init {
        appPrefs = context.getSharedPreferences(PREFS_APP_SETTINGS, Context.MODE_PRIVATE)
    }

    override val selectedTheme: Flow<AppTheme> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_SELECTED_THEME) {
                val themeName = appPrefs.getString(KEY_SELECTED_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
                trySend(AppTheme.valueOf(themeName))
            }
        }
        appPrefs.registerOnSharedPreferenceChangeListener(listener)

        // Emit the initial value
        val initialThemeName = appPrefs.getString(KEY_SELECTED_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
        trySend(AppTheme.valueOf(initialThemeName))

        awaitClose { appPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    override val isDarkMode: Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_IS_DARK_MODE) {
                trySend(appPrefs.getBoolean(KEY_IS_DARK_MODE, DEFAULT_IS_DARK_MODE))
            }
        }
        appPrefs.registerOnSharedPreferenceChangeListener(listener)

        // Emit the initial value
        trySend(appPrefs.getBoolean(KEY_IS_DARK_MODE, DEFAULT_IS_DARK_MODE))

        awaitClose { appPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    override val calibrationFactorX: Flow<Float?> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_CALIBRATION_FACTOR_X) {
                if (appPrefs.contains(KEY_CALIBRATION_FACTOR_X)) {
                    trySend(appPrefs.getFloat(KEY_CALIBRATION_FACTOR_X, -1f))
                } else {
                    trySend(null)
                }
            }
        }
        appPrefs.registerOnSharedPreferenceChangeListener(listener)

        // Emit initial value
        if (appPrefs.contains(KEY_CALIBRATION_FACTOR_X)) {
            trySend(appPrefs.getFloat(KEY_CALIBRATION_FACTOR_X, -1f))
        } else {
            trySend(null)
        }

        awaitClose { appPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    override val calibrationFactorY: Flow<Float?> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_CALIBRATION_FACTOR_Y) {
                if (appPrefs.contains(KEY_CALIBRATION_FACTOR_Y)) {
                    trySend(appPrefs.getFloat(KEY_CALIBRATION_FACTOR_Y, -1f))
                } else {
                    trySend(null)
                }
            }
        }
        appPrefs.registerOnSharedPreferenceChangeListener(listener)

        // Emit initial value
        if (appPrefs.contains(KEY_CALIBRATION_FACTOR_Y)) {
            trySend(appPrefs.getFloat(KEY_CALIBRATION_FACTOR_Y, -1f))
        } else {
            trySend(null)
        }

        awaitClose { appPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    override suspend fun setSelectedTheme(theme: AppTheme) {
        appPrefs.edit {
            putString(KEY_SELECTED_THEME, theme.name)
        }
    }

    override suspend fun setIsDarkMode(isDark: Boolean) {
        appPrefs.edit {
            putBoolean(KEY_IS_DARK_MODE, isDark)
        }
    }

    override suspend fun setCalibrationFactors(factorX: Float?, factorY: Float?) {
        appPrefs.edit {
            if (factorX != null) {
                putFloat(KEY_CALIBRATION_FACTOR_X, factorX)
            } else {
                remove(KEY_CALIBRATION_FACTOR_X)
            }
            if (factorY != null) {
                putFloat(KEY_CALIBRATION_FACTOR_Y, factorY)
            } else {
                remove(KEY_CALIBRATION_FACTOR_Y)
            }
        }
    }
} 