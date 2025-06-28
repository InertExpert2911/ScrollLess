package com.example.scrolltrack.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(@ApplicationContext context: Context) : SettingsRepository {

    private val appPrefs: SharedPreferences

    private companion object {
        const val PREFS_APP_SETTINGS = "ScrollTrackAppSettings"
        const val KEY_SELECTED_THEME = "selected_theme_variant"
        const val DEFAULT_THEME = "oled_dark"
    }

    init {
        appPrefs = context.getSharedPreferences(PREFS_APP_SETTINGS, Context.MODE_PRIVATE)
    }

    override fun getSelectedTheme(): String {
        return appPrefs.getString(KEY_SELECTED_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
    }

    override fun setSelectedTheme(theme: String) {
        appPrefs.edit {
            putString(KEY_SELECTED_THEME, theme)
        }
    }
} 