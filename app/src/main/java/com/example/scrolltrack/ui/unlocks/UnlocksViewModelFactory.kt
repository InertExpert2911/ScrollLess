package com.example.scrolltrack.ui.unlocks

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.scrolltrack.ScrollTrackApplication

class UnlocksViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UnlocksViewModel::class.java)) {
            val repository = (application as ScrollTrackApplication).repository
            @Suppress("UNCHECKED_CAST")
            return UnlocksViewModel(repository, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 