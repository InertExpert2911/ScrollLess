package com.example.scrolltrack.data

interface SettingsRepository {
    fun isHistoricalBackfillDone(): Boolean
    fun setHistoricalBackfillDone(value: Boolean)
    fun getSelectedTheme(): String
    fun setSelectedTheme(theme: String)
} 