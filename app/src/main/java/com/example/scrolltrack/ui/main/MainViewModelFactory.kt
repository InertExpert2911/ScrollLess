package com.example.scrolltrack.ui.main

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.scrolltrack.ScrollTrackApplication
import com.example.scrolltrack.data.ScrollDataRepository
import com.example.scrolltrack.data.SettingsRepository
import com.example.scrolltrack.data.SettingsRepositoryImpl
import com.example.scrolltrack.ui.main.FakeScrollDataRepository
import com.example.scrolltrack.ui.main.FakeSettingsRepository

class MainViewModelFactory(
    private val application: Application,
    private val useFakeRepos: Boolean = false
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {

            val scrollDataRepo = if (useFakeRepos) {
                FakeScrollDataRepository()
            } else {
                (application as ScrollTrackApplication).repository
            }

            val settingsRepo = if (useFakeRepos) {
                FakeSettingsRepository()
            } else {
                SettingsRepositoryImpl(application)
            }


            @Suppress("UNCHECKED_CAST")
            return MainViewModel(scrollDataRepo, settingsRepo, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
