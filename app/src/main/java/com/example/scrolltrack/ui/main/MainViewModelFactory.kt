package com.example.scrolltrack.ui.main

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.data.SettingsRepository
import com.example.scrolltrack.data.SettingsRepositoryImpl
import com.example.scrolltrack.db.AppDatabase

class MainViewModelFactory(
    private val application: Application,
    private val repository: ScrollDataRepository?, // Nullable for previews
    private val settingsRepository: SettingsRepository? // Nullable for previews
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            val repo = repository ?: (application as com.example.scrolltrack.ScrollTrackApplication).repository
            val settingsRepo = settingsRepository ?: SettingsRepositoryImpl(application)
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repo, settingsRepo, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
