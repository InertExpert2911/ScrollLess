package com.example.scrolltrack.data

interface SettingsRepository {
    fun getSelectedTheme(): String
    fun setSelectedTheme(theme: String)
} 