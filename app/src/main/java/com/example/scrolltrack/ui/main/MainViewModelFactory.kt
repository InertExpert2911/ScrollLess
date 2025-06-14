package com.example.scrolltrack.ui.main

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.scrolltrack.ScrollTrackApplication
import com.example.scrolltrack.data.SettingsRepositoryImpl

class MainViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            val repository = (application as ScrollTrackApplication).repository
            val settingsRepository = SettingsRepositoryImpl(application)
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository, settingsRepository, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
