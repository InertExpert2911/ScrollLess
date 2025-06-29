package com.example.scrolltrack.data

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val selectedTheme: Flow<String>
    suspend fun setSelectedTheme(theme: String)
} 